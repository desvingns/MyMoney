package com.kshavrin.mymoney.core.datastore.usecase

import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class DashboardData(
    val accounts: List<Account>,
    val currencies: List<Currency>,
    val settings: AppSettings,
)

class DashboardDataUseCase
    @Inject
    constructor(
        private val accountRepository: AccountRepository,
        private val currencyRepository: CurrencyRepository,
        private val appSettingsRepository: AppSettingsRepository,
        private val transactionRepository: TransactionRepository,
        private val categoryRepository: CategoryRepository,
    ) {
        fun observeInputs(): Flow<DashboardData> =
            combine(
                accountRepository.observeActive(),
                currencyRepository.observeActive(),
                appSettingsRepository.settings,
                ::DashboardData,
            )

        fun observeCategories(): Flow<List<Category>> = categoryRepository.observeAll()

        fun observeExpenseCategories(): Flow<List<Category>> =
            categoryRepository.observeByKind(CategoryKind.Expense)

        fun observeTransactionChanges(): Flow<List<Transaction>> = transactionRepository.observeRecent(limit = 1)

        suspend fun currentSettings(): AppSettings = appSettingsRepository.settings.first()

        suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
            appSettingsRepository.update(transform)
        }

        suspend fun findCurrency(
            currencyId: Long,
        ): Currency? = currencyRepository.findById(currencyId)

        suspend fun findTransactions(
            accountId: Long,
            period: Period,
        ): List<Transaction> =
            transactionRepository.findByPeriod(accountId, period)

        suspend fun allTransactions(): List<Transaction> = transactionRepository.observeAll().first()
    }
