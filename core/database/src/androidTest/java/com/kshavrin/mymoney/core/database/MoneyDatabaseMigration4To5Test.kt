package com.kshavrin.mymoney.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.database.migration.MIGRATION_4_5
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoneyDatabaseMigration4To5Test {
    private val dbName = "migration-4-5-test.db"

    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MoneyDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun `migrate 4 to 5 rounds every monetary column to two decimal places`() {
        helper.createDatabase(dbName, 4).apply {
            seedCurrency()
            execSQL(
                "INSERT INTO `account` " +
                    "(`id`, `name`, `currency_id`, `initial_balance`, `type`, `color_hex`, `icon_key`, " +
                    "`is_default`, `sort_order`, `created_at`, `updated_at`, `is_archived`) " +
                    "VALUES (1, 'Cash', 1, 9876.54321, 'CASH', '#FFFFFF', 'cash', 1, 0, 1000, 1000, 0)",
            )
            execSQL(
                "INSERT INTO `transaction` " +
                    "(`id`, `kind`, `amount`, `currency_id`, `account_id`, `category_id`, `note`, " +
                    "`occurred_at`, `created_at`, `updated_at`, `is_deleted`, `to_account_id`, " +
                    "`to_amount`, `exchange_rate`) " +
                    "VALUES (1, 'TRANSFER', -1182337.0799999996, 1, 1, NULL, NULL, 1000, 1000, 1000, " +
                    "0, 1, 0.30000000000000004, 1.234567)",
            )
            execSQL(
                "INSERT INTO `goal` " +
                    "(`id`, `name`, `icon_key`, `color_hex`, `account_id`, `variant`, `target_amount`, " +
                    "`starting_capital`, `monthly_contribution`, `annual_rate_percent`, `down_payment`, " +
                    "`term_months`, `term_date`, `created_at`, `updated_at`, `is_archived`, " +
                    "`contribution_breakdown`) " +
                    "VALUES (1, 'Home', 'house', '#000000', 1, 'CREDIT', 123456.789, " +
                    "0.30000000000000004, 100.50, 12.345, 45678.7654, 241, NULL, 1000, 1000, 0, NULL)",
            )
            execSQL(
                "INSERT INTO `budget` " +
                    "(`id`, `category_id`, `period_kind`, `period_start`, `amount`, `currency_id`, " +
                    "`alert_threshold_pct`, `is_active`) " +
                    "VALUES (1, NULL, 'MONTH', 1000, 765.4321, 1, 80, 1)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 5, true, MIGRATION_4_5)

        assertRealValues(db, "SELECT `amount`, `to_amount` FROM `transaction`", -1_182_337.08, 0.30)
        assertRealValues(db, "SELECT `initial_balance` FROM `account`", 9_876.54)
        assertRealValues(
            db,
            "SELECT `target_amount`, `starting_capital`, `monthly_contribution`, `down_payment` FROM `goal`",
            123_456.79,
            0.30,
            100.50,
            45_678.77,
        )
        assertRealValues(db, "SELECT `amount` FROM `budget`", 765.43)

        db.close()
    }

    @Test
    fun `migrate 4 to 5 preserves null monetary values`() {
        helper.createDatabase(dbName, 4).apply {
            seedCurrency()
            execSQL(
                "INSERT INTO `account` " +
                    "(`id`, `name`, `currency_id`, `initial_balance`, `type`, `color_hex`, `icon_key`, " +
                    "`is_default`, `sort_order`, `created_at`, `updated_at`, `is_archived`) " +
                    "VALUES (1, 'Cash', 1, 0.0, 'CASH', '#FFFFFF', 'cash', 1, 0, 1000, 1000, 0)",
            )
            execSQL(
                "INSERT INTO `transaction` " +
                    "(`id`, `kind`, `amount`, `currency_id`, `account_id`, `category_id`, `note`, " +
                    "`occurred_at`, `created_at`, `updated_at`, `is_deleted`, `to_account_id`, " +
                    "`to_amount`, `exchange_rate`) " +
                    "VALUES (1, 'EXPENSE', 10.129, 1, 1, NULL, NULL, 1000, 1000, 1000, " +
                    "0, NULL, NULL, NULL)",
            )
            execSQL(
                "INSERT INTO `goal` " +
                    "(`id`, `name`, `icon_key`, `color_hex`, `account_id`, `variant`, `target_amount`, " +
                    "`starting_capital`, `monthly_contribution`, `annual_rate_percent`, `down_payment`, " +
                    "`term_months`, `term_date`, `created_at`, `updated_at`, `is_archived`, " +
                    "`contribution_breakdown`) " +
                    "VALUES (1, 'Trip', 'plane', '#000000', 1, 'SAVINGS', 1000.0, " +
                    "0.0, 100.0, NULL, NULL, NULL, NULL, 1000, 1000, 0, NULL)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 5, true, MIGRATION_4_5)

        db.query("SELECT `to_amount` FROM `transaction`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
        }
        db.query("SELECT `down_payment` FROM `goal`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
        }

        db.close()
    }

    @Test
    fun `migrate 4 to 5 leaves annual rate and term months unchanged`() {
        helper.createDatabase(dbName, 4).apply {
            execSQL(
                "INSERT INTO `goal` " +
                    "(`id`, `name`, `icon_key`, `color_hex`, `account_id`, `variant`, `target_amount`, " +
                    "`starting_capital`, `monthly_contribution`, `annual_rate_percent`, `down_payment`, " +
                    "`term_months`, `term_date`, `created_at`, `updated_at`, `is_archived`, " +
                    "`contribution_breakdown`) " +
                    "VALUES (1, 'Mortgage', 'house', '#000000', 1, 'CREDIT', 1000000.009, " +
                    "100000.009, 10000.009, 12.345, 250000.009, 241, NULL, 1000, 1000, 0, NULL)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 5, true, MIGRATION_4_5)

        db.query("SELECT `annual_rate_percent`, `term_months` FROM `goal`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(12.345, cursor.getDouble(0), 0.0)
            assertEquals(241, cursor.getInt(1))
        }

        db.close()
    }

    private fun SupportSQLiteDatabase.seedCurrency() {
        execSQL(
            "INSERT INTO `currency` " +
                "(`id`, `code`, `symbol`, `name`, `decimal_digits`, `is_active`, `sort_order`) " +
                "VALUES (1, 'USD', '\$', 'US Dollar', 2, 1, 0)",
        )
    }

    private fun assertRealValues(
        db: SupportSQLiteDatabase,
        query: String,
        vararg expected: Double,
    ) {
        db.query(query).use { cursor ->
            assertTrue(cursor.moveToFirst())
            expected.forEachIndexed { index, value ->
                assertEquals(value, cursor.getDouble(index), 0.0)
            }
        }
    }
}
