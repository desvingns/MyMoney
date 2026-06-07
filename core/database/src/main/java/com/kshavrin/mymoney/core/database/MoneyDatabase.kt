package com.kshavrin.mymoney.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kshavrin.mymoney.core.database.converter.MoneyTypeConverters
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
import com.kshavrin.mymoney.core.database.entity.AccountEntity
import com.kshavrin.mymoney.core.database.entity.BudgetEntity
import com.kshavrin.mymoney.core.database.entity.CategoryEntity
import com.kshavrin.mymoney.core.database.entity.CurrencyEntity
import com.kshavrin.mymoney.core.database.entity.CurrencyRateEntity
import com.kshavrin.mymoney.core.database.entity.GoalEntity
import com.kshavrin.mymoney.core.database.entity.RecurringTemplateEntity
import com.kshavrin.mymoney.core.database.entity.SearchHistoryEntity
import com.kshavrin.mymoney.core.database.entity.SyncLogEntity
import com.kshavrin.mymoney.core.database.entity.TransactionEntity

@Database(
    entities = [
        CurrencyEntity::class,
        CurrencyRateEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        RecurringTemplateEntity::class,
        SyncLogEntity::class,
        SearchHistoryEntity::class,
        GoalEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(MoneyTypeConverters::class)
abstract class MoneyDatabase : RoomDatabase() {
    abstract fun currencyDao(): CurrencyDao
    abstract fun currencyRateDao(): CurrencyRateDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringTemplateDao(): RecurringTemplateDao
    abstract fun syncLogDao(): SyncLogDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun goalDao(): GoalDao
}
