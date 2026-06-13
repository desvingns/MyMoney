package com.kshavrin.mymoney.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kshavrin.mymoney.core.database.entity.AccountEntity
import com.kshavrin.mymoney.core.database.entity.CategoryEntity
import com.kshavrin.mymoney.core.database.entity.CurrencyEntity
import com.kshavrin.mymoney.core.database.repository.BackupRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal
import java.time.Instant

/**
 * Verifies the transfer-aware CSV export/import path in BackupRepositoryImpl:
 *   1. Export with transfers present does not throw and includes all row kinds.
 *   2. Round-trip: export → wipe DB → import restores the transfer entity faithfully.
 *   3. Legacy 9-column CSV (no to_account/to_amount) still imports expenses and income.
 */
@RunWith(AndroidJUnit4::class)
class BackupCsvTransferTest {

    private lateinit var db: MoneyDatabase
    private lateinit var context: Context
    private lateinit var repo: BackupRepositoryImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MoneyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = BackupRepositoryImpl(context, db, Dispatchers.IO)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // -------------------------------------------------------------------------
    // Shared seed helpers
    // -------------------------------------------------------------------------

    private suspend fun seedCurrency(): Long =
        db.currencyDao().upsert(
            CurrencyEntity(
                code = "RUB", symbol = "₽", name = "Russian Ruble",
                decimalDigits = 2, isActive = true, sortOrder = 0,
            ),
        )

    private suspend fun seedAccount(name: String, currencyId: Long, sortOrder: Int = 0): Long {
        val now = System.currentTimeMillis()
        return db.accountDao().upsert(
            AccountEntity(
                name = name, currencyId = currencyId, initialBalance = 0.0,
                type = "cash", colorHex = "#EF5350", iconKey = "ic_account_cash",
                isDefault = sortOrder == 0, sortOrder = sortOrder,
                createdAt = now, updatedAt = now, isArchived = false,
            ),
        )
    }

    private suspend fun seedCategory(name: String, kind: String, sortOrder: Int = 0): Long {
        val now = System.currentTimeMillis()
        return db.categoryDao().upsert(
            CategoryEntity(
                name = name, kind = kind, iconKey = "ic_cat_other",
                colorHex = "#9CCC65", sortOrder = sortOrder,
                isDefault = false, isArchived = false, createdAt = now,
            ),
        )
    }

    // -------------------------------------------------------------------------
    // Acceptance scenario 1: export with transfers present does NOT throw;
    // file contains expense, income, AND transfer rows.
    // -------------------------------------------------------------------------

