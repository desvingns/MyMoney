package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.common.di.DefaultDispatcher
import com.kshavrin.mymoney.core.domain.model.BalanceSnapshot
import com.kshavrin.mymoney.core.domain.model.CategoryBalance
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
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

        val expenseSummary = transactionRepository.getCategorySummary(accountId, period, TransactionKind.Expense)
        val incomeSummary = transactionRepository.getCategorySummary(accountId, period, TransactionKind.Income)

        val totalExpense = expenseSummary.sumOf { it.total }
        val totalIncome = incomeSummary.sumOf { it.total }
        val net = totalIncome.subtract(totalExpense)

        val byCategory = (expenseSummary + incomeSummary).map { summary ->
            val combined = totalIncome.add(totalExpense)
            val fraction = if (combined.signum() == 0) 0f else summary.total.toFloat() / combined.toFloat()
            CategoryBalance(
                categoryId = summary.categoryId,
                categoryName = summary.categoryName,
                colorHex = summary.colorHex,
                total = Money(summary.total, currency),
                fraction = fraction,
            )
        }

        BalanceSnapshot(
            income = Money(totalIncome, currency),
            expense = Money(totalExpense, currency),
            net = Money(net, currency),
            byCategory = byCategory,
        )
    }
}
