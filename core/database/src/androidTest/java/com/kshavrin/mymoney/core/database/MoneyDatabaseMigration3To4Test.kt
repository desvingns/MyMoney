package com.kshavrin.mymoney.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.database.migration.MIGRATION_3_4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoneyDatabaseMigration3To4Test {

    private val dbName = "migration-3-4-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MoneyDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate3To4_adds_down_payment_and_term_months_columns() {
        helper.createDatabase(dbName, 3).apply { close() }

        val db = helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4)

        var foundDownPayment = false
        var foundTermMonths = false
        db.query("PRAGMA table_info(`goal`)").use { c ->
            while (c.moveToNext()) {
                when (c.getString(c.getColumnIndexOrThrow("name"))) {
                    "down_payment" -> foundDownPayment = true
                    "term_months" -> foundTermMonths = true
                }
            }
        }
        assertTrue("down_payment column must exist in goal table after migration", foundDownPayment)
        assertTrue("term_months column must exist in goal table after migration", foundTermMonths)

        db.close()
    }

    @Test
    fun migrate3To4_down_payment_column_has_REAL_affinity() {
        helper.createDatabase(dbName, 3).apply { close() }

        val db = helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4)

        var colType: String? = null
        db.query("PRAGMA table_info(`goal`)").use { c ->
            while (c.moveToNext()) {
                if (c.getString(c.getColumnIndexOrThrow("name")) == "down_payment") {
                    colType = c.getString(c.getColumnIndexOrThrow("type"))
                }
            }
        }
        assertEquals("REAL", colType)

        db.close()
    }

    @Test
    fun migrate3To4_term_months_column_has_INTEGER_affinity() {
        helper.createDatabase(dbName, 3).apply { close() }

        val db = helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4)

        var colType: String? = null
        db.query("PRAGMA table_info(`goal`)").use { c ->
            while (c.moveToNext()) {
                if (c.getString(c.getColumnIndexOrThrow("name")) == "term_months") {
                    colType = c.getString(c.getColumnIndexOrThrow("type"))
                }
            }
        }
        assertEquals("INTEGER", colType)

        db.close()
    }

    @Test
    fun migrate3To4_pre_existing_savings_row_has_null_down_payment_and_term_months() {
        // A SAVINGS goal created at v3 (with term_date populated, down_payment/term_months absent)
        // must survive migration: down_payment=NULL, term_months=NULL, no data loss on other columns.
        helper.createDatabase(dbName, 3).apply {
            execSQL(
                "INSERT INTO `goal` (`name`, `icon_key`, `color_hex`, `account_id`, `variant`, " +
                    "`target_amount`, `starting_capital`, `monthly_contribution`, " +
                    "`annual_rate_percent`, `term_date`, `created_at`, `updated_at`, `is_archived`, " +
                    "`contribution_breakdown`) " +
                    "VALUES ('Car', 'ic_goal_car', '#7AC794', 2, 'SAVINGS', 500000.0, 10000.0, 20000.0, " +
                    "NULL, NULL, 1000, 1000, 0, NULL)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4)

        db.query(
            "SELECT `name`, `variant`, `down_payment`, `term_months` FROM `goal` WHERE `name` = 'Car'",
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Car", c.getString(0))
            assertEquals("SAVINGS", c.getString(1))
            assertTrue("down_payment must be NULL for pre-migration savings row", c.isNull(2))
            assertTrue("term_months must be NULL for pre-migration savings row", c.isNull(3))
        }

        db.close()
    }

    @Test
    fun migrate3To4_pre_existing_credit_row_with_term_date_gets_null_new_columns() {
        // A CREDIT goal stored at v3 had term_date (legacy column) set.
        // After migration: down_payment=NULL and term_months=NULL (ADD COLUMN adds NULLs for existing rows).
        // term_date value must be preserved — this verifies no destructive rebuild occurred.
        helper.createDatabase(dbName, 3).apply {
            execSQL(
                "INSERT INTO `goal` (`name`, `icon_key`, `color_hex`, `account_id`, `variant`, " +
                    "`target_amount`, `starting_capital`, `monthly_contribution`, " +
                    "`annual_rate_percent`, `term_date`, `created_at`, `updated_at`, `is_archived`, " +
                    "`contribution_breakdown`) " +
                    "VALUES ('Mortgage', 'ic_goal_house', '#4A90D9', 1, 'CREDIT', 10000000.0, 2000000.0, " +
                    "85000.0, 12.5, 19000, 1000, 1000, 0, NULL)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4)

        db.query(
            "SELECT `name`, `variant`, `annual_rate_percent`, `term_date`, " +
                "`down_payment`, `term_months` FROM `goal` WHERE `name` = 'Mortgage'",
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Mortgage", c.getString(0))
            assertEquals("CREDIT", c.getString(1))
            assertEquals(12.5, c.getDouble(2), 0.0)
            assertEquals(19000L, c.getLong(3))         // term_date preserved
            assertTrue("down_payment must be NULL for pre-migration credit row", c.isNull(4))
            assertTrue("term_months must be NULL for pre-migration credit row", c.isNull(5))
        }

        db.close()
    }

    @Test
    fun migrate3To4_all_pre_existing_rows_get_null_for_both_new_columns() {
        // Multiple pre-migration rows — all must receive NULL for down_payment and term_months.
        helper.createDatabase(dbName, 3).apply {
            val base = "INSERT INTO `goal` (`name`, `icon_key`, `color_hex`, `account_id`, `variant`, " +
                "`target_amount`, `starting_capital`, `monthly_contribution`, " +
                "`annual_rate_percent`, `term_date`, `created_at`, `updated_at`, `is_archived`, " +
                "`contribution_breakdown`) VALUES "
            execSQL(base + "('Goal1', 'ic_g', '#111', 1, 'SAVINGS', 100.0, 0.0, 10.0, NULL, NULL, 1000, 1000, 0, NULL)")
            execSQL(base + "('Goal2', 'ic_g', '#222', 2, 'CREDIT', 200.0, 50.0, 20.0, 10.0, 19000, 2000, 2000, 0, NULL)")
            execSQL(base + "('Goal3', 'ic_g', '#333', 3, 'SAVINGS', 300.0, 0.0, 30.0, NULL, NULL, 3000, 3000, 1, NULL)")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4)

        db.query(
            "SELECT `name`, `down_payment`, `term_months` FROM `goal` ORDER BY `name`",
        ).use { c ->
            assertEquals(3, c.count)
            assertTrue(c.moveToFirst())
            assertEquals("Goal1", c.getString(0))
            assertTrue("Goal1 must have NULL down_payment", c.isNull(1))
            assertTrue("Goal1 must have NULL term_months", c.isNull(2))
            assertTrue(c.moveToNext())
            assertEquals("Goal2", c.getString(0))
            assertTrue("Goal2 must have NULL down_payment", c.isNull(1))
            assertTrue("Goal2 must have NULL term_months", c.isNull(2))
            assertTrue(c.moveToNext())
            assertEquals("Goal3", c.getString(0))
            assertTrue("Goal3 must have NULL down_payment", c.isNull(1))
            assertTrue("Goal3 must have NULL term_months", c.isNull(2))
        }

        db.close()
    }

    @Test
    fun migrate3To4_new_row_inserted_after_migration_with_down_payment_and_term_months_round_trips() {
        // After the migration, inserting a new CREDIT goal with down_payment and term_months set
        // must survive a read-back (verifies the column is writable and readable at the SQL level).
        helper.createDatabase(dbName, 3).apply { close() }

        val db = helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4)

        db.execSQL(
            "INSERT INTO `goal` (`name`, `icon_key`, `color_hex`, `account_id`, `variant`, " +
                "`target_amount`, `starting_capital`, `monthly_contribution`, " +
                "`annual_rate_percent`, `term_date`, `created_at`, `updated_at`, `is_archived`, " +
                "`contribution_breakdown`, `down_payment`, `term_months`) " +
                "VALUES ('HomeLoan', 'ic_goal_house', '#4A90D9', 1, 'CREDIT', " +
                "10000000.0, 2000000.0, 85000.0, 12.5, NULL, 5000, 5000, 0, NULL, 2000000.0, 240)",
        )

        db.query(
            "SELECT `name`, `down_payment`, `term_months` FROM `goal` WHERE `name` = 'HomeLoan'",
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("HomeLoan", c.getString(0))
            assertEquals(2_000_000.0, c.getDouble(1), 0.0)
            assertEquals(240, c.getInt(2))
        }

        db.close()
    }

    @Test
    fun migrate3To4_pre_existing_columns_are_not_corrupted() {
        // All columns present in v3 must retain their values exactly after the additive migration.
        helper.createDatabase(dbName, 3).apply {
            execSQL(
                "INSERT INTO `goal` (`name`, `icon_key`, `color_hex`, `account_id`, `variant`, " +
                    "`target_amount`, `starting_capital`, `monthly_contribution`, " +
                    "`annual_rate_percent`, `term_date`, `created_at`, `updated_at`, `is_archived`, " +
                    "`contribution_breakdown`) " +
                    "VALUES ('Integrity', 'ic_goal_house', '#FFFFFF', 5, 'CREDIT', " +
                    "10000000.0, 2000000.0, 85000.0, 12.5, 19000, 1111, 2222, 1, " +
                    "'{\"enabled\":true,\"incomes\":[],\"expenses\":[]}')",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4)

        db.query(
            "SELECT `name`, `icon_key`, `color_hex`, `account_id`, `variant`, " +
                "`target_amount`, `starting_capital`, `monthly_contribution`, " +
                "`annual_rate_percent`, `term_date`, `created_at`, `updated_at`, `is_archived`, " +
                "`contribution_breakdown` FROM `goal` WHERE `name` = 'Integrity'",
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Integrity", c.getString(0))
            assertEquals("ic_goal_house", c.getString(1))
            assertEquals("#FFFFFF", c.getString(2))
            assertEquals(5L, c.getLong(3))
            assertEquals("CREDIT", c.getString(4))
            assertEquals(10_000_000.0, c.getDouble(5), 0.0)
            assertEquals(2_000_000.0, c.getDouble(6), 0.0)
            assertEquals(85_000.0, c.getDouble(7), 0.0)
            assertEquals(12.5, c.getDouble(8), 0.0)
            assertEquals(19000L, c.getLong(9))
            assertEquals(1111L, c.getLong(10))
            assertEquals(2222L, c.getLong(11))
            assertEquals(1, c.getInt(12))
            assertEquals("{\"enabled\":true,\"incomes\":[],\"expenses\":[]}", c.getString(13))
        }

        db.close()
    }

    @Test
    fun migrate3To4_new_savings_row_with_null_down_payment_and_term_months_round_trips() {
        helper.createDatabase(dbName, 3).apply { close() }

        val db = helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4)

        db.execSQL(
            "INSERT INTO `goal` (`name`, `icon_key`, `color_hex`, `account_id`, `variant`, " +
                "`target_amount`, `starting_capital`, `monthly_contribution`, " +
                "`annual_rate_percent`, `term_date`, `created_at`, `updated_at`, `is_archived`, " +
                "`contribution_breakdown`, `down_payment`, `term_months`) " +
                "VALUES ('VacationFund', 'ic_goal_savings', '#7AC794', 2, 'SAVINGS', " +
                "500000.0, 10000.0, 20000.0, NULL, NULL, 6000, 6000, 0, NULL, NULL, NULL)",
        )

        db.query(
            "SELECT `name`, `variant`, `down_payment`, `term_months` FROM `goal` WHERE `name` = 'VacationFund'",
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("VacationFund", c.getString(0))
            assertEquals("SAVINGS", c.getString(1))
            assertTrue("down_payment must be NULL for new savings row", c.isNull(2))
            assertTrue("term_months must be NULL for new savings row", c.isNull(3))
        }

        db.close()
    }
}
