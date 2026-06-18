package com.kshavrin.mymoney.core.database.di

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
import com.kshavrin.mymoney.core.database.migration.MIGRATION_1_2
import com.kshavrin.mymoney.core.database.migration.MIGRATION_2_3
import com.kshavrin.mymoney.core.database.migration.MIGRATION_3_4
import com.kshavrin.mymoney.core.database.migration.MIGRATION_4_5
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideMoneyDatabase(
        @ApplicationContext context: Context,
    ): MoneyDatabase =
        Room
            .databaseBuilder(context, MoneyDatabase::class.java, "monefy.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .fallbackToDestructiveMigrationFrom(99)
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
