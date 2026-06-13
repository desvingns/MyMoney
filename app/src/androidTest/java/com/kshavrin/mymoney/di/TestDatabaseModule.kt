package com.kshavrin.mymoney.di

import android.content.Context
import androidx.room.Room
import com.kshavrin.mymoney.core.database.MoneyDatabase
import com.kshavrin.mymoney.core.database.dao.AccountDao
import com.kshavrin.mymoney.core.database.dao.BudgetDao
import com.kshavrin.mymoney.core.database.dao.CategoryDao
import com.kshavrin.mymoney.core.database.dao.CurrencyDao
import com.kshavrin.mymoney.core.database.dao.CurrencyRateDao
import com.kshavrin.mymoney.core.database.dao.GoalDao
import com.kshavrin.mymoney.core.database.dao.RecurringTemplateDao
import com.kshavrin.mymoney.core.database.dao.SearchHistoryDao
import com.kshavrin.mymoney.core.database.dao.SyncLogDao
import com.kshavrin.mymoney.core.database.dao.TransactionDao
import com.kshavrin.mymoney.core.database.di.DatabaseModule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object TestDatabaseModule {
    @Provides
    @Singleton
    fun provideMoneyDatabase(
        @ApplicationContext context: Context,
    ): MoneyDatabase =
        Room
            .inMemoryDatabaseBuilder(context, MoneyDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    @Provides
    fun provideCurrencyDao(db: MoneyDatabase): CurrencyDao = db.currencyDao()

    @Provides
    fun provideCurrencyRateDao(db: MoneyDatabase): CurrencyRateDao = db.currencyRateDao()

    @Provides
    fun provideAccountDao(db: MoneyDatabase): AccountDao = db.accountDao()

    @Provides
    fun provideCategoryDao(db: MoneyDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideTransactionDao(db: MoneyDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideBudgetDao(db: MoneyDatabase): BudgetDao = db.budgetDao()

    @Provides
    fun provideRecurringTemplateDao(db: MoneyDatabase): RecurringTemplateDao = db.recurringTemplateDao()

    @Provides
    fun provideSyncLogDao(db: MoneyDatabase): SyncLogDao = db.syncLogDao()

    @Provides
    fun provideSearchHistoryDao(db: MoneyDatabase): SearchHistoryDao = db.searchHistoryDao()

    @Provides
    fun provideGoalDao(db: MoneyDatabase): GoalDao = db.goalDao()
}
