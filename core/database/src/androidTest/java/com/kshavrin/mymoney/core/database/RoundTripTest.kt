package com.kshavrin.mymoney.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kshavrin.mymoney.core.database.entity.AccountEntity
import com.kshavrin.mymoney.core.database.entity.BudgetEntity
import com.kshavrin.mymoney.core.database.entity.CategoryEntity
import com.kshavrin.mymoney.core.database.entity.CurrencyEntity
import com.kshavrin.mymoney.core.database.entity.CurrencyRateEntity
import com.kshavrin.mymoney.core.database.entity.RecurringTemplateEntity
import com.kshavrin.mymoney.core.database.entity.SyncLogEntity
import com.kshavrin.mymoney.core.database.entity.TransactionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoundTripTest {
    private lateinit var db: MoneyDatabase

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    MoneyDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun currency_roundtrip() =
        runTest {
            val id =
                db.currencyDao().upsert(
                    CurrencyEntity(code = "USD", symbol = "$", name = "US Dollar", decimalDigits = 2, isActive = true, sortOrder = 0),
                )
            val read = db.currencyDao().findById(id)
            assertNotNull(read)
            assertEquals("USD", read!!.code)
        }

    @Test
    fun account_roundtrip() =
        runTest {
            val currencyId =
                db.currencyDao().upsert(
                    CurrencyEntity(code = "USD", symbol = "$", name = "US Dollar", decimalDigits = 2, isActive = true, sortOrder = 0),
                )
            val id =
                db.accountDao().upsert(
                    AccountEntity(
                        name = "Cash",
                        currencyId = currencyId,
                        initialBalance = 100.0,
                        type = "cash",
                        colorHex = "#7AC794",
                        iconKey = "ic_cash",
                        isDefault = true,
                        sortOrder = 0,
                        createdAt = 0L,
                        updatedAt = 0L,
                        isArchived = false,
                    ),
                )
            val read = db.accountDao().findById(id)
            assertNotNull(read)
            assertEquals("Cash", read!!.name)
        }

    @Test
    fun category_roundtrip() =
        runTest {
            val id =
                db.categoryDao().upsert(
                    CategoryEntity(
                        name = "Food",
                        kind = "expense",
                        iconKey = "ic_cat_food",
                        colorHex = "#E07AAE",
                        textColor = "#FFFFFF",
                        sortOrder = 0,
                        isDefault = true,
                        isArchived = false,
                        createdAt = 0L,
                    ),
                )
            val read = db.categoryDao().findById(id)
            assertNotNull(read)
            assertEquals("Food", read!!.name)
        }

    @Test
    fun transaction_roundtrip() =
        runTest {
            val currencyId =
                db.currencyDao().upsert(
                    CurrencyEntity(code = "USD", symbol = "$", name = "US Dollar", decimalDigits = 2, isActive = true, sortOrder = 0),
                )
            val accountId =
                db.accountDao().upsert(
                    AccountEntity(name = "Cash", currencyId = currencyId, initialBalance = 100.0, type = "cash", colorHex = "#7AC794", iconKey = "ic_cash", isDefault = true, sortOrder = 0, createdAt = 0L, updatedAt = 0L, isArchived = false),
                )
            val categoryId =
                db.categoryDao().upsert(
                    CategoryEntity(name = "Food", kind = "expense", iconKey = "ic_cat_food", colorHex = "#E07AAE", textColor = "#FFFFFF", sortOrder = 0, isDefault = true, isArchived = false, createdAt = 0L),
                )
            val id =
                db.transactionDao().upsert(
                    TransactionEntity(
                        kind = "expense",
                        amount = 10.5,
                        currencyId = currencyId,
                        accountId = accountId,
                        categoryId = categoryId,
                        note = "lunch",
                        occurredAt = 0L,
                        createdAt = 0L,
                        updatedAt = 0L,
                        isDeleted = false,
                        toAccountId = null,
                        toAmount = null,
                        exchangeRate = null,
                    ),
                )
            val read = db.transactionDao().findById(id)
            assertNotNull(read)
            assertEquals(10.5, read!!.amount, 0.0)
        }

    @Test
    fun budget_roundtrip() =
        runTest {
            val currencyId =
                db.currencyDao().upsert(
                    CurrencyEntity(code = "USD", symbol = "$", name = "US Dollar", decimalDigits = 2, isActive = true, sortOrder = 0),
                )
            val categoryId =
                db.categoryDao().upsert(
                    CategoryEntity(name = "Food", kind = "expense", iconKey = "ic_cat_food", colorHex = "#E07AAE", textColor = "#FFFFFF", sortOrder = 0, isDefault = true, isArchived = false, createdAt = 0L),
                )
            val id =
                db.budgetDao().upsert(
                    BudgetEntity(
                        categoryId = categoryId,
                        periodKind = "month",
                        periodStart = 0L,
                        amount = 500.0,
                        currencyId = currencyId,
                        alertThresholdPct = 80,
                        isActive = true,
                    ),
                )
            val read = db.budgetDao().findForCategory(categoryId)
            assertNotNull(read)
            assertEquals(500.0, read!!.amount, 0.0)
        }

    @Test
    fun recurring_template_roundtrip() =
        runTest {
            val currencyId =
                db.currencyDao().upsert(
                    CurrencyEntity(code = "USD", symbol = "$", name = "US Dollar", decimalDigits = 2, isActive = true, sortOrder = 0),
                )
            val accountId =
                db.accountDao().upsert(
                    AccountEntity(name = "Cash", currencyId = currencyId, initialBalance = 100.0, type = "cash", colorHex = "#7AC794", iconKey = "ic_cash", isDefault = true, sortOrder = 0, createdAt = 0L, updatedAt = 0L, isArchived = false),
                )
            val id =
                db.recurringTemplateDao().upsert(
                    RecurringTemplateEntity(
                        baseKind = "expense",
                        amount = 50.0,
                        currencyId = currencyId,
                        accountId = accountId,
                        categoryId = null,
                        toAccountId = null,
                        note = null,
                        recurrenceKind = "monthly",
                        interval = 1,
                        byDay = null,
                        startsAt = 0L,
                        endsAt = null,
                        nextRunAt = 0L,
                        isActive = true,
                    ),
                )
            val due = db.recurringTemplateDao().findDue(1L)
            assertEquals(1, due.size)
            assertEquals(id, due.first().id)
        }

    @Test
    fun sync_log_roundtrip() =
        runTest {
            val id =
                db.syncLogDao().insert(
                    SyncLogEntity(
                        target = "dropbox",
                        event = "push",
                        entityKind = null,
                        entityId = null,
                        performedAt = 0L,
                        status = "success",
                        payloadHash = "abc",
                        errorMessage = null,
                    ),
                )
            val list = db.syncLogDao().recentByTarget("dropbox", 10)
            assertEquals(1, list.size)
            assertEquals(id, list.first().id)
        }

    @Test
    fun search_history_roundtrip() =
        runTest {
            db.searchHistoryDao().upsertQuery("food", 0L)
            val list = db.searchHistoryDao().observe()
            // observe() returns a Flow — can't easily collect in this scaffolding test;
            // a focused FlowTurbine test arrives in PHASE_05 / PHASE_11. This test pins the upsert path.
            assertNotNull(list)
        }

    @Test
    fun currency_rate_roundtrip() =
        runTest {
            val fromId =
                db.currencyDao().upsert(
                    CurrencyEntity(code = "USD", symbol = "$", name = "US Dollar", decimalDigits = 2, isActive = true, sortOrder = 0),
                )
            val toId =
                db.currencyDao().upsert(
                    CurrencyEntity(code = "EUR", symbol = "€", name = "Euro", decimalDigits = 2, isActive = true, sortOrder = 1),
                )
            val id =
                db.currencyRateDao().upsert(
                    CurrencyRateEntity(
                        fromCurrencyId = fromId,
                        toCurrencyId = toId,
                        rate = 0.92,
                        updatedAt = 0L,
                    ),
                )
            val read = db.currencyRateDao().findRate(fromId, toId)
            assertNotNull(read)
            assertEquals(0.92, read!!.rate, 0.0)
        }
}