    @Test
    fun export_with_transfers_does_not_throw_and_file_contains_all_row_kinds() = runTest {
        val currencyId = seedCurrency()
        val cashId = seedAccount("Наличные", currencyId, sortOrder = 0)
        val cardId = seedAccount("Карта", currencyId, sortOrder = 1)
        val groceriesId = seedCategory("Продукты", "expense", sortOrder = 0)
        val salaryId = seedCategory("Зарплата", "income", sortOrder = 1)

        val now = Instant.now()
        // Insert expense
        db.transactionDao().upsert(
            com.kshavrin.mymoney.core.database.entity.TransactionEntity(
                id = 0L, kind = "expense", amount = 100.0, currencyId = currencyId,
                accountId = cashId, categoryId = groceriesId, note = null,
                occurredAt = now.minusSeconds(300).toEpochMilli(),
                createdAt = now.toEpochMilli(), updatedAt = now.toEpochMilli(),
                isDeleted = false, toAccountId = null, toAmount = null, exchangeRate = null,
            ),
        )
        // Insert income
        db.transactionDao().upsert(
            com.kshavrin.mymoney.core.database.entity.TransactionEntity(
                id = 0L, kind = "income", amount = 50000.0, currencyId = currencyId,
                accountId = cashId, categoryId = salaryId, note = null,
                occurredAt = now.minusSeconds(200).toEpochMilli(),
                createdAt = now.toEpochMilli(), updatedAt = now.toEpochMilli(),
                isDeleted = false, toAccountId = null, toAmount = null, exchangeRate = null,
            ),
        )
        // Insert transfer
        db.transactionDao().upsert(
            com.kshavrin.mymoney.core.database.entity.TransactionEntity(
                id = 0L, kind = "transfer", amount = 500.0, currencyId = currencyId,
                accountId = cashId, categoryId = null, note = null,
                occurredAt = now.minusSeconds(100).toEpochMilli(),
                createdAt = now.toEpochMilli(), updatedAt = now.toEpochMilli(),
                isDeleted = false, toAccountId = cardId, toAmount = 500.0, exchangeRate = null,
            ),
        )

        val csvFile = File(context.cacheDir, "export_transfer_test.csv")
        try {
            val result = repo.exportTransactionsCsv("file://${csvFile.absolutePath}")
            assertTrue(
                "Export must succeed; error: ${result.exceptionOrNull()?.message}",
                result.isSuccess,
            )
            assertTrue("CSV file must be created", csvFile.exists())

            val content = csvFile.readText(Charsets.UTF_8)
            val lines = content.lines().filter { it.isNotBlank() }

            // header + 3 data rows
            assertTrue("Expected at least 4 lines (header + 3 rows), got ${lines.size}", lines.size >= 4)

            val header = lines[0]
            assertTrue("Header must include to_account column", header.contains("to_account"))
            assertTrue("Header must include to_amount column", header.contains("to_amount"))

            val dataLines = lines.drop(1)
            assertTrue(
                "Must contain an expense row",
                dataLines.any { it.contains(",expense,") },
            )
            assertTrue(
                "Must contain an income row",
                dataLines.any { it.contains(",income,") },
            )
            assertTrue(
                "Must contain a transfer row",
                dataLines.any { it.contains(",transfer,") },
            )

            val transferLine = dataLines.first { it.contains(",transfer,") }
            assertTrue(
                "Transfer row must carry the to_account name",
                transferLine.contains("Карта"),
            )
            assertTrue(
                "Transfer row must carry a non-empty to_amount",
                transferLine.split(",").last().isNotEmpty(),
            )
        } finally {
            csvFile.delete()
        }
    }

    // -------------------------------------------------------------------------
    // Acceptance scenario 2: round-trip — export a transfer, import into a clean
    // DB, the transfer is restored with the same accounts and amounts.
    // -------------------------------------------------------------------------

    @Test
    fun round_trip_export_then_import_restores_transfer_with_correct_accounts_and_amounts() = runTest {
        val currencyId = seedCurrency()
        val cashId = seedAccount("Наличные", currencyId, sortOrder = 0)
        val cardId = seedAccount("Карта", currencyId, sortOrder = 1)

        val occurredAt = Instant.parse("2026-06-01T12:00:00Z")
        val createdAt = Instant.parse("2026-06-01T12:00:00Z")
        db.transactionDao().upsert(
            com.kshavrin.mymoney.core.database.entity.TransactionEntity(
                id = 1L, kind = "transfer", amount = 500.0, currencyId = currencyId,
                accountId = cashId, categoryId = null, note = null,
                occurredAt = occurredAt.toEpochMilli(),
                createdAt = createdAt.toEpochMilli(),
                updatedAt = createdAt.toEpochMilli(),
                isDeleted = false, toAccountId = cardId, toAmount = 500.0, exchangeRate = null,
            ),
        )

        val csvFile = File(context.cacheDir, "roundtrip_transfer_test.csv")
        try {
            val exportResult = repo.exportTransactionsCsv("file://${csvFile.absolutePath}")
            assertTrue(
                "Export must succeed; error: ${exportResult.exceptionOrNull()?.message}",
                exportResult.isSuccess,
            )

            // Purge the seeded transfer so importMyMoneyCsv does not see a duplicate id=1.
            val nowMs = Instant.now().toEpochMilli()
            db.transactionDao().softDelete(1L, nowMs)
            db.transactionDao().pruneDeleted(nowMs + 1_000L)

            val txsBefore = db.transactionDao().observeAll().first()
            assertTrue("DB must have no transactions before import", txsBefore.isEmpty())

            val importResult = repo.importTransactionsCsv("file://${csvFile.absolutePath}")
            assertTrue(
                "Import must succeed; error: ${importResult.exceptionOrNull()?.message}",
                importResult.isSuccess,
            )

            val transactions = db.transactionDao().observeAll().first()
            assertEquals("One transfer row must be restored", 1, transactions.size)

            val restored = transactions.single()
            assertEquals("kind must be transfer", "transfer", restored.kind.lowercase())
            assertEquals("from-account must match", cashId, restored.accountId)
            assertEquals("to-account must match", cardId, restored.toAccountId)
            assertEquals(
                "from-amount must be 500",
                0,
                BigDecimal("500").compareTo(BigDecimal.valueOf(restored.amount)),
            )
            assertNotNull("to_amount must not be null", restored.toAmount)
            assertEquals(
                "to-amount must be 500",
                0,
                BigDecimal("500").compareTo(BigDecimal.valueOf(restored.toAmount!!)),
            )
            assertNull("transfer must have no category", restored.categoryId)
        } finally {
            csvFile.delete()
        }
    }

