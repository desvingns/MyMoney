package com.kshavrin.mymoney.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.database.migration.MIGRATION_7_8
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoneyDatabaseMigration7To8Test {
    private val dbName = "migration-7-8-test.db"

    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MoneyDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    // ─── op_journal table ──────────────────────────────────────────────────

    @Test
    fun `migrate 7 to 8 creates op_journal table`() {
        helper.createDatabase(dbName, 7).apply { close() }

        val db = helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8)

        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='op_journal'").use { c ->
            assertTrue("op_journal table must exist after migration", c.moveToFirst())
        }

        db.close()
    }

    @Test
    fun `migrate 7 to 8 creates index on op_journal entity_uuid`() {
        helper.createDatabase(dbName, 7).apply { close() }

        val db = helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8)

        db
            .query(
                "SELECT name FROM sqlite_master WHERE type='index' AND name='index_op_journal_entity_uuid'",
            ).use { c ->
                assertTrue("index_op_journal_entity_uuid must exist after migration", c.moveToFirst())
            }

        db.close()
    }

    @Test
    fun `migrate 7 to 8 creates index on op_journal synced_to_remote`() {
        helper.createDatabase(dbName, 7).apply { close() }

        val db = helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8)

        db
            .query(
                "SELECT name FROM sqlite_master WHERE type='index' AND name='index_op_journal_synced_to_remote'",
            ).use { c ->
                assertTrue("index_op_journal_synced_to_remote must exist after migration", c.moveToFirst())
            }

        db.close()
    }

    @Test
    fun `migrate 7 to 8 op_journal is empty after migration with no data`() {
        helper.createDatabase(dbName, 7).apply { close() }

        val db = helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8)

        db.query("SELECT COUNT(*) FROM op_journal").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("op_journal must be empty when no ops were inserted", 0, c.getInt(0))
        }

        db.close()
    }

    // ─── categories.updated_at column ──────────────────────────────────────

    @Test
    fun `migrate 7 to 8 adds updated_at column to category with default 0`() {
        helper.createDatabase(dbName, 7).apply {
            seedCategory(name = "Food", iconKey = "ic_cat_food")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8)

        db.query("SELECT updated_at FROM category WHERE name = 'Food'").use { c ->
            assertTrue("category row must survive migration", c.moveToFirst())
            assertEquals(
                "categories.updated_at must default to 0 post-migration",
                0L,
                c.getLong(0),
            )
        }

        db.close()
    }

    // ─── uuid columns ──────────────────────────────────────────────────────

    @Test
    fun `migrate 7 to 8 adds uuid column to transaction and backfills non-empty value`() {
        helper.createDatabase(dbName, 7).apply {
            seedTransaction()
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8)

        db.query("SELECT uuid FROM `transaction`").use { c ->
            assertTrue("transaction row must exist", c.moveToFirst())
            val uuid = c.getString(0)
            assertFalse("transaction uuid must not be empty after backfill", uuid.isEmpty())
            assertEquals("transaction uuid must be 32 hex chars", 32, uuid.length)
        }

        db.close()
    }

    @Test
    fun `migrate 7 to 8 adds uuid column to category and backfills non-empty value`() {
        helper.createDatabase(dbName, 7).apply {
            seedCategory(name = "Bills", iconKey = "ic_cat_bills")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8)

        db.query("SELECT uuid FROM category WHERE name = 'Bills'").use { c ->
            assertTrue("category row must exist", c.moveToFirst())
            val uuid = c.getString(0)
            assertFalse("category uuid must not be empty after backfill", uuid.isEmpty())
            assertEquals("category uuid must be 32 hex chars", 32, uuid.length)
        }

        db.close()
    }

    @Test
    fun `migrate 7 to 8 adds uuid column to account and backfills non-empty value`() {
        helper.createDatabase(dbName, 7).apply {
            seedAccount(name = "Cash")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8)

        db.query("SELECT uuid FROM account WHERE name = 'Cash'").use { c ->
            assertTrue("account row must exist", c.moveToFirst())
            val uuid = c.getString(0)
            assertFalse("account uuid must not be empty after backfill", uuid.isEmpty())
            assertEquals("account uuid must be 32 hex chars", 32, uuid.length)
        }

        db.close()
    }

    // ─── uuid uniqueness ───────────────────────────────────────────────────

    @Test
    fun `migrate 7 to 8 backfills unique uuid values across transaction rows`() {
        helper.createDatabase(dbName, 7).apply {
            seedTransaction()
            seedTransaction()
            seedTransaction()
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8)

        db.query("SELECT COUNT(DISTINCT uuid) FROM `transaction`").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(
                "all transaction uuid values must be unique after backfill",
                3,
                c.getInt(0),
            )
        }

        db.close()
    }

    @Test
    fun `migrate 7 to 8 backfills unique uuid values across category rows`() {
        helper.createDatabase(dbName, 7).apply {
            seedCategory(name = "Cat1", iconKey = "ic_cat_food")
            seedCategory(name = "Cat2", iconKey = "ic_cat_food")
            seedCategory(name = "Cat3", iconKey = "ic_cat_food")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8)

        db.query("SELECT COUNT(DISTINCT uuid) FROM category").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(
                "all category uuid values must be unique after backfill",
                3,
                c.getInt(0),
            )
        }

        db.close()
    }

    @Test
    fun `migrate 7 to 8 backfills unique uuid values across account rows`() {
        helper.createDatabase(dbName, 7).apply {
            seedAccount(name = "Acc1")
            seedAccount(name = "Acc2")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8)

        db.query("SELECT COUNT(DISTINCT uuid) FROM account").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(
                "all account uuid values must be unique after backfill",
                2,
                c.getInt(0),
            )
        }

        db.close()
    }

    // ─── UNIQUE index existence ─────────────────────────────────────────────

    @Test
    fun `migrate 7 to 8 creates unique index on transaction uuid`() {
        helper.createDatabase(dbName, 7).apply { close() }

        val db = helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8)

        db
            .query(
                "SELECT name FROM sqlite_master WHERE type='index' AND name='index_transaction_uuid'",
            ).use { c ->
                assertTrue("index_transaction_uuid must exist after migration", c.moveToFirst())
            }

        db.close()
    }

    @Test
    fun `migrate 7 to 8 creates unique index on category uuid`() {
        helper.createDatabase(dbName, 7).apply { close() }

        val db = helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8)

        db
            .query(
                "SELECT name FROM sqlite_master WHERE type='index' AND name='index_category_uuid'",
            ).use { c ->
                assertTrue("index_category_uuid must exist after migration", c.moveToFirst())
            }

        db.close()
    }

    @Test
    fun `migrate 7 to 8 creates unique index on account uuid`() {
        helper.createDatabase(dbName, 7).apply { close() }

        val db = helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8)

        db
            .query(
                "SELECT name FROM sqlite_master WHERE type='index' AND name='index_account_uuid'",
            ).use { c ->
                assertTrue("index_account_uuid must exist after migration", c.moveToFirst())
            }

        db.close()
    }

    // ─── device_id column ──────────────────────────────────────────────────

    @Test
    fun `migrate 7 to 8 adds device_id column to transaction with empty default`() {
        helper.createDatabase(dbName, 7).apply {
            seedTransaction()
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8)

        db.query("SELECT device_id FROM `transaction`").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(
                "transaction device_id must be empty string after migration",
                "",
                c.getString(0),
            )
        }

        db.close()
    }

    @Test
    fun `migrate 7 to 8 adds device_id column to category with empty default`() {
        helper.createDatabase(dbName, 7).apply {
            seedCategory(name = "Transport", iconKey = "ic_cat_taxi")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8)

        db.query("SELECT device_id FROM category WHERE name = 'Transport'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(
                "category device_id must be empty string after migration",
                "",
                c.getString(0),
            )
        }

        db.close()
    }

    @Test
    fun `migrate 7 to 8 adds device_id column to account with empty default`() {
        helper.createDatabase(dbName, 7).apply {
            seedAccount(name = "Wallet")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8)

        db.query("SELECT device_id FROM account WHERE name = 'Wallet'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(
                "account device_id must be empty string after migration",
                "",
                c.getString(0),
            )
        }

        db.close()
    }

    // ─── no existing uuid rows disturbed ───────────────────────────────────

    @Test
    fun `migrate 7 to 8 leaves empty tables intact`() {
        helper.createDatabase(dbName, 7).apply { close() }

        val db = helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8)

        listOf("transaction", "category", "account").forEach { table ->
            db.query("SELECT COUNT(*) FROM `$table`").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("$table must remain empty when no rows were seeded", 0, c.getInt(0))
            }
        }

        db.close()
    }

    @Test
    fun `migrate 7 to 8 leaves no uuid empty after backfill with multiple rows per table`() {
        helper.createDatabase(dbName, 7).apply {
            seedTransaction()
            seedTransaction()
            seedCategory(name = "Groceries", iconKey = "ic_cat_food")
            seedCategory(name = "Rent", iconKey = "ic_cat_bills")
            seedAccount(name = "Bank")
            seedAccount(name = "Savings")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8)

        listOf("transaction", "category", "account").forEach { table ->
            db.query("SELECT COUNT(*) FROM `$table` WHERE uuid = '' OR uuid IS NULL").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(
                    "$table must have no empty uuid values after backfill",
                    0,
                    c.getInt(0),
                )
            }
        }

        db.close()
    }

    // ─── seed helpers ──────────────────────────────────────────────────────

    private fun SupportSQLiteDatabase.seedTransaction(
        kind: String = "expense",
        amount: Double = 100.0,
    ) {
        val now = System.currentTimeMillis()
        execSQL(
            "INSERT INTO `transaction` " +
                "(`kind`, `amount`, `currency_id`, `account_id`, `category_id`, `note`, " +
                "`occurred_at`, `created_at`, `updated_at`, `is_deleted`, `to_account_id`, " +
                "`to_amount`, `exchange_rate`) " +
                "VALUES ('$kind', $amount, 1, 1, NULL, NULL, $now, $now, $now, 0, NULL, NULL, NULL)",
        )
    }

    private fun SupportSQLiteDatabase.seedCategory(
        name: String,
        iconKey: String,
        kind: String = "expense",
    ) {
        val now = System.currentTimeMillis()
        execSQL(
            "INSERT INTO `category` " +
                "(`name`, `kind`, `icon_key`, `color_hex`, `text_color`, `sort_order`, " +
                "`is_default`, `is_archived`, `created_at`) " +
                "VALUES ('${name.replace("'", "''")}', '$kind', '$iconKey', '#AABBCC', '#FFFFFF', " +
                "0, 0, 0, $now)",
        )
    }

    private fun SupportSQLiteDatabase.seedAccount(name: String) {
        val now = System.currentTimeMillis()
        execSQL(
            "INSERT INTO `account` " +
                "(`name`, `currency_id`, `initial_balance`, `type`, `color_hex`, `icon_key`, " +
                "`is_default`, `sort_order`, `created_at`, `updated_at`, `is_archived`) " +
                "VALUES ('${name.replace("'", "''")}', 1, 0.0, 'cash', '#7AC794', 'ic_cash', " +
                "0, 0, $now, $now, 0)",
        )
    }
}
