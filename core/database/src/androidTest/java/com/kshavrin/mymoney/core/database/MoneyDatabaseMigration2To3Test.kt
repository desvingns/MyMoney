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

    @Test
    fun migrate2To3_multiple_pre_migration_rows_all_receive_null_breakdown() {
        // All goals that existed before the migration must have contribution_breakdown = NULL —
        // the migration is a pure ADD COLUMN with no default, so SQLite assigns NULL to all rows.
        helper.createDatabase(dbName, 2).apply {
            val insertGoal = "INSERT INTO `goal` (`name`, `icon_key`, `color_hex`, `account_id`, `variant`, " +
                "`target_amount`, `starting_capital`, `monthly_contribution`, " +
                "`annual_rate_percent`, `term_date`, `created_at`, `updated_at`, `is_archived`) " +
                "VALUES "
            execSQL(insertGoal + "('Goal1', 'ic_goal_car', '#111111', 1, 'SAVINGS', 100.0, 0.0, 10.0, NULL, NULL, 1000, 1000, 0)")
            execSQL(insertGoal + "('Goal2', 'ic_goal_house', '#222222', 2, 'CREDIT', 200.0, 50.0, 20.0, 10.0, 19000, 2000, 2000, 0)")
            execSQL(insertGoal + "('Goal3', 'ic_goal_plane', '#333333', 3, 'SAVINGS', 300.0, 0.0, 30.0, NULL, NULL, 3000, 3000, 1)")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3)

        db.query("SELECT `name`, `contribution_breakdown` FROM `goal` ORDER BY `name`").use { c ->
            assertEquals(3, c.count)
            assertTrue(c.moveToFirst())
            assertEquals("Goal1", c.getString(0))
            assertTrue("Goal1 must have NULL breakdown after migration", c.isNull(1))
            assertTrue(c.moveToNext())
            assertEquals("Goal2", c.getString(0))
            assertTrue("Goal2 must have NULL breakdown after migration", c.isNull(1))
            assertTrue(c.moveToNext())
            assertEquals("Goal3", c.getString(0))
            assertTrue("Goal3 must have NULL breakdown after migration", c.isNull(1))
        }

        db.close()
    }

    @Test
    fun migrate2To3_contribution_breakdown_column_is_TEXT_affinity() {
        // Verify the column was added with TEXT affinity, not REAL/INTEGER, so that exact
        // BigDecimal plain-string values are stored without coercion.
        helper.createDatabase(dbName, 2).apply { close() }

        val db = helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3)

        var found = false
        db.query("PRAGMA table_info(`goal`)").use { c ->
            while (c.moveToNext()) {
                val colName = c.getString(c.getColumnIndexOrThrow("name"))
                if (colName == "contribution_breakdown") {
                    val colType = c.getString(c.getColumnIndexOrThrow("type"))
                    assertEquals("TEXT", colType)
                    found = true
                }
            }
        }
        assertTrue("contribution_breakdown column must exist in goal table", found)

        db.close()
    }

    @Test
    fun migrate2To3_complex_breakdown_json_with_multiple_items_round_trips_intact() {
        // Insert a goal after migration with a full breakdown JSON containing multiple incomes
        // and expenses using BigDecimal plain-string amounts (no JSON numbers/Doubles) — verify
        // that the exact JSON string is preserved byte-for-byte by SQLite TEXT storage.
        helper.createDatabase(dbName, 2).apply { close() }

        val db = helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3)

        val complexJson = """{"enabled":true,"incomes":[{"name":"Salary","amount":"120000.55"},{"name":"Bonus","amount":"3000.00"},{"name":"Freelance","amount":"15000.50"}],"expenses":[{"name":"Rent","amount":"45000.10"},{"name":"Utilities","amount":"5000.00"}]}"""

        db.execSQL(
            "INSERT INTO `goal` (`name`, `icon_key`, `color_hex`, `account_id`, `variant`, " +
                "`target_amount`, `starting_capital`, `monthly_contribution`, " +
                "`annual_rate_percent`, `term_date`, `created_at`, `updated_at`, `is_archived`, " +
                "`contribution_breakdown`) " +
                "VALUES ('Complex', 'ic_goal_car', '#AABBCC', 1, 'SAVINGS', 999999.99, 0.0, 50000.0, " +
                "NULL, NULL, 3000, 3000, 0, '$complexJson')",
        )

        db.query("SELECT `contribution_breakdown` FROM `goal` WHERE `name` = 'Complex'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(complexJson, c.getString(0))
        }

        db.close()
    }

    @Test
    fun migrate2To3_negative_amount_in_breakdown_json_is_stored_as_text_unchanged() {
        // Negative amounts are allowed in breakdown items. Storing as TEXT means the minus sign
        // survives — SQLite would coerce "-500.50" to a REAL if the column were NUMERIC.
        helper.createDatabase(dbName, 2).apply { close() }

        val db = helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3)

        val jsonWithNegative = """{"enabled":true,"incomes":[],"expenses":[{"name":"Overspend","amount":"-500.50"}]}"""

        db.execSQL(
            "INSERT INTO `goal` (`name`, `icon_key`, `color_hex`, `account_id`, `variant`, " +
                "`target_amount`, `starting_capital`, `monthly_contribution`, " +
                "`annual_rate_percent`, `term_date`, `created_at`, `updated_at`, `is_archived`, " +
                "`contribution_breakdown`) " +
                "VALUES ('NegativeTest', 'ic_goal_car', '#000000', 1, 'SAVINGS', 1000.0, 0.0, 100.0, " +
                "NULL, NULL, 4000, 4000, 0, '$jsonWithNegative')",
        )

        db.query("SELECT `contribution_breakdown` FROM `goal` WHERE `name` = 'NegativeTest'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(jsonWithNegative, c.getString(0))
        }

        db.close()
    }

    @Test
    fun migrate2To3_existing_data_other_columns_are_not_corrupted() {
        // Verify that the migration does not corrupt any pre-existing column values in the row.
        helper.createDatabase(dbName, 2).apply {
            execSQL(
                "INSERT INTO `goal` (`name`, `icon_key`, `color_hex`, `account_id`, `variant`, " +
                    "`target_amount`, `starting_capital`, `monthly_contribution`, " +
                    "`annual_rate_percent`, `term_date`, `created_at`, `updated_at`, `is_archived`) " +
                    "VALUES ('Integrity', 'ic_goal_house', '#FFFFFF', 5, 'CREDIT', " +
                    "10000000.0, 2000000.0, 85000.0, 12.5, 19000, 1111, 2222, 1)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3)

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
            assertTrue("contribution_breakdown must be NULL for pre-migration row", c.isNull(13))
        }

        db.close()
    }
}