    // -------------------------------------------------------------------------
    // Acceptance scenario 3: legacy 9-column CSV (no to_account / to_amount
    // columns, exported by an older version) still imports expenses and income.
    // -------------------------------------------------------------------------

    @Test
    fun legacy_9_column_csv_imports_expense_and_income_correctly() = runTest {
        val currencyId = seedCurrency()
        seedAccount("Наличные", currencyId, sortOrder = 0)
        seedCategory("Продукты", "expense", sortOrder = 0)
        seedCategory("Зарплата", "income", sortOrder = 1)

        val occurredAt = "2026-05-01T10:00:00Z"
        val createdAt = "2026-05-01T10:00:00Z"

        // 9-column format (legacy — no to_account, no to_amount)
        val legacyCsv = buildString {
            append("id,kind,amount,currency,account,category,note,occurredAt,createdAt\r\n")
            append("10,expense,100,RUB,Наличные,Продукты,,${occurredAt},${createdAt}\r\n")
            append("11,income,50000,RUB,Наличные,Зарплата,,${occurredAt},${createdAt}\r\n")
        }

        val csvFile = File(context.cacheDir, "legacy_9col_test.csv")
        try {
            csvFile.writeText(legacyCsv, Charsets.UTF_8)

            val result = repo.importTransactionsCsv("file://${csvFile.absolutePath}")
            assertTrue(
                "Legacy 9-column import must succeed; error: ${result.exceptionOrNull()?.message}",
                result.isSuccess,
            )

            val transactions = db.transactionDao().observeAll().first()
            assertEquals("Must import exactly 2 transactions", 2, transactions.size)

            val expense = transactions.single { it.kind.lowercase() == "expense" }
            assertEquals("Expense amount must be 100", 100.0, expense.amount, 0.001)
            assertNull("Expense must have no to_account", expense.toAccountId)
            assertNull("Expense must have no to_amount", expense.toAmount)

            val income = transactions.single { it.kind.lowercase() == "income" }
            assertEquals("Income amount must be 50000", 50000.0, income.amount, 0.001)
            assertNull("Income must have no to_account", income.toAccountId)
            assertNull("Income must have no to_amount", income.toAmount)
        } finally {
            csvFile.delete()
        }
    }

    // -------------------------------------------------------------------------
    // Regression: a single transfer row in a 9-column file (no to_account cols)
    // must be rejected with a clear error rather than silently imported.
    // -------------------------------------------------------------------------

    @Test
    fun transfer_row_in_legacy_9_column_file_is_rejected_with_an_error() = runTest {
        val currencyId = seedCurrency()
        seedAccount("Наличные", currencyId, sortOrder = 0)

        val occurredAt = "2026-05-01T10:00:00Z"

        // A 9-column file containing a transfer row — transfer needs 11 columns; this must fail.
        val malformedCsv = buildString {
            append("id,kind,amount,currency,account,category,note,occurredAt,createdAt\r\n")
            append("20,transfer,500,RUB,Наличные,,,$occurredAt,$occurredAt\r\n")
        }

        val csvFile = File(context.cacheDir, "transfer_9col_rejection_test.csv")
        try {
            csvFile.writeText(malformedCsv, Charsets.UTF_8)

            val result = repo.importTransactionsCsv("file://${csvFile.absolutePath}")
            assertTrue(
                "Import must fail when transfer row lacks to_account/to_amount columns",
                result.isFailure,
            )
            val error = result.exceptionOrNull()
            assertNotNull("Failure must carry an exception", error)
            assertTrue(
                "Error message must mention the missing transfer columns",
                error!!.message?.contains("to_account") == true ||
                    error.message?.contains("transfer") == true,
            )
        } finally {
            csvFile.delete()
        }
    }
}
