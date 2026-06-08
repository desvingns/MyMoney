package com.kshavrin.mymoney.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kshavrin.mymoney.core.database.entity.CurrencyEntity
import com.kshavrin.mymoney.core.database.repository.BackupRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * E2E instrumented smoke-test for the Monefy CSV import path.
 *
 * Prerequisites (set up once per device session):
 *   adb push <Monefy.Data.*.csv> /data/local/tmp/monefy.csv
 *
 * The test copies the file from /data/local/tmp/ into the app's cache dir,
 * seeds the required RUB currency row, calls importTransactionsCsv, and
 * asserts that transactions + auto-created accounts/categories are present.
 */
@RunWith(AndroidJUnit4::class)
class MonefyCsvImportE2ETest {

    private lateinit var db: MoneyDatabase
    private lateinit var csvFile: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MoneyDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // Staged via: adb push <Monefy CSV> /data/local/tmp/monefy.csv
        val staged = File("/data/local/tmp/monefy.csv")
        check(staged.exists()) {
            "CSV not staged. Run: adb push <Monefy CSV> /data/local/tmp/monefy.csv"
        }
        csvFile = File(context.cacheDir, "monefy_e2e_test.csv")
        staged.copyTo(csvFile, overwrite = true)
    }

    @After
    fun tearDown() {
        db.close()
        csvFile.delete()
    }

    @Test
    fun monefy_csv_import_inserts_transactions_and_auto_creates_accounts_and_categories() = runTest {
        // Seed the single currency referenced in the Monefy export (RUB)
        db.currencyDao().upsert(
            CurrencyEntity(
                code = "RUB", symbol = "₽", name = "Russian Ruble",
                decimalDigits = 2, isActive = true, sortOrder = 0,
            ),
        )

        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = BackupRepositoryImpl(context, db, Dispatchers.IO)

        val result = repo.importTransactionsCsv("file://${csvFile.absolutePath}")

        assertTrue(
            "Import returned failure: ${result.exceptionOrNull()?.message}",
            result.isSuccess,
        )
        val focus = result.getOrThrow()
        assertNotNull(focus)
        assertTrue(focus!!.occurredAtEpochMs > 0L)

        val transactions = db.transactionDao().observeAll().first()
        assertTrue(
            "Expected > 0 imported transactions, got ${transactions.size}",
            transactions.isNotEmpty(),
        )

        val accounts = db.accountDao().observeActive().first()
        assertTrue(
            "Expected at least one auto-created account, got ${accounts.size}",
            accounts.isNotEmpty(),
        )

        val categories = db.categoryDao().observeAll().first()
        assertTrue(
            "Expected at least one auto-created category, got ${categories.size}",
            categories.isNotEmpty(),
        )
    }
}
