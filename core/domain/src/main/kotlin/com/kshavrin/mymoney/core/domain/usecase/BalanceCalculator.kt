package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.common.di.DefaultDispatcher
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.BalanceSnapshot
import com.kshavrin.mymoney.core.domain.model.CategoryBalance
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import java.math.BigDecimal
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class BalanceCalculator @Inject constructor(
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
    private val transactionRepository: TransactionRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {

    suspend operator fun invoke(accountId: Long, period: Period): BalanceSnapshot = withContext(defaultDispatcher) {
        val account = accountRepository.findById(accountId)
            ?: throw IllegalArgumentException("Account $accountId not found")
        val currency = currencyRepository.findById(account.currencyId)
            ?: throw IllegalStateException("Currency ${account.currencyId} not found")
        calculate(listOf(account.id), currency, period)
    }

    suspend fun forAccounts(accounts: List<Account>, currency: Currency, period: Period): BalanceSnapshot =
        withContext(defaultDispatcher) {
            val activeAccounts = accounts.filterNot { it.isArchived }
            require(activeAccounts.all { it.currencyId == currency.id }) { "All accounts must use ${currency.code}" }
            calculate(activeAccounts.map { it.id }, currency, period)
        }

    private suspend fun calculate(accountIds: List<Long>, currency: Currency, period: Period): BalanceSnapshot {
        val categoryTotals = accountIds.flatMap { accountId ->
            transactionRepository.getCategorySummary(accountId, period, TransactionKind.Expense).map { it to true } +
                transactionRepository.getCategorySummary(accountId, period, TransactionKind.Income).map { it to false }
        }
            .groupBy { (summary, isExpense) -> CategoryKey(summary.categoryId, isExpense) }
            .map { (_, entries) ->
                val first = entries.first()
                CategoryTotal(
                    categoryId = first.first.categoryId,
                    categoryName = first.first.categoryName,
                    colorHex = first.first.colorHex,
                    iconKey = first.first.iconKey,
                    isExpense = first.second,
                    amount = entries.fold(BigDecimal.ZERO) { acc, (summary, _) -> acc + summary.total },
                )
            }

        val totalExpense = categoryTotals
            .filter { it.isExpense }
            .fold(BigDecimal.ZERO) { acc, item -> acc + item.amount }
        val totalIncome = categoryTotals
            .filter { !it.isExpense }
            .fold(BigDecimal.ZERO) { acc, item -> acc + item.amount }
        val combined = totalIncome.add(totalExpense)

        return BalanceSnapshot(
            income = Money(totalIncome, currency),
            expense = Money(totalExpense, currency),
            net = Money(totalIncome.subtract(totalExpense), currency),
            byCategory = categoryTotals.map { total ->
                CategoryBalance(
                    categoryId = total.categoryId,
                    categoryName = total.categoryName,
                    colorHex = total.colorHex,
                    total = Money(total.amount, currency),
                    fraction = if (combined.signum() == 0) 0f else total.amount.toFloat() / combined.toFloat(),
                    iconKey = total.iconKey,
                    isExpense = total.isExpense,
                )
            },
        )
    }
}

private data class CategoryKey(
    val categoryId: Long,
    val isExpense: Boolean,
)

private data class CategoryTotal(
    val categoryId: Long,
    val categoryName: String,
    val colorHex: String,
    val iconKey: String,
    val isExpense: Boolean,
    val amount: BigDecimal,
)
