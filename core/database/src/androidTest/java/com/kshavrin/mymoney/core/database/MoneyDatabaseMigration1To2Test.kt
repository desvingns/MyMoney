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
    val helper = MigrationTestHelper(
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
        db.close()
    }
}
