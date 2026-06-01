package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.common.di.DefaultDispatcher
import com.kshavrin.mymoney.core.domain.model.CategoryRecordGroup
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetCategoryRecordsUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
    private val transactionRepository: TransactionRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {

    suspend operator fun invoke(accountId: Long, period: Period): List<CategoryRecordGroup> = withContext(defaultDispatcher) {
        val account = accountRepository.findById(accountId)
            ?: throw IllegalArgumentException("Account $accountId not found")
        val currency = currencyRepository.findById(account.currencyId)
            ?: throw IllegalStateException("Currency ${account.currencyId} not found")

        val groups = transactionRepository.getCategoryGroups(accountId, period)
        val byCategory = transactionRepository.findByPeriod(accountId, period)
            .groupBy { it.categoryId }

        groups.map { group ->
            val transactions = byCategory[group.categoryId]
                .orEmpty()
                .sortedByDescending { it.occurredAt }
            CategoryRecordGroup(
                categoryId = group.categoryId,
                name = group.name,
                iconKey = group.iconKey,
                colorHex = group.colorHex,
                kind = group.kind,
                total = Money(group.total, currency),
                count = group.count,
                transactions = transactions,
            )
        }
    }
}
