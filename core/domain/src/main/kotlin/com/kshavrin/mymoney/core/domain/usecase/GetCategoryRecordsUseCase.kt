package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.common.di.DefaultDispatcher
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.CategoryRecordGroup
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import java.math.BigDecimal
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
        recordsForAccounts(listOf(account), currency, period)
    }

    suspend fun forAccounts(accounts: List<Account>, currency: Currency, period: Period): List<CategoryRecordGroup> =
        withContext(defaultDispatcher) {
            val activeAccounts = accounts.filterNot { it.isArchived }
            require(activeAccounts.all { it.currencyId == currency.id }) { "All accounts must use ${currency.code}" }
            recordsForAccounts(activeAccounts, currency, period)
        }

    private suspend fun recordsForAccounts(
        accounts: List<Account>,
        currency: Currency,
        period: Period,
    ): List<CategoryRecordGroup> {
        val groups = accounts.flatMap { account -> transactionRepository.getCategoryGroups(account.id, period) }
            .groupBy { it.categoryId }
            .map { (_, entries) ->
                val first = entries.first()
                CategoryGroupTotal(
                    categoryId = first.categoryId,
                    name = first.name,
                    iconKey = first.iconKey,
                    colorHex = first.colorHex,
                    kind = first.kind,
                    total = entries.fold(BigDecimal.ZERO) { acc, item -> acc + item.total },
                    count = entries.sumOf { it.count },
                )
            }
        val byCategory = accounts.flatMap { account -> transactionRepository.findByPeriod(account.id, period) }
            .groupBy { it.categoryId }

        return groups.map { group ->
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

private data class CategoryGroupTotal(
    val categoryId: Long,
    val name: String,
    val iconKey: String,
    val colorHex: String,
    val kind: CategoryKind,
    val total: BigDecimal,
    val count: Int,
)
