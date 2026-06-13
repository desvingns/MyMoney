package com.kshavrin.mymoney.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kshavrin.mymoney.core.database.entity.AccountEntity
import com.kshavrin.mymoney.core.database.entity.CategoryEntity
import com.kshavrin.mymoney.core.database.entity.CurrencyEntity
import com.kshavrin.mymoney.core.database.entity.TransactionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionDaoCategoryGroupsTest {
    private lateinit var db: MoneyDatabase

    private var currencyId: Long = 0L
    private var accountA: Long = 0L
    private var accountB: Long = 0L
    private var categoryFood: Long = 0L
    private var categoryBills: Long = 0L
    private var categorySalary: Long = 0L

    private val day = 86_400_000L

    @Before
    fun setUp() =
        runTest {
            db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext(),
                        MoneyDatabase::class.java,
                    ).allowMainThreadQueries()
                    .build()

            currencyId =
                db.currencyDao().upsert(
                    CurrencyEntity(code = "USD", symbol = "$", name = "US Dollar", decimalDigits = 2, isActive = true, sortOrder = 0),
                )
            accountA = db.accountDao().upsert(account("A"))
            accountB = db.accountDao().upsert(account("B"))
            categoryFood = db.categoryDao().upsert(category("Food", kind = "expense"))
            categoryBills = db.categoryDao().upsert(category("Bills", kind = "expense"))
            categorySalary = db.categoryDao().upsert(category("Salary", kind = "income"))
        }

    @After
    fun tearDown() {
        db.close()
    }

    private fun account(name: String) =
        AccountEntity(
            name = name,
            currencyId = currencyId,
            initialBalance = 0.0,
            type = "cash",
            colorHex = "#7AC794",
            iconKey = "ic_cash",
            isDefault = false,
            sortOrder = 0,
            createdAt = 0L,
            updatedAt = 0L,
            isArchived = false,
        )

    private fun category(
        name: String,
        kind: String,
    ) =
        CategoryEntity(
            name = name,
            kind = kind,
            iconKey = "ic_cat_${name.lowercase()}",
            colorHex = "#E07AAE",
            sortOrder = 0,
            isDefault = false,
            isArchived = false,
            createdAt = 0L,
        )

    private suspend fun insert(
        accountId: Long = accountA,
        categoryId: Long?,
        amount: Double,
        occurredAt: Long,
        kind: String = "expense",
        isDeleted: Boolean = false,
        toAccountId: Long? = null,
    ): Long =
        db.transactionDao().upsert(
            TransactionEntity(
                kind = kind,
                amount = amount,
                currencyId = currencyId,
                accountId = accountId,
                categoryId = categoryId,
                note = null,
                occurredAt = occurredAt,
                createdAt = occurredAt,
                updatedAt = occurredAt,
                isDeleted = isDeleted,
                toAccountId = toAccountId,
                toAmount = null,
                exchangeRate = null,
            ),
        )

    @Test
    fun getCategoryGroups_sums_amount_and_counts_rows_per_category() =
        runTest {
            insert(categoryId = categoryFood, amount = 10.0, occurredAt = 1 * day)
            insert(categoryId = categoryFood, amount = 5.0, occurredAt = 2 * day)
            insert(categoryId = categoryBills, amount = 7.0, occurredAt = 3 * day)

            val groups = db.transactionDao().getCategoryGroups(accountA, from = 0L, to = 10 * day)

            val food = groups.first { it.categoryId == categoryFood }
            val bills = groups.first { it.categoryId == categoryBills }
            assertEquals(15.0, food.total, 0.0001)
            assertEquals(2, food.txCount)
            assertEquals(7.0, bills.total, 0.0001)
            assertEquals(1, bills.txCount)
        }

    @Test
    fun getCategoryGroups_includes_both_income_and_expense_with_correct_kind() =
        runTest {
            insert(categoryId = categoryFood, amount = 10.0, occurredAt = 1 * day, kind = "expense")
            insert(categoryId = categorySalary, amount = 100.0, occurredAt = 2 * day, kind = "income")

            val groups = db.transactionDao().getCategoryGroups(accountA, from = 0L, to = 10 * day)

            assertEquals(2, groups.size)
            assertEquals("expense", groups.first { it.categoryId == categoryFood }.kind)
            assertEquals("income", groups.first { it.categoryId == categorySalary }.kind)
        }

    @Test
    fun getCategoryGroups_orders_by_total_desc() =
        runTest {
            insert(categoryId = categoryFood, amount = 10.0, occurredAt = 1 * day)
            insert(categoryId = categoryBills, amount = 50.0, occurredAt = 2 * day)
            insert(categoryId = categorySalary, amount = 30.0, occurredAt = 3 * day, kind = "income")

            val groups = db.transactionDao().getCategoryGroups(accountA, from = 0L, to = 10 * day)

            assertEquals(
                listOf(categoryBills, categorySalary, categoryFood),
                groups.map { it.categoryId },
            )
        }

    @Test
    fun getCategoryGroups_excludes_soft_deleted_rows_from_total_and_count() =
        runTest {
            insert(categoryId = categoryFood, amount = 10.0, occurredAt = 1 * day, isDeleted = false)
            insert(categoryId = categoryFood, amount = 99.0, occurredAt = 2 * day, isDeleted = true)

            val groups = db.transactionDao().getCategoryGroups(accountA, from = 0L, to = 10 * day)

            val food = groups.first { it.categoryId == categoryFood }
            assertEquals(10.0, food.total, 0.0001)
            assertEquals(1, food.txCount)
        }

    @Test
    fun getCategoryGroups_excludes_transfers() =
        runTest {
            insert(categoryId = categoryFood, amount = 10.0, occurredAt = 1 * day, kind = "expense")
            insert(
                categoryId = null,
                amount = 25.0,
                occurredAt = 2 * day,
                kind = "transfer",
                toAccountId = accountB,
            )

            val groups = db.transactionDao().getCategoryGroups(accountA, from = 0L, to = 10 * day)

            assertEquals(listOf(categoryFood), groups.map { it.categoryId })
            assertTrue("transfers must not appear", groups.none { it.kind == "transfer" })
        }

    @Test
    fun getCategoryGroups_filters_by_account_and_period() =
        runTest {
            insert(accountId = accountA, categoryId = categoryFood, amount = 10.0, occurredAt = 5 * day)
            insert(accountId = accountB, categoryId = categoryFood, amount = 10.0, occurredAt = 5 * day)
            insert(accountId = accountA, categoryId = categoryBills, amount = 10.0, occurredAt = 50 * day)

            val groups = db.transactionDao().getCategoryGroups(accountA, from = 0L, to = 10 * day)

            assertEquals(listOf(categoryFood), groups.map { it.categoryId })
        }

    @Test
    fun getCategoryGroups_returns_empty_for_empty_period() =
        runTest {
            insert(categoryId = categoryFood, amount = 10.0, occurredAt = 50 * day)

            val groups = db.transactionDao().getCategoryGroups(accountA, from = 0L, to = 10 * day)

            assertTrue("expected empty result but got $groups", groups.isEmpty())
        }
}
