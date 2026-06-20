package com.kshavrin.mymoney.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.database.migration.MIGRATION_5_6
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoneyDatabaseMigration5To6Test {
    private val dbName = "migration-5-6-test.db"

    private val snapshotMillis = 1781913600000L

    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MoneyDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun `migrate 5 to 6 inserts KZT currency`() {
        helper.createDatabase(dbName, 5).apply { close() }

        val db = helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6)

        db.query("SELECT `code`, `symbol`, `name`, `decimal_digits`, `is_active`, `sort_order` FROM `currency` WHERE `code` = 'KZT'").use { c ->
            assertTrue("KZT currency must exist after migration", c.moveToFirst())
            assertEquals("KZT", c.getString(0))
            assertEquals("₸", c.getString(1))
            assertEquals("Kazakhstani Tenge", c.getString(2))
            assertEquals(2, c.getInt(3))
            assertEquals(1, c.getInt(4))
            assertEquals(21, c.getInt(5))
        }

        db.close()
    }

    @Test
    fun `migrate 5 to 6 inserts AED currency`() {
        helper.createDatabase(dbName, 5).apply { close() }

        val db = helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6)

        db.query("SELECT `code`, `symbol`, `name`, `decimal_digits`, `is_active`, `sort_order` FROM `currency` WHERE `code` = 'AED'").use { c ->
            assertTrue("AED currency must exist after migration", c.moveToFirst())
            assertEquals("AED", c.getString(0))
            assertEquals("د.إ", c.getString(1))
            assertEquals("UAE Dirham", c.getString(2))
            assertEquals(2, c.getInt(3))
            assertEquals(1, c.getInt(4))
            assertEquals(22, c.getInt(5))
        }

        db.close()
    }

    @Test
    fun `migrate 5 to 6 inserts five EUR-based rates`() {
        helper.createDatabase(dbName, 5).apply {
            seedRequiredCurrencies()
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6)

        val eurId = db.queryCurrencyId("EUR")
        val expectedToCodes = listOf("USD", "RUB", "RSD", "KZT", "AED")

        db
            .query(
                "SELECT cr.`rate`, cr.`updated_at`, c.`code` " +
                    "FROM `currency_rate` cr " +
                    "JOIN `currency` c ON c.`id` = cr.`to_currency_id` " +
                    "WHERE cr.`from_currency_id` = $eurId " +
                    "ORDER BY c.`code`",
            ).use { c ->
                val found = mutableListOf<String>()
                while (c.moveToNext()) {
                    found.add(c.getString(2))
                    assertEquals(
                        "updatedAt must equal snapshot millis for code ${c.getString(2)}",
                        snapshotMillis,
                        c.getLong(1),
                    )
                }
                assertEquals(
                    "must have exactly 5 EUR-based rates",
                    expectedToCodes.sorted(),
                    found.sorted(),
                )
            }

        db.close()
    }

    @Test
    fun `migrate 5 to 6 EUR to USD rate matches snapshot value`() {
        helper.createDatabase(dbName, 5).apply {
            seedRequiredCurrencies()
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6)

        assertRate(db, "EUR", "USD", 1.146893)

        db.close()
    }

    @Test
    fun `migrate 5 to 6 EUR to RUB rate matches snapshot value`() {
        helper.createDatabase(dbName, 5).apply {
            seedRequiredCurrencies()
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6)

        assertRate(db, "EUR", "RUB", 84.181245)

        db.close()
    }

    @Test
    fun `migrate 5 to 6 EUR to RSD rate matches snapshot value`() {
        helper.createDatabase(dbName, 5).apply {
            seedRequiredCurrencies()
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6)

        assertRate(db, "EUR", "RSD", 117.390388)

        db.close()
    }

    @Test
    fun `migrate 5 to 6 EUR to KZT rate matches snapshot value`() {
        helper.createDatabase(dbName, 5).apply {
            seedRequiredCurrencies()
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6)

        assertRate(db, "EUR", "KZT", 559.417885)

        db.close()
    }

    @Test
    fun `migrate 5 to 6 EUR to AED rate matches snapshot value`() {
        helper.createDatabase(dbName, 5).apply {
            seedRequiredCurrencies()
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6)

        assertRate(db, "EUR", "AED", 4.211961)

        db.close()
    }

    @Test
    fun `migrate 5 to 6 is idempotent for KZT and AED currencies`() {
        helper.createDatabase(dbName, 5).apply {
            execSQL(
                "INSERT OR IGNORE INTO `currency` " +
                    "(`code`, `symbol`, `name`, `decimal_digits`, `is_active`, `sort_order`) " +
                    "VALUES ('KZT', '₸', 'Kazakhstani Tenge', 2, 1, 21)",
            )
            execSQL(
                "INSERT OR IGNORE INTO `currency` " +
                    "(`code`, `symbol`, `name`, `decimal_digits`, `is_active`, `sort_order`) " +
                    "VALUES ('AED', 'د.إ', 'UAE Dirham', 2, 1, 22)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6)

        db.query("SELECT COUNT(*) FROM `currency` WHERE `code` IN ('KZT', 'AED')").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("KZT and AED must appear exactly once each after idempotent migration", 2, c.getInt(0))
        }

        db.close()
    }

    @Test
    fun `migrate 5 to 6 does not create duplicate rates when run twice`() {
        helper.createDatabase(dbName, 5).apply {
            seedRequiredCurrencies()
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6)

        val eurId = db.queryCurrencyId("EUR")

        db.query("SELECT COUNT(*) FROM `currency_rate` WHERE `from_currency_id` = $eurId").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("must have exactly 5 EUR-based rates with no duplicates", 5, c.getInt(0))
        }

        db.close()
    }

    @Test
    fun `migrate 5 to 6 does not affect pre-existing currencies`() {
        helper.createDatabase(dbName, 5).apply {
            seedRequiredCurrencies()
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6)

        db.query("SELECT `code` FROM `currency` WHERE `code` = 'USD'").use { c ->
            assertTrue("pre-existing USD currency must survive migration", c.moveToFirst())
        }
        db.query("SELECT `code` FROM `currency` WHERE `code` = 'EUR'").use { c ->
            assertTrue("pre-existing EUR currency must survive migration", c.moveToFirst())
        }
        db.query("SELECT `code` FROM `currency` WHERE `code` = 'RSD'").use { c ->
            assertTrue("pre-existing RSD currency must survive migration", c.moveToFirst())
        }

        db.close()
    }

    private fun SupportSQLiteDatabase.seedRequiredCurrencies() {
        val currencies =
            listOf(
                Triple("USD", "$", "US Dollar"),
                Triple("EUR", "€", "Euro"),
                Triple("RUB", "₽", "Russian Ruble"),
                Triple("GBP", "£", "British Pound"),
                Triple("RSD", "RSD", "Serbian Dinar"),
            )
        currencies.forEachIndexed { index, (code, symbol, name) ->
            execSQL(
                "INSERT OR IGNORE INTO `currency` " +
                    "(`code`, `symbol`, `name`, `decimal_digits`, `is_active`, `sort_order`) " +
                    "VALUES ('$code', '$symbol', '$name', 2, 1, $index)",
            )
        }
    }

    private fun SupportSQLiteDatabase.queryCurrencyId(code: String): Long {
        query("SELECT `id` FROM `currency` WHERE `code` = '$code'").use { c ->
            assertTrue("$code must exist in currency table", c.moveToFirst())
            return c.getLong(0)
        }
    }

    private fun assertRate(
        db: SupportSQLiteDatabase,
        fromCode: String,
        toCode: String,
        expectedRate: Double,
    ) {
        val fromId = db.queryCurrencyId(fromCode)
        val toId = db.queryCurrencyId(toCode)
        db
            .query(
                "SELECT `rate` FROM `currency_rate` WHERE `from_currency_id` = $fromId AND `to_currency_id` = $toId",
            ).use { c ->
                assertTrue("$fromCode→$toCode rate must exist", c.moveToFirst())
                assertEquals(expectedRate, c.getDouble(0), 1e-6)
            }
    }
}
