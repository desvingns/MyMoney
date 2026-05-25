package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.common.di.DefaultDispatcher
import com.kshavrin.mymoney.core.domain.model.RecurringTemplate
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.repository.RecurringTemplateRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

class GenerateDueRecurringUseCase @Inject constructor(
    private val recurringTemplateRepository: RecurringTemplateRepository,
    private val transactionRepository: TransactionRepository,
    private val recurringScheduler: RecurringScheduler,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {

    suspend operator fun invoke(now: Instant): Unit = withContext(defaultDispatcher) {
        recurringTemplateRepository.findDue(now)
            .filter { it.isActive }
            .forEach { template -> generate(template, now) }
    }

    private suspend fun generate(template: RecurringTemplate, now: Instant) {
        var currentRun = template.nextRunAt
        while (!currentRun.isAfter(now) && withinEnd(template, currentRun)) {
            transactionRepository.upsert(occurrence(template, currentRun, now))
            currentRun = recurringScheduler.computeNextRun(template.copy(nextRunAt = currentRun), now)
            if (passedEnd(template, currentRun)) {
                recurringTemplateRepository.deactivate(template.id)
                return
            }
        }
        recurringTemplateRepository.updateNextRun(template.id, currentRun)
    }

    private fun withinEnd(template: RecurringTemplate, runAt: Instant): Boolean =
        template.endsAt == null || !runAt.isAfter(template.endsAt)

    private fun passedEnd(template: RecurringTemplate, runAt: Instant): Boolean =
        template.endsAt != null && runAt.isAfter(template.endsAt)

    private fun occurrence(template: RecurringTemplate, occurredAt: Instant, now: Instant): Transaction =
        Transaction(
            id = 0L,
            kind = template.baseKind,
            amount = template.amount,
            currencyId = template.currencyId,
            accountId = template.accountId,
            categoryId = template.categoryId,
            note = template.note,
            occurredAt = occurredAt,
            createdAt = now,
            updatedAt = now,
            isDeleted = false,
            toAccountId = template.toAccountId,
            toAmount = null,
            exchangeRate = null,
        )
}
