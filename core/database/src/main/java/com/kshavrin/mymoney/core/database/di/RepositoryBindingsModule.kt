package com.kshavrin.mymoney.core.database.di

import com.kshavrin.mymoney.core.database.repository.AccountRepositoryImpl
import com.kshavrin.mymoney.core.database.repository.BackupRepositoryImpl
import com.kshavrin.mymoney.core.database.repository.BudgetRepositoryImpl
import com.kshavrin.mymoney.core.database.repository.CategoryRepositoryImpl
import com.kshavrin.mymoney.core.database.repository.CurrencyRateRepositoryImpl
import com.kshavrin.mymoney.core.database.repository.CurrencyRepositoryImpl
import com.kshavrin.mymoney.core.database.repository.GoalRepositoryImpl
import com.kshavrin.mymoney.core.database.repository.RecurringTemplateRepositoryImpl
import com.kshavrin.mymoney.core.database.repository.SearchHistoryRepositoryImpl
import com.kshavrin.mymoney.core.database.repository.SyncLogRepositoryImpl
import com.kshavrin.mymoney.core.database.repository.TransactionRepositoryImpl
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.BackupRepository
import com.kshavrin.mymoney.core.domain.repository.BudgetRepository
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRateRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.repository.GoalRepository
import com.kshavrin.mymoney.core.domain.repository.RecurringTemplateRepository
import com.kshavrin.mymoney.core.domain.repository.SearchHistoryRepository
import com.kshavrin.mymoney.core.domain.repository.SyncLogRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindingsModule {

    @Binds
    @Singleton
    abstract fun bindCurrencyRepository(impl: CurrencyRepositoryImpl): CurrencyRepository

    @Binds
    @Singleton
    abstract fun bindCurrencyRateRepository(impl: CurrencyRateRepositoryImpl): CurrencyRateRepository

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindBudgetRepository(impl: BudgetRepositoryImpl): BudgetRepository

    @Binds
    @Singleton
    abstract fun bindRecurringTemplateRepository(impl: RecurringTemplateRepositoryImpl): RecurringTemplateRepository

    @Binds
    @Singleton
    abstract fun bindSyncLogRepository(impl: SyncLogRepositoryImpl): SyncLogRepository

    @Binds
    @Singleton
    abstract fun bindSearchHistoryRepository(impl: SearchHistoryRepositoryImpl): SearchHistoryRepository

    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository

    @Binds
    @Singleton
    abstract fun bindGoalRepository(impl: GoalRepositoryImpl): GoalRepository
}
