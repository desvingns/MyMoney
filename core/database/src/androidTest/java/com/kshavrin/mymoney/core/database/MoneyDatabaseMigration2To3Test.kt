package com.kshavrin.mymoney.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.database.migration.MIGRATION_2_3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoneyDatabaseMigration2To3Test {

    private val dbName = "migration-2-3-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MoneyDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate2To3_adds_contribution_breakdown_column_and_preserves_existing_goal() {
        helper.createDatabase(dbName, 2).apply {
            execSQL(
                "INSERT INTO `goal` (`name`, `icon_key`, `color_hex`, `account_id`, `variant`, " +
                    "`target_amount`, `starting_capital`, `monthly_contribution`, " +
                    "`annual_rate_percent`, `term_date`, `created_at`, `updated_at`, `is_archived`) " +
                    "VALUES ('Car', 'ic_goal_car', '#7AC794', 1, 'SAVINGS', 500000.0, 10000.0, 20000.0, NULL, NULL, 1000, 1000, 0)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3)

        db.query("SELECT `name`, `variant`, `contribution_breakdown` FROM `goal`").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Car", c.getString(0))
            assertEquals("SAVINGS", c.getString(1))
            assertTrue(c.isNull(2))
        }

        db.execSQL(
            "INSERT INTO `goal` (`name`, `icon_key`, `color_hex`, `account_id`, `variant`, " +
                "`target_amount`, `starting_capital`, `monthly_contribution`, " +
                "`annual_rate_percent`, `term_date`, `created_at`, `updated_at`, `is_archived`, " +
                "`contribution_breakdown`) " +
                "VALUES ('Trip', 'ic_goal_plane', '#FF5733', 1, 'SAVINGS', 150000.0, 5000.0, 10000.0, " +
                "NULL, NULL, 2000, 2000, 0, '{\"enabled\":true,\"incomes\":[],\"expenses\":[]}')",
        )

        db.query(
            "SELECT `contribution_breakdown` FROM `goal` WHERE `name` = 'Trip'",
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("{\"enabled\":true,\"incomes\":[],\"expenses\":[]}", c.getString(0))
        }

        db.query(
            "SELECT `contribution_breakdown` FROM `goal` WHERE `name` = 'Car'",
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertNull(c.getString(0))
        }

        db.close()
    }
}
