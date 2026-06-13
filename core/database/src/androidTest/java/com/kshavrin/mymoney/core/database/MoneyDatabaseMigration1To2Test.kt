package com.kshavrin.mymoney.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.database.migration.MIGRATION_1_2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoneyDatabaseMigration1To2Test {
    private val dbName = "migration-1-2-test.db"

    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MoneyDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migrate1To2_creates_goal_table_and_preserves_existing_data() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO `currency` (`id`, `code`, `symbol`, `name`, `decimal_digits`, `is_active`, `sort_order`) " +
                    "VALUES (1, 'USD', '\$', 'US Dollar', 2, 1, 0)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)

        db.query("SELECT code FROM `currency` WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("USD", c.getString(0))
        }

        db.query("SELECT COUNT(*) FROM `goal`").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }

        db.execSQL(
            "INSERT INTO `goal` (`name`, `icon_key`, `color_hex`, `account_id`, `variant`, " +
                "`target_amount`, `starting_capital`, `monthly_contribution`, " +
                "`annual_rate_percent`, `term_date`, `created_at`, `updated_at`, `is_archived`) " +
                "VALUES ('Car', 'ic_goal_car', '#7AC794', 1, 'SAVINGS', 500000.0, 10000.0, 20000.0, NULL, NULL, 1000, 1000, 0)",
        )

        db.query("SELECT `name`, `variant`, `is_archived` FROM `goal`").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Car", c.getString(0))
            assertEquals("SAVINGS", c.getString(1))
            assertEquals(0, c.getInt(2))
        }

        db.close()
    }
}
