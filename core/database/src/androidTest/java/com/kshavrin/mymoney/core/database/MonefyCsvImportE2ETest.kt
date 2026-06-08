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
import org.junit.Assert.assertNotEquals
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

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MoneyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun monefy_csv_import_inserts_transactions_and_auto_creates_accounts_and_categories() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Staged via: adb push <Monefy CSV> /data/local/tmp/monefy.csv
        val staged = File("/data/local/tmp/monefy.csv")
        check(staged.exists()) {
            "CSV not staged. Run: adb push <Monefy CSV> /data/local/tmp/monefy.csv"
        }
        val csvFile = File(context.cacheDir, "monefy_e2e_test.csv")
        staged.copyTo(csvFile, overwrite = true)

        // Seed the single currency referenced in the Monefy export (RUB)
        db.currencyDao().upsert(
            CurrencyEntity(
                code = "RUB", symbol = "₽", name = "Russian Ruble",
                decimalDigits = 2, isActive = true, sortOrder = 0,
            ),
        )

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

    @Test
    fun monefy_csv_import_merges_into_seeded_RU_entities_without_duplicating() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val now = System.currentTimeMillis()

        val currencyId = db.currencyDao().upsert(
            CurrencyEntity(
                code = "RUB", symbol = "₽", name = "Russian Ruble",
                decimalDigits = 2, isActive = true, sortOrder = 0,
            ),
        )

        // Seed RU built-ins, deliberately with extra/inner whitespace and an NFC-decomposable
        // form so only normalize-then-compare can match the CSV names.
        val cashId = db.accountDao().upsert(
            AccountEntity(
                name = " Наличные ", currencyId = currencyId, initialBalance = 0.0,
                type = "cash", colorHex = "#EF5350", iconKey = "ic_account_cash",
                isDefault = true, sortOrder = 0, createdAt = now, updatedAt = now,
                isArchived = false,
            ),
        )
        val salaryId = db.categoryDao().upsert(
            CategoryEntity(
                name = "Зарплата", kind = "income", iconKey = "ic_cat_other",
                colorHex = "#9CCC65", sortOrder = 0, isDefault = true, isArchived = false,
                createdAt = now,
            ),
        )
        val groceriesId = db.categoryDao().upsert(
            CategoryEntity(
                name = "Продукты", kind = "expense", iconKey = "ic_cat_other",
                colorHex = "#EF5350", sortOrder = 1, isDefault = true, isArchived = false,
                createdAt = now,
            ),
        )

        val accountsBefore = db.accountDao().observeActive().first().size
        val categoriesBefore = db.categoryDao().observeAll().first().size

        val header =
            "date,account,category,amount,currency,converted amount,currency,description"
        val csv = buildString {
            append(header).append("\r\n")
            // "наличные" lowercase vs seeded " Наличные " → normalized account match.
            append("01/01/2020,наличные,Зарплата,50000,RUB,50000,RUB,\r\n")
            append("02/01/2020,Наличные,Продукты,-1000,RUB,-1000,RUB,хлеб\r\n")
            // A category not in the seed → must create exactly one new category.
            append("03/01/2020,Наличные,My custom,-200,RUB,-200,RUB,\r\n")
        }
        val csvFile = File(context.cacheDir, "monefy_dedup_test.csv")
        csvFile.writeText(csv, Charsets.UTF_8)

        val repo = BackupRepositoryImpl(context, db, Dispatchers.IO)
        val result = repo.importTransactionsCsv("file://${csvFile.absolutePath}")
        assertTrue(
            "Import returned failure: ${result.exceptionOrNull()?.message}",
            result.isSuccess,
        )

        val accountsAfter = db.accountDao().observeActive().first()
        assertEquals(
            "Account merged into seed, no duplicate expected",
            accountsBefore,
            accountsAfter.size,
        )

        val categoriesAfter = db.categoryDao().observeAll().first()
        assertEquals(
            "Only the genuinely-new 'My custom' category should be added",
            categoriesBefore + 1,
            categoriesAfter.size,
        )

        val transactions = db.transactionDao().observeAll().first()
        assertEquals(3, transactions.size)
        assertTrue(
            "Every imported transaction must reference the seeded account",
            transactions.all { it.accountId == cashId },
        )
        val salaryTx = transactions.single { it.categoryId == salaryId }
        assertEquals("income", salaryTx.kind.lowercase())
        assertTrue(
            "Seeded 'Продукты' must be reused",
            transactions.any { it.categoryId == groceriesId },
        )
        val customCategory = categoriesAfter.single { it.id !in setOf(salaryId, groceriesId) }
        assertEquals("My custom", customCategory.name)

        csvFile.delete()
    }

    @Test
    fun monefy_csv_import_second_import_adds_transactions_but_does_not_create_duplicate_entities() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val now = System.currentTimeMillis()

        val currencyId = db.currencyDao().upsert(
            CurrencyEntity(
                code = "RUB", symbol = "₽", name = "Russian Ruble",
                decimalDigits = 2, isActive = true, sortOrder = 0,
            ),
        )

        db.accountDao().upsert(
            AccountEntity(
                name = "Наличные", currencyId = currencyId, initialBalance = 0.0,
                type = "cash", colorHex = "#EF5350", iconKey = "ic_account_cash",
                isDefault = true, sortOrder = 0, createdAt = now, updatedAt = now,
                isArchived = false,
            ),
        )
        db.categoryDao().upsert(
            CategoryEntity(
                name = "Продукты", kind = "expense", iconKey = "ic_cat_other",
                colorHex = "#EF5350", sortOrder = 0, isDefault = true, isArchived = false,
                createdAt = now,
            ),
        )

        val header =
            "date,account,category,amount,currency,converted amount,currency,description"
        val csv = buildString {
            append(header).append("\r\n")
            append("01/01/2020,Наличные,Продукты,-500,RUB,-500,RUB,first\r\n")
        }

        val csvFile = File(context.cacheDir, "monefy_repeat_import_test.csv")
        csvFile.writeText(csv, Charsets.UTF_8)

        val repo = BackupRepositoryImpl(context, db, Dispatchers.IO)

        val firstResult = repo.importTransactionsCsv("file://${csvFile.absolutePath}")
        assertTrue("First import failed: ${firstResult.exceptionOrNull()?.message}", firstResult.isSuccess)

        val accountsAfterFirst = db.accountDao().observeActive().first().size
        val categoriesAfterFirst = db.categoryDao().observeAll().first().size
        val txAfterFirst = db.transactionDao().observeAll().first().size

        val secondResult = repo.importTransactionsCsv("file://${csvFile.absolutePath}")
        assertTrue("Second import failed: ${secondResult.exceptionOrNull()?.message}", secondResult.isSuccess)

        assertEquals(
            "Re-import must not create new account entities",
            accountsAfterFirst,
            db.accountDao().observeActive().first().size,
        )
        assertEquals(
            "Re-import must not create new category entities",
            categoriesAfterFirst,
            db.categoryDao().observeAll().first().size,
        )
        assertEquals(
            "Re-import is additive: transactions count must double",
            txAfterFirst * 2,
            db.transactionDao().observeAll().first().size,
        )

        csvFile.delete()
    }

    @Test
    fun monefy_csv_import_category_kind_aware_same_name_expense_and_income_create_separate_entries() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val now = System.currentTimeMillis()

        val currencyId = db.currencyDao().upsert(
            CurrencyEntity(
                code = "RUB", symbol = "₽", name = "Russian Ruble",
                decimalDigits = 2, isActive = true, sortOrder = 0,
            ),
        )

        db.accountDao().upsert(
            AccountEntity(
                name = "Наличные", currencyId = currencyId, initialBalance = 0.0,
                type = "cash", colorHex = "#EF5350", iconKey = "ic_account_cash",
                isDefault = true, sortOrder = 0, createdAt = now, updatedAt = now,
                isArchived = false,
            ),
        )

        // Seed "Прочее" as expense only — the same name as income must create a separate row.
        val expenseId = db.categoryDao().upsert(
            CategoryEntity(
                name = "Прочее", kind = "expense", iconKey = "ic_cat_other",
                colorHex = "#EF5350", sortOrder = 0, isDefault = true, isArchived = false,
                createdAt = now,
            ),
        )

        val categoriesBefore = db.categoryDao().observeAll().first().size

        val header =
            "date,account,category,amount,currency,converted amount,currency,description"
        val csv = buildString {
            append(header).append("\r\n")
            // expense row → must reuse seeded expenseId
            append("01/01/2020,Наличные,Прочее,-100,RUB,-100,RUB,\r\n")
            // income row with the same category name → different kind, must create a NEW category
            append("02/01/2020,Наличные,Прочее,200,RUB,200,RUB,\r\n")
        }

        val csvFile = File(context.cacheDir, "monefy_kind_aware_test.csv")
        csvFile.writeText(csv, Charsets.UTF_8)

        val repo = BackupRepositoryImpl(context, db, Dispatchers.IO)
        val result = repo.importTransactionsCsv("file://${csvFile.absolutePath}")
        assertTrue("Import failed: ${result.exceptionOrNull()?.message}", result.isSuccess)

        val categoriesAfter = db.categoryDao().observeAll().first()
        assertEquals(
            "Same name with different kind must create exactly one new category",
            categoriesBefore + 1,
            categoriesAfter.size,
        )

        val transactions = db.transactionDao().observeAll().first()
        assertEquals(2, transactions.size)

        val expenseTx = transactions.single { it.kind.lowercase() == "expense" }
        assertEquals(
            "Expense transaction must reference seeded expense category",
            expenseId,
            expenseTx.categoryId,
        )

        val incomeTx = transactions.single { it.kind.lowercase() == "income" }
        assertNotEquals(
            "Income transaction must reference the newly-created income category, not the expense one",
            expenseId,
            incomeTx.categoryId,
        )

        csvFile.delete()
    }
}
