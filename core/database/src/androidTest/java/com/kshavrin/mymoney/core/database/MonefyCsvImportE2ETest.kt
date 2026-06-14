package com.kshavrin.mymoney.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kshavrin.mymoney.core.database.entity.AccountEntity
import com.kshavrin.mymoney.core.database.entity.CategoryEntity
import com.kshavrin.mymoney.core.database.entity.CurrencyEntity
import com.kshavrin.mymoney.core.database.entity.TransactionEntity
import com.kshavrin.mymoney.core.database.repository.BackupRepositoryImpl
import com.kshavrin.mymoney.core.domain.csv.ImportCategoryStrategy
import com.kshavrin.mymoney.core.domain.csv.ImportDataStrategy
import com.kshavrin.mymoney.core.domain.csv.ImportPlan
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
        db =
            Room
                .inMemoryDatabaseBuilder(context, MoneyDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun monefy_csv_import_inserts_transactions_and_auto_creates_accounts_and_categories() =
        runTest {
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
                    code = "RUB",
                    symbol = "₽",
                    name = "Russian Ruble",
                    decimalDigits = 2,
                    isActive = true,
                    sortOrder = 0,
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
    fun monefy_csv_import_merges_into_seeded_RU_entities_without_duplicating() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val now = System.currentTimeMillis()

            val currencyId =
                db.currencyDao().upsert(
                    CurrencyEntity(
                        code = "RUB",
                        symbol = "₽",
                        name = "Russian Ruble",
                        decimalDigits = 2,
                        isActive = true,
                        sortOrder = 0,
                    ),
                )

            // Seed RU built-ins, deliberately with extra/inner whitespace and an NFC-decomposable
            // form so only normalize-then-compare can match the CSV names.
            val cashId =
                db.accountDao().upsert(
                    AccountEntity(
                        name = " Наличные ",
                        currencyId = currencyId,
                        initialBalance = 0.0,
                        type = "cash",
                        colorHex = "#EF5350",
                        iconKey = "ic_account_cash",
                        isDefault = true,
                        sortOrder = 0,
                        createdAt = now,
                        updatedAt = now,
                        isArchived = false,
                    ),
                )
            val salaryId =
                db.categoryDao().upsert(
                    CategoryEntity(
                        name = "Зарплата",
                        kind = "income",
                        iconKey = "ic_cat_other",
                        colorHex = "#9CCC65",
                        sortOrder = 0,
                        isDefault = true,
                        isArchived = false,
                        createdAt = now,
                    ),
                )
            val groceriesId =
                db.categoryDao().upsert(
                    CategoryEntity(
                        name = "Продукты",
                        kind = "expense",
                        iconKey = "ic_cat_other",
                        colorHex = "#EF5350",
                        sortOrder = 1,
                        isDefault = true,
                        isArchived = false,
                        createdAt = now,
                    ),
                )

            val accountsBefore =
                db
                    .accountDao()
                    .observeActive()
                    .first()
                    .size
            val categoriesBefore =
                db
                    .categoryDao()
                    .observeAll()
                    .first()
                    .size

            val header =
                "date,account,category,amount,currency,converted amount,currency,description"
            val csv =
                buildString {
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
    fun monefy_csv_import_second_import_adds_transactions_but_does_not_create_duplicate_entities() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val now = System.currentTimeMillis()

            val currencyId =
                db.currencyDao().upsert(
                    CurrencyEntity(
                        code = "RUB",
                        symbol = "₽",
                        name = "Russian Ruble",
                        decimalDigits = 2,
                        isActive = true,
                        sortOrder = 0,
                    ),
                )

            db.accountDao().upsert(
                AccountEntity(
                    name = "Наличные",
                    currencyId = currencyId,
                    initialBalance = 0.0,
                    type = "cash",
                    colorHex = "#EF5350",
                    iconKey = "ic_account_cash",
                    isDefault = true,
                    sortOrder = 0,
                    createdAt = now,
                    updatedAt = now,
                    isArchived = false,
                ),
            )
            db.categoryDao().upsert(
                CategoryEntity(
                    name = "Продукты",
                    kind = "expense",
                    iconKey = "ic_cat_other",
                    colorHex = "#EF5350",
                    sortOrder = 0,
                    isDefault = true,
                    isArchived = false,
                    createdAt = now,
                ),
            )

            val header =
                "date,account,category,amount,currency,converted amount,currency,description"
            val csv =
                buildString {
                    append(header).append("\r\n")
                    append("01/01/2020,Наличные,Продукты,-500,RUB,-500,RUB,first\r\n")
                }

            val csvFile = File(context.cacheDir, "monefy_repeat_import_test.csv")
            csvFile.writeText(csv, Charsets.UTF_8)

            val repo = BackupRepositoryImpl(context, db, Dispatchers.IO)

            val firstResult = repo.importTransactionsCsv("file://${csvFile.absolutePath}")
            assertTrue("First import failed: ${firstResult.exceptionOrNull()?.message}", firstResult.isSuccess)

            val accountsAfterFirst =
                db
                    .accountDao()
                    .observeActive()
                    .first()
                    .size
            val categoriesAfterFirst =
                db
                    .categoryDao()
                    .observeAll()
                    .first()
                    .size
            val txAfterFirst =
                db
                    .transactionDao()
                    .observeAll()
                    .first()
                    .size

            val secondResult = repo.importTransactionsCsv("file://${csvFile.absolutePath}")
            assertTrue("Second import failed: ${secondResult.exceptionOrNull()?.message}", secondResult.isSuccess)

            assertEquals(
                "Re-import must not create new account entities",
                accountsAfterFirst,
                db
                    .accountDao()
                    .observeActive()
                    .first()
                    .size,
            )
            assertEquals(
                "Re-import must not create new category entities",
                categoriesAfterFirst,
                db
                    .categoryDao()
                    .observeAll()
                    .first()
                    .size,
            )
            assertEquals(
                "Re-import is additive: transactions count must double",
                txAfterFirst * 2,
                db
                    .transactionDao()
                    .observeAll()
                    .first()
                    .size,
            )

            csvFile.delete()
        }

    // -----------------------------------------------------------------------
    // resolveAccountId: name + currency keying (bugfix coverage)
    // -----------------------------------------------------------------------

    @Test
    fun `currency conflict creates a separate suffixed account and does not mix balances`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val now = System.currentTimeMillis()

            val rubId =
                db.currencyDao().upsert(
                    CurrencyEntity(
                        code = "RUB",
                        symbol = "₽",
                        name = "Russian Ruble",
                        decimalDigits = 2,
                        isActive = true,
                        sortOrder = 0,
                    ),
                )
            val usdId =
                db.currencyDao().upsert(
                    CurrencyEntity(
                        code = "USD",
                        symbol = "$",
                        name = "US Dollar",
                        decimalDigits = 2,
                        isActive = true,
                        sortOrder = 1,
                    ),
                )

            db.accountDao().upsert(
                AccountEntity(
                    name = "Наличные",
                    currencyId = rubId,
                    initialBalance = 0.0,
                    type = "cash",
                    colorHex = "#EF5350",
                    iconKey = "ic_account_cash",
                    isDefault = true,
                    sortOrder = 0,
                    createdAt = now,
                    updatedAt = now,
                    isArchived = false,
                ),
            )
            db.categoryDao().upsert(
                CategoryEntity(
                    name = "Зарплата",
                    kind = "income",
                    iconKey = "ic_cat_other",
                    colorHex = "#9CCC65",
                    sortOrder = 0,
                    isDefault = true,
                    isArchived = false,
                    createdAt = now,
                ),
            )

            val header = "date,account,category,amount,currency,converted amount,currency,description"
            val csv =
                buildString {
                    append(header).append("\r\n")
                    append("01/01/2020,Наличные,Зарплата,100,USD,100,USD,\r\n")
                    append("02/01/2020,Наличные,Зарплата,200,USD,200,USD,\r\n")
                }
            val csvFile = File(context.cacheDir, "monefy_currency_conflict_test.csv")
            csvFile.writeText(csv, Charsets.UTF_8)

            val repo = BackupRepositoryImpl(context, db, Dispatchers.IO)
            val result = repo.importTransactionsCsv("file://${csvFile.absolutePath}")
            assertTrue(
                "Import returned failure: ${result.exceptionOrNull()?.message}",
                result.isSuccess,
            )

            val accounts = db.accountDao().observeActive().first()
            assertEquals(
                "Expected exactly two accounts: Наличные/RUB and Наличные (USD)/USD",
                2,
                accounts.size,
            )

            val rubAccount = accounts.single { it.currencyId == rubId }
            assertEquals("Наличные", rubAccount.name)

            val usdAccount = accounts.single { it.currencyId == usdId }
            assertEquals("Наличные (USD)", usdAccount.name)

            val transactions = db.transactionDao().observeAll().first()
            assertEquals(2, transactions.size)
            assertTrue(
                "All USD-rows must reference the suffixed account, not the RUB account",
                transactions.all { it.accountId == usdAccount.id },
            )
            assertTrue(
                "No transaction must reference the RUB account",
                transactions.none { it.accountId == rubAccount.id },
            )

            csvFile.delete()
        }

    @Test
    fun `name and currency match reuses the existing account without creating a duplicate`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val now = System.currentTimeMillis()

            val rubId =
                db.currencyDao().upsert(
                    CurrencyEntity(
                        code = "RUB",
                        symbol = "₽",
                        name = "Russian Ruble",
                        decimalDigits = 2,
                        isActive = true,
                        sortOrder = 0,
                    ),
                )

            val seededAccountId =
                db.accountDao().upsert(
                    AccountEntity(
                        name = "Карта",
                        currencyId = rubId,
                        initialBalance = 0.0,
                        type = "card",
                        colorHex = "#5C6BC0",
                        iconKey = "ic_account_cash",
                        isDefault = false,
                        sortOrder = 0,
                        createdAt = now,
                        updatedAt = now,
                        isArchived = false,
                    ),
                )
            db.categoryDao().upsert(
                CategoryEntity(
                    name = "Продукты",
                    kind = "expense",
                    iconKey = "ic_cat_other",
                    colorHex = "#EF5350",
                    sortOrder = 0,
                    isDefault = true,
                    isArchived = false,
                    createdAt = now,
                ),
            )

            val accountsBefore =
                db
                    .accountDao()
                    .observeActive()
                    .first()
                    .size

            val header = "date,account,category,amount,currency,converted amount,currency,description"
            val csv =
                buildString {
                    append(header).append("\r\n")
                    append("01/01/2020,Карта,Продукты,-500,RUB,-500,RUB,\r\n")
                    append("02/01/2020,Карта,Продукты,-300,RUB,-300,RUB,\r\n")
                }
            val csvFile = File(context.cacheDir, "monefy_name_currency_match_test.csv")
            csvFile.writeText(csv, Charsets.UTF_8)

            val repo = BackupRepositoryImpl(context, db, Dispatchers.IO)
            val result = repo.importTransactionsCsv("file://${csvFile.absolutePath}")
            assertTrue(
                "Import returned failure: ${result.exceptionOrNull()?.message}",
                result.isSuccess,
            )

            val accountsAfter = db.accountDao().observeActive().first()
            assertEquals(
                "No new account should be created when name+currency match",
                accountsBefore,
                accountsAfter.size,
            )

            val transactions = db.transactionDao().observeAll().first()
            assertEquals(2, transactions.size)
            assertTrue(
                "All transactions must reference the pre-existing Карта/RUB account",
                transactions.all { it.accountId == seededAccountId },
            )

            csvFile.delete()
        }

    @Test
    fun `multiple rows for the same new name and currency create the suffixed account exactly once`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val now = System.currentTimeMillis()

            val rubId =
                db.currencyDao().upsert(
                    CurrencyEntity(
                        code = "RUB",
                        symbol = "₽",
                        name = "Russian Ruble",
                        decimalDigits = 2,
                        isActive = true,
                        sortOrder = 0,
                    ),
                )
            val usdId =
                db.currencyDao().upsert(
                    CurrencyEntity(
                        code = "USD",
                        symbol = "$",
                        name = "US Dollar",
                        decimalDigits = 2,
                        isActive = true,
                        sortOrder = 1,
                    ),
                )

            db.accountDao().upsert(
                AccountEntity(
                    name = "Наличные",
                    currencyId = rubId,
                    initialBalance = 0.0,
                    type = "cash",
                    colorHex = "#EF5350",
                    iconKey = "ic_account_cash",
                    isDefault = true,
                    sortOrder = 0,
                    createdAt = now,
                    updatedAt = now,
                    isArchived = false,
                ),
            )
            db.categoryDao().upsert(
                CategoryEntity(
                    name = "Зарплата",
                    kind = "income",
                    iconKey = "ic_cat_other",
                    colorHex = "#9CCC65",
                    sortOrder = 0,
                    isDefault = true,
                    isArchived = false,
                    createdAt = now,
                ),
            )

            val header = "date,account,category,amount,currency,converted amount,currency,description"
            val csv =
                buildString {
                    append(header).append("\r\n")
                    append("01/01/2020,Наличные,Зарплата,100,USD,100,USD,\r\n")
                    append("02/01/2020,Наличные,Зарплата,200,USD,200,USD,\r\n")
                    append("03/01/2020,Наличные,Зарплата,300,USD,300,USD,\r\n")
                }
            val csvFile = File(context.cacheDir, "monefy_single_new_account_test.csv")
            csvFile.writeText(csv, Charsets.UTF_8)

            val repo = BackupRepositoryImpl(context, db, Dispatchers.IO)
            val result = repo.importTransactionsCsv("file://${csvFile.absolutePath}")
            assertTrue(
                "Import returned failure: ${result.exceptionOrNull()?.message}",
                result.isSuccess,
            )

            val accounts = db.accountDao().observeActive().first()
            assertEquals(
                "Exactly two accounts expected: Наличные/RUB and Наличные (USD)/USD",
                2,
                accounts.size,
            )

            val usdAccounts = accounts.filter { it.currencyId == usdId }
            assertEquals(
                "Наличные (USD) must be created exactly once, not once per row",
                1,
                usdAccounts.size,
            )
            assertEquals("Наличные (USD)", usdAccounts.single().name)

            val transactions = db.transactionDao().observeAll().first()
            assertEquals(3, transactions.size)
            val usdAccountId = usdAccounts.single().id
            assertTrue(
                "All three USD transactions must share the single suffixed account",
                transactions.all { it.accountId == usdAccountId },
            )

            csvFile.delete()
        }

    @Test
    fun `suffixed account name itself normalizes and matches on a subsequent import`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val now = System.currentTimeMillis()

            val rubId =
                db.currencyDao().upsert(
                    CurrencyEntity(
                        code = "RUB",
                        symbol = "₽",
                        name = "Russian Ruble",
                        decimalDigits = 2,
                        isActive = true,
                        sortOrder = 0,
                    ),
                )
            val usdId =
                db.currencyDao().upsert(
                    CurrencyEntity(
                        code = "USD",
                        symbol = "$",
                        name = "US Dollar",
                        decimalDigits = 2,
                        isActive = true,
                        sortOrder = 1,
                    ),
                )

            db.accountDao().upsert(
                AccountEntity(
                    name = "Наличные",
                    currencyId = rubId,
                    initialBalance = 0.0,
                    type = "cash",
                    colorHex = "#EF5350",
                    iconKey = "ic_account_cash",
                    isDefault = true,
                    sortOrder = 0,
                    createdAt = now,
                    updatedAt = now,
                    isArchived = false,
                ),
            )
            db.categoryDao().upsert(
                CategoryEntity(
                    name = "Зарплата",
                    kind = "income",
                    iconKey = "ic_cat_other",
                    colorHex = "#9CCC65",
                    sortOrder = 0,
                    isDefault = true,
                    isArchived = false,
                    createdAt = now,
                ),
            )

            val header = "date,account,category,amount,currency,converted amount,currency,description"
            val firstCsv =
                buildString {
                    append(header).append("\r\n")
                    append("01/01/2020,Наличные,Зарплата,100,USD,100,USD,\r\n")
                }
            val firstFile = File(context.cacheDir, "monefy_nfc_first.csv")
            firstFile.writeText(firstCsv, Charsets.UTF_8)

            val repo = BackupRepositoryImpl(context, db, Dispatchers.IO)
            repo.importTransactionsCsv("file://${firstFile.absolutePath}")

            val accountsBetween = db.accountDao().observeActive().first()
            assertEquals(2, accountsBetween.size)

            val secondCsv =
                buildString {
                    append(header).append("\r\n")
                    append("04/01/2020,Наличные,Зарплата,50,USD,50,USD,\r\n")
                }
            val secondFile = File(context.cacheDir, "monefy_nfc_second.csv")
            secondFile.writeText(secondCsv, Charsets.UTF_8)

            val result2 = repo.importTransactionsCsv("file://${secondFile.absolutePath}")
            assertTrue(
                "Second import returned failure: ${result2.exceptionOrNull()?.message}",
                result2.isSuccess,
            )

            val accountsAfter = db.accountDao().observeActive().first()
            assertEquals(
                "Second import of same name+currency must reuse Наличные (USD), not create a third account",
                2,
                accountsAfter.size,
            )

            val transactions = db.transactionDao().observeAll().first()
            assertEquals(2, transactions.size)
            val usdAccount = accountsAfter.single { it.currencyId == usdId }
            assertTrue(
                "Both USD transactions must point to the same suffixed account",
                transactions.all { it.accountId == usdAccount.id },
            )

            firstFile.delete()
            secondFile.delete()
        }

    @Test
    fun monefy_csv_import_category_kind_aware_same_name_expense_and_income_create_separate_entries() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val now = System.currentTimeMillis()

            val currencyId =
                db.currencyDao().upsert(
                    CurrencyEntity(
                        code = "RUB",
                        symbol = "₽",
                        name = "Russian Ruble",
                        decimalDigits = 2,
                        isActive = true,
                        sortOrder = 0,
                    ),
                )

            db.accountDao().upsert(
                AccountEntity(
                    name = "Наличные",
                    currencyId = currencyId,
                    initialBalance = 0.0,
                    type = "cash",
                    colorHex = "#EF5350",
                    iconKey = "ic_account_cash",
                    isDefault = true,
                    sortOrder = 0,
                    createdAt = now,
                    updatedAt = now,
                    isArchived = false,
                ),
            )

            // Seed "Прочее" as expense only — the same name as income must create a separate row.
            val expenseId =
                db.categoryDao().upsert(
                    CategoryEntity(
                        name = "Прочее",
                        kind = "expense",
                        iconKey = "ic_cat_other",
                        colorHex = "#EF5350",
                        sortOrder = 0,
                        isDefault = true,
                        isArchived = false,
                        createdAt = now,
                    ),
                )

            val categoriesBefore =
                db
                    .categoryDao()
                    .observeAll()
                    .first()
                    .size

            val header =
                "date,account,category,amount,currency,converted amount,currency,description"
            val csv =
                buildString {
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

    // -----------------------------------------------------------------------
    // parseImport — parse-only, DB must remain untouched (SPEC-02 O1)
    // -----------------------------------------------------------------------

    @Test
    fun `parseImport returns preview without writing to the database`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val now = System.currentTimeMillis()

            val rubId =
                db.currencyDao().upsert(
                    CurrencyEntity(
                        code = "RUB",
                        symbol = "₽",
                        name = "Russian Ruble",
                        decimalDigits = 2,
                        isActive = true,
                        sortOrder = 0,
                    ),
                )
            db.accountDao().upsert(
                AccountEntity(
                    name = "Наличные",
                    currencyId = rubId,
                    initialBalance = 0.0,
                    type = "cash",
                    colorHex = "#EF5350",
                    iconKey = "ic_account_cash",
                    isDefault = true,
                    sortOrder = 0,
                    createdAt = now,
                    updatedAt = now,
                    isArchived = false,
                ),
            )
            db.categoryDao().upsert(
                CategoryEntity(
                    name = "Продукты",
                    kind = "expense",
                    iconKey = "ic_cat_other",
                    colorHex = "#EF5350",
                    sortOrder = 0,
                    isDefault = true,
                    isArchived = false,
                    createdAt = now,
                ),
            )

            val accountsBefore =
                db
                    .accountDao()
                    .observeActive()
                    .first()
                    .size
            val categoriesBefore =
                db
                    .categoryDao()
                    .observeAll()
                    .first()
                    .size
            val txBefore =
                db
                    .transactionDao()
                    .observeAll()
                    .first()
                    .size

            val header =
                "date,account,category,amount,currency,converted amount,currency,description"
            val csv =
                buildString {
                    append(header).append("\r\n")
                    append("01/01/2020,Наличные,Продукты,-100,RUB,-100,RUB,хлеб\r\n")
                    append("02/01/2020,Наличные,Продукты,-200,RUB,-200,RUB,молоко\r\n")
                    append("03/01/2020,Наличные,Продукты,-300,RUB,-300,RUB,мясо\r\n")
                }
            val csvFile = File(context.cacheDir, "parse_only_test.csv")
            csvFile.writeText(csv, Charsets.UTF_8)

            val repo = BackupRepositoryImpl(context, db, Dispatchers.IO)
            val result = repo.parseImport("file://${csvFile.absolutePath}")

            assertTrue(
                "parseImport returned failure: ${result.exceptionOrNull()?.message}",
                result.isSuccess,
            )
            val staged = result.getOrThrow()
            assertEquals("Expected 3 data rows in preview", 3, staged.preview.rowCount)
            assertEquals(
                "Expected one unique category in preview",
                1,
                staged.preview.categories.size,
            )
            assertEquals(
                "Expected one unique account in preview",
                1,
                staged.preview.accounts.size,
            )
            assertNotNull("Date range must be present", staged.preview.dateRange)

            assertEquals(
                "parseImport must not create new accounts",
                accountsBefore,
                db
                    .accountDao()
                    .observeActive()
                    .first()
                    .size,
            )
            assertEquals(
                "parseImport must not create new categories",
                categoriesBefore,
                db
                    .categoryDao()
                    .observeAll()
                    .first()
                    .size,
            )
            assertEquals(
                "parseImport must not insert any transactions",
                txBefore,
                db
                    .transactionDao()
                    .observeAll()
                    .first()
                    .size,
            )

            csvFile.delete()
        }

    // -----------------------------------------------------------------------
    // commitImport Append: 3 existing + 5 imported = 8 (SPEC-02 A1)
    // -----------------------------------------------------------------------

    @Test
    fun `commitImport with Append adds imported rows to existing transactions`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val now = System.currentTimeMillis()

            val rubId =
                db.currencyDao().upsert(
                    CurrencyEntity(
                        code = "RUB",
                        symbol = "₽",
                        name = "Russian Ruble",
                        decimalDigits = 2,
                        isActive = true,
                        sortOrder = 0,
                    ),
                )
            val seedAccountId =
                db.accountDao().upsert(
                    AccountEntity(
                        name = "Наличные",
                        currencyId = rubId,
                        initialBalance = 0.0,
                        type = "cash",
                        colorHex = "#EF5350",
                        iconKey = "ic_account_cash",
                        isDefault = true,
                        sortOrder = 0,
                        createdAt = now,
                        updatedAt = now,
                        isArchived = false,
                    ),
                )
            val seedCategoryId =
                db.categoryDao().upsert(
                    CategoryEntity(
                        name = "Продукты",
                        kind = "expense",
                        iconKey = "ic_cat_other",
                        colorHex = "#EF5350",
                        sortOrder = 0,
                        isDefault = true,
                        isArchived = false,
                        createdAt = now,
                    ),
                )

            // Seed 3 existing transactions via direct DAO (not via import).
            repeat(3) { i ->
                db.transactionDao().upsert(
                    TransactionEntity(
                        id = 0L,
                        kind = "expense",
                        amount = (i + 1) * 100.0,
                        currencyId = rubId,
                        accountId = seedAccountId,
                        categoryId = seedCategoryId,
                        note = "existing $i",
                        occurredAt = now - (i + 1) * 86_400_000L,
                        createdAt = now,
                        updatedAt = now,
                        isDeleted = false,
                        toAccountId = null,
                        toAmount = null,
                        exchangeRate = null,
                    ),
                )
            }

            val txBefore =
                db
                    .transactionDao()
                    .observeAll()
                    .first()
                    .size
            assertEquals("Expected 3 seeded transactions", 3, txBefore)

            val header =
                "date,account,category,amount,currency,converted amount,currency,description"
            val csv =
                buildString {
                    append(header).append("\r\n")
                    append("10/01/2020,Наличные,Продукты,-50,RUB,-50,RUB,import1\r\n")
                    append("11/01/2020,Наличные,Продукты,-60,RUB,-60,RUB,import2\r\n")
                    append("12/01/2020,Наличные,Продукты,-70,RUB,-70,RUB,import3\r\n")
                    append("13/01/2020,Наличные,Продукты,-80,RUB,-80,RUB,import4\r\n")
                    append("14/01/2020,Наличные,Продукты,-90,RUB,-90,RUB,import5\r\n")
                }
            val csvFile = File(context.cacheDir, "append_strategy_test.csv")
            csvFile.writeText(csv, Charsets.UTF_8)

            val repo = BackupRepositoryImpl(context, db, Dispatchers.IO)
            val staged = repo.parseImport("file://${csvFile.absolutePath}").getOrThrow()

            val result =
                repo.commitImport(
                    staged,
                    ImportPlan(
                        dataStrategy = ImportDataStrategy.Append,
                        categoryStrategy = ImportCategoryStrategy.Append,
                    ),
                )

            assertTrue(
                "commitImport(Append) returned failure: ${result.exceptionOrNull()?.message}",
                result.isSuccess,
            )

            val txAfter =
                db
                    .transactionDao()
                    .observeAll()
                    .first()
                    .size
            assertEquals("3 existing + 5 imported must equal 8", 8, txAfter)

            csvFile.delete()
        }

    // -----------------------------------------------------------------------
    // commitImport AppendDedup: drops intra-file dupes + against-DB dupes (SPEC-02 D3)
    // -----------------------------------------------------------------------

    @Test
    fun `commitImport with AppendDedup drops intra-file duplicates and against-DB duplicates`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val now = System.currentTimeMillis()

            val rubId =
                db.currencyDao().upsert(
                    CurrencyEntity(
                        code = "RUB",
                        symbol = "₽",
                        name = "Russian Ruble",
                        decimalDigits = 2,
                        isActive = true,
                        sortOrder = 0,
                    ),
                )
            db.accountDao().upsert(
                AccountEntity(
                    name = "Наличные",
                    currencyId = rubId,
                    initialBalance = 0.0,
                    type = "cash",
                    colorHex = "#EF5350",
                    iconKey = "ic_account_cash",
                    isDefault = true,
                    sortOrder = 0,
                    createdAt = now,
                    updatedAt = now,
                    isArchived = false,
                ),
            )
            db.categoryDao().upsert(
                CategoryEntity(
                    name = "Продукты",
                    kind = "expense",
                    iconKey = "ic_cat_other",
                    colorHex = "#EF5350",
                    sortOrder = 0,
                    isDefault = true,
                    isArchived = false,
                    createdAt = now,
                ),
            )

            // First import — Append — seeds row A and row B.
            val headerLine =
                "date,account,category,amount,currency,converted amount,currency,description"
            val firstCsv =
                buildString {
                    append(headerLine).append("\r\n")
                    // row A: 01/01/2020, -100
                    append("01/01/2020,Наличные,Продукты,-100,RUB,-100,RUB,rowA\r\n")
                    // row B: 02/01/2020, -200
                    append("02/01/2020,Наличные,Продукты,-200,RUB,-200,RUB,rowB\r\n")
                }
            val firstFile = File(context.cacheDir, "dedup_first.csv")
            firstFile.writeText(firstCsv, Charsets.UTF_8)

            val repo = BackupRepositoryImpl(context, db, Dispatchers.IO)
            repo.commitImport(
                repo.parseImport("file://${firstFile.absolutePath}").getOrThrow(),
                ImportPlan(ImportDataStrategy.Append, ImportCategoryStrategy.Append),
            )

            val txAfterFirst =
                db
                    .transactionDao()
                    .observeAll()
                    .first()
                    .size
            assertEquals("Expected 2 transactions after first import", 2, txAfterFirst)

            // Second CSV: row A again (against-DB dupe), row C twice (intra-file dupe), row B again (against-DB dupe).
            val secondCsv =
                buildString {
                    append(headerLine).append("\r\n")
                    // dupe of row A already in DB
                    append("01/01/2020,Наличные,Продукты,-100,RUB,-100,RUB,rowA\r\n")
                    // row C (new) — first occurrence, must be kept
                    append("03/01/2020,Наличные,Продукты,-300,RUB,-300,RUB,rowC\r\n")
                    // row C again — intra-file dupe, must be dropped
                    append("03/01/2020,Наличные,Продукты,-300,RUB,-300,RUB,rowC\r\n")
                    // dupe of row B already in DB
                    append("02/01/2020,Наличные,Продукты,-200,RUB,-200,RUB,rowB\r\n")
                }
            val secondFile = File(context.cacheDir, "dedup_second.csv")
            secondFile.writeText(secondCsv, Charsets.UTF_8)

            val result =
                repo.commitImport(
                    repo.parseImport("file://${secondFile.absolutePath}").getOrThrow(),
                    ImportPlan(ImportDataStrategy.AppendDedup, ImportCategoryStrategy.Append),
                )

            assertTrue(
                "commitImport(AppendDedup) returned failure: ${result.exceptionOrNull()?.message}",
                result.isSuccess,
            )

            val txAfterSecond =
                db
                    .transactionDao()
                    .observeAll()
                    .first()
                    .size
            assertEquals(
                "Only row C must be added: 2 existing + 1 unique new = 3",
                3,
                txAfterSecond,
            )

            firstFile.delete()
            secondFile.delete()
        }

    // -----------------------------------------------------------------------
    // commitImport ReplaceAll: clears tx/accounts/categories; currencies survive (SPEC-02 O2)
    // -----------------------------------------------------------------------

    @Test
    fun `commitImport with ReplaceAll clears existing data and imports fresh keeping currencies`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val now = System.currentTimeMillis()

            val rubId =
                db.currencyDao().upsert(
                    CurrencyEntity(
                        code = "RUB",
                        symbol = "₽",
                        name = "Russian Ruble",
                        decimalDigits = 2,
                        isActive = true,
                        sortOrder = 0,
                    ),
                )
            // Seed a second currency — it must survive ReplaceAll.
            val usdId =
                db.currencyDao().upsert(
                    CurrencyEntity(
                        code = "USD",
                        symbol = "$",
                        name = "US Dollar",
                        decimalDigits = 2,
                        isActive = true,
                        sortOrder = 1,
                    ),
                )

            val oldAccountId =
                db.accountDao().upsert(
                    AccountEntity(
                        name = "ОldAccount",
                        currencyId = rubId,
                        initialBalance = 0.0,
                        type = "cash",
                        colorHex = "#EF5350",
                        iconKey = "ic_account_cash",
                        isDefault = true,
                        sortOrder = 0,
                        createdAt = now,
                        updatedAt = now,
                        isArchived = false,
                    ),
                )
            val oldCategoryId =
                db.categoryDao().upsert(
                    CategoryEntity(
                        name = "OldCategory",
                        kind = "expense",
                        iconKey = "ic_cat_other",
                        colorHex = "#EF5350",
                        sortOrder = 0,
                        isDefault = true,
                        isArchived = false,
                        createdAt = now,
                    ),
                )
            db.transactionDao().upsert(
                TransactionEntity(
                    id = 0L,
                    kind = "expense",
                    amount = 999.0,
                    currencyId = rubId,
                    accountId = oldAccountId,
                    categoryId = oldCategoryId,
                    note = "old tx",
                    occurredAt = now - 86_400_000L,
                    createdAt = now,
                    updatedAt = now,
                    isDeleted = false,
                    toAccountId = null,
                    toAmount = null,
                    exchangeRate = null,
                ),
            )

            assertEquals(
                "Expected 1 old transaction before replace",
                1,
                db
                    .transactionDao()
                    .observeAll()
                    .first()
                    .size,
            )
            assertEquals(
                "Expected 1 old account before replace",
                1,
                db
                    .accountDao()
                    .observeActive()
                    .first()
                    .size,
            )
            assertEquals(
                "Expected 1 old category before replace",
                1,
                db
                    .categoryDao()
                    .observeAll()
                    .first()
                    .size,
            )

            // Import CSV that references RUB (the seeded currency).
            val header =
                "date,account,category,amount,currency,converted amount,currency,description"
            val csv =
                buildString {
                    append(header).append("\r\n")
                    append("15/01/2020,NewAccount,NewCategory,-500,RUB,-500,RUB,fresh\r\n")
                    append("16/01/2020,NewAccount,NewCategory,-600,RUB,-600,RUB,fresh2\r\n")
                }
            val csvFile = File(context.cacheDir, "replace_all_test.csv")
            csvFile.writeText(csv, Charsets.UTF_8)

            val repo = BackupRepositoryImpl(context, db, Dispatchers.IO)
            val staged = repo.parseImport("file://${csvFile.absolutePath}").getOrThrow()

            val result =
                repo.commitImport(
                    staged,
                    ImportPlan(
                        dataStrategy = ImportDataStrategy.ReplaceAll,
                        categoryStrategy = ImportCategoryStrategy.Append,
                    ),
                )

            assertTrue(
                "commitImport(ReplaceAll) returned failure: ${result.exceptionOrNull()?.message}",
                result.isSuccess,
            )

            // Old tx must be gone; only 2 fresh ones remain.
            val txAfter = db.transactionDao().observeAll().first()
            assertEquals("ReplaceAll must leave exactly 2 imported transactions", 2, txAfter.size)
            assertTrue(
                "Old transaction (note='old tx') must not exist after ReplaceAll",
                txAfter.none { it.note == "old tx" },
            )

            // Old account must be gone; only NewAccount remains.
            val accountsAfter = db.accountDao().observeActive().first()
            assertEquals("Only NewAccount must remain after ReplaceAll", 1, accountsAfter.size)
            assertEquals("NewAccount", accountsAfter.single().name)
            assertTrue(
                "Old account id must not reference OldAccount",
                accountsAfter.none { it.id == oldAccountId },
            )

            // Old category must be gone; only NewCategory remains.
            val categoriesAfter = db.categoryDao().observeAll().first()
            assertEquals("Only NewCategory must remain after ReplaceAll", 1, categoriesAfter.size)
            assertEquals("NewCategory", categoriesAfter.single().name)
            assertTrue(
                "Old category id must not reference OldCategory",
                categoriesAfter.none { it.id == oldCategoryId },
            )

            // Currencies must survive ReplaceAll — both RUB and USD.
            val currencies = db.currencyDao().observeAll().first()
            assertTrue("RUB must survive ReplaceAll", currencies.any { it.id == rubId })
            assertTrue("USD must survive ReplaceAll", currencies.any { it.id == usdId })
            assertEquals("Both currencies must remain after ReplaceAll", 2, currencies.size)

            csvFile.delete()
        }

    // -----------------------------------------------------------------------
    // DAO smoke: deleteAll methods for the new instrumented DAO coverage (SPEC TEST_TYPES: dao)
    // -----------------------------------------------------------------------

    @Test
    fun `TransactionDao deleteAll removes all rows`() =
        runTest {
            val now = System.currentTimeMillis()
            val rubId =
                db.currencyDao().upsert(
                    CurrencyEntity(
                        code = "RUB",
                        symbol = "₽",
                        name = "Russian Ruble",
                        decimalDigits = 2,
                        isActive = true,
                        sortOrder = 0,
                    ),
                )
            val accountId =
                db.accountDao().upsert(
                    AccountEntity(
                        name = "Наличные",
                        currencyId = rubId,
                        initialBalance = 0.0,
                        type = "cash",
                        colorHex = "#EF5350",
                        iconKey = "ic_account_cash",
                        isDefault = true,
                        sortOrder = 0,
                        createdAt = now,
                        updatedAt = now,
                        isArchived = false,
                    ),
                )
            val categoryId =
                db.categoryDao().upsert(
                    CategoryEntity(
                        name = "Продукты",
                        kind = "expense",
                        iconKey = "ic_cat_other",
                        colorHex = "#EF5350",
                        sortOrder = 0,
                        isDefault = true,
                        isArchived = false,
                        createdAt = now,
                    ),
                )
            db.transactionDao().upsert(
                TransactionEntity(
                    id = 0L,
                    kind = "expense",
                    amount = 100.0,
                    currencyId = rubId,
                    accountId = accountId,
                    categoryId = categoryId,
                    note = null,
                    occurredAt = now,
                    createdAt = now,
                    updatedAt = now,
                    isDeleted = false,
                    toAccountId = null,
                    toAmount = null,
                    exchangeRate = null,
                ),
            )
            assertTrue(
                "Precondition: at least 1 transaction must exist",
                db
                    .transactionDao()
                    .observeAll()
                    .first()
                    .isNotEmpty(),
            )

            db.transactionDao().deleteAll()

            assertTrue(
                "After deleteAll, no transactions must remain",
                db
                    .transactionDao()
                    .observeAll()
                    .first()
                    .isEmpty(),
            )
        }

    @Test
    fun `AccountDao deleteAll removes all rows`() =
        runTest {
            val now = System.currentTimeMillis()
            val rubId =
                db.currencyDao().upsert(
                    CurrencyEntity(
                        code = "RUB",
                        symbol = "₽",
                        name = "Russian Ruble",
                        decimalDigits = 2,
                        isActive = true,
                        sortOrder = 0,
                    ),
                )
            db.accountDao().upsert(
                AccountEntity(
                    name = "Карта",
                    currencyId = rubId,
                    initialBalance = 0.0,
                    type = "card",
                    colorHex = "#5C6BC0",
                    iconKey = "ic_account_cash",
                    isDefault = false,
                    sortOrder = 0,
                    createdAt = now,
                    updatedAt = now,
                    isArchived = false,
                ),
            )
            assertTrue(
                "Precondition: at least 1 account must exist",
                db
                    .accountDao()
                    .observeActive()
                    .first()
                    .isNotEmpty(),
            )

            db.accountDao().deleteAll()

            assertTrue(
                "After deleteAll, no accounts must remain",
                db
                    .accountDao()
                    .observeActive()
                    .first()
                    .isEmpty(),
            )
        }

    @Test
    fun `CategoryDao deleteAll removes all rows`() =
        runTest {
            val now = System.currentTimeMillis()
            db.categoryDao().upsert(
                CategoryEntity(
                    name = "Еда",
                    kind = "expense",
                    iconKey = "ic_cat_other",
                    colorHex = "#EF5350",
                    sortOrder = 0,
                    isDefault = true,
                    isArchived = false,
                    createdAt = now,
                ),
            )
            assertTrue(
                "Precondition: at least 1 category must exist",
                db
                    .categoryDao()
                    .observeAll()
                    .first()
                    .isNotEmpty(),
            )

            db.categoryDao().deleteAll()

            assertTrue(
                "After deleteAll, no categories must remain",
                db
                    .categoryDao()
                    .observeAll()
                    .first()
                    .isEmpty(),
            )
        }

    @Test
    fun `TransactionDao listDedupRows returns account and category names via join`() =
        runTest {
            val now = System.currentTimeMillis()
            val rubId =
                db.currencyDao().upsert(
                    CurrencyEntity(
                        code = "RUB",
                        symbol = "₽",
                        name = "Russian Ruble",
                        decimalDigits = 2,
                        isActive = true,
                        sortOrder = 0,
                    ),
                )
            val accountId =
                db.accountDao().upsert(
                    AccountEntity(
                        name = "Наличные",
                        currencyId = rubId,
                        initialBalance = 0.0,
                        type = "cash",
                        colorHex = "#EF5350",
                        iconKey = "ic_account_cash",
                        isDefault = true,
                        sortOrder = 0,
                        createdAt = now,
                        updatedAt = now,
                        isArchived = false,
                    ),
                )
            val categoryId =
                db.categoryDao().upsert(
                    CategoryEntity(
                        name = "Продукты",
                        kind = "expense",
                        iconKey = "ic_cat_other",
                        colorHex = "#EF5350",
                        sortOrder = 0,
                        isDefault = true,
                        isArchived = false,
                        createdAt = now,
                    ),
                )
            val amount = 250.0
            val occurredAt = now - 86_400_000L
            db.transactionDao().upsert(
                TransactionEntity(
                    id = 0L,
                    kind = "expense",
                    amount = amount,
                    currencyId = rubId,
                    accountId = accountId,
                    categoryId = categoryId,
                    note = "dedup note",
                    occurredAt = occurredAt,
                    createdAt = now,
                    updatedAt = now,
                    isDeleted = false,
                    toAccountId = null,
                    toAmount = null,
                    exchangeRate = null,
                ),
            )

            val rows = db.transactionDao().listDedupRows()

            assertEquals("Expected exactly 1 dedup row", 1, rows.size)
            val row = rows.single()
            assertEquals("accountName must match seeded account", "Наличные", row.accountName)
            assertEquals("categoryName must match seeded category", "Продукты", row.categoryName)
            assertEquals("kind must match inserted kind", "expense", row.kind)
            assertEquals("amount must match inserted amount", amount, row.amount, 0.001)
            assertEquals("occurredAt must match inserted value", occurredAt, row.occurredAt)
            assertEquals("note must be preserved", "dedup note", row.note)
        }
}
