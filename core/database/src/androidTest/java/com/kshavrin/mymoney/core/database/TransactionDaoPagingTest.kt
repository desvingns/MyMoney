package com.kshavrin.mymoney.core.database

import androidx.paging.PagingSource
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionDaoPagingTest {
    private lateinit var db: MoneyDatabase

    private var currencyId: Long = 0L
    private var accountA: Long = 0L
    private var accountB: Long = 0L
    private var categoryFood: Long = 0L
    private var categoryBills: Long = 0L

    // A day in millis to keep occurred_at values readable.
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
            categoryFood = db.categoryDao().upsert(category("Food"))
            categoryBills = db.categoryDao().upsert(category("Bills"))
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

    private fun category(name: String) =
        CategoryEntity(
            name = name,
            kind = "expense",
            iconKey = "ic_cat_${name.lowercase()}",
            colorHex = "#E07AAE",
            sortOrder = 0,
            isDefault = false,
            isArchived = false,
            createdAt = 0L,
        )

    private suspend fun insert(
        accountId: Long,
        occurredAt: Long,
        categoryId: Long? = categoryFood,
        isDeleted: Boolean = false,
        createdAt: Long = occurredAt,
        kind: String = "expense",
        toAccountId: Long? = null,
    ): Long =
        db.transactionDao().upsert(
            TransactionEntity(
                kind = kind,
                amount = 10.0,
                currencyId = currencyId,
                accountId = accountId,
                categoryId = categoryId,
                note = null,
                occurredAt = occurredAt,
                createdAt = createdAt,
                updatedAt = createdAt,
                isDeleted = isDeleted,
                toAccountId = toAccountId,
                toAmount = null,
                exchangeRate = null,
            ),
        )

    private suspend fun loadAll(source: PagingSource<Int, TransactionEntity>): List<TransactionEntity> {
        val result =
            source.load(
                PagingSource.LoadParams.Refresh(
                    key = null,
                    loadSize = 100,
                    placeholdersEnabled = false,
                ),
            )
        assertTrue("expected a Page result but got $result", result is PagingSource.LoadResult.Page)
        return (result as PagingSource.LoadResult.Page).data
    }

    @Test
    fun pagedByPeriod_filters_by_account() =
        runTest {
            insert(accountId = accountA, occurredAt = 5 * day)
            insert(accountId = accountB, occurredAt = 5 * day)

            val page =
                loadAll(
                    db.transactionDao().pagedByPeriod(accountA, categoryId = null, from = 0L, to = 10 * day),
                )

            assertEquals(1, page.size)
            assertTrue(page.all { it.accountId == accountA })
        }

    @Test
    fun pagedByPeriod_filters_by_occurred_at_between_from_and_to() =
        runTest {
            insert(accountId = accountA, occurredAt = 1 * day)
            val inRange = insert(accountId = accountA, occurredAt = 5 * day)
            insert(accountId = accountA, occurredAt = 20 * day)

            val page =
                loadAll(
                    db.transactionDao().pagedByPeriod(accountA, categoryId = null, from = 3 * day, to = 10 * day),
                )

            assertEquals(listOf(inRange), page.map { it.id })
        }

    @Test
    fun pagedByPeriod_includes_boundary_values_inclusive() =
        runTest {
            val atFrom = insert(accountId = accountA, occurredAt = 3 * day)
            val atTo = insert(accountId = accountA, occurredAt = 10 * day)

            val page =
                loadAll(
                    db.transactionDao().pagedByPeriod(accountA, categoryId = null, from = 3 * day, to = 10 * day),
                )

            assertEquals(setOf(atFrom, atTo), page.map { it.id }.toSet())
        }

    @Test
    fun pagedByPeriod_excludes_soft_deleted_rows() =
        runTest {
            val live = insert(accountId = accountA, occurredAt = 5 * day, isDeleted = false)
            insert(accountId = accountA, occurredAt = 6 * day, isDeleted = true)

            val page =
                loadAll(
                    db.transactionDao().pagedByPeriod(accountA, categoryId = null, from = 0L, to = 10 * day),
                )

            assertEquals(listOf(live), page.map { it.id })
        }

    @Test
    fun pagedByPeriod_null_category_returns_all_including_transfers_with_null_category() =
        runTest {
            insert(accountId = accountA, occurredAt = 5 * day, categoryId = categoryFood)
            insert(accountId = accountA, occurredAt = 6 * day, categoryId = categoryBills)
            insert(
                accountId = accountA,
                occurredAt = 7 * day,
                categoryId = null,
                kind = "transfer",
                toAccountId = accountB,
            )

            val page =
                loadAll(
                    db.transactionDao().pagedByPeriod(accountA, categoryId = null, from = 0L, to = 10 * day),
                )

            assertEquals(3, page.size)
            assertTrue("null-category transfer must be included", page.any { it.categoryId == null })
        }

    @Test
    fun pagedByPeriod_nonnull_category_returns_only_that_category() =
        runTest {
            val food = insert(accountId = accountA, occurredAt = 5 * day, categoryId = categoryFood)
            insert(accountId = accountA, occurredAt = 6 * day, categoryId = categoryBills)
            insert(accountId = accountA, occurredAt = 7 * day, categoryId = null, kind = "transfer", toAccountId = accountB)

            val page =
                loadAll(
                    db.transactionDao().pagedByPeriod(accountA, categoryId = categoryFood, from = 0L, to = 10 * day),
                )

            assertEquals(listOf(food), page.map { it.id })
        }

    @Test
    fun pagedByPeriod_orders_by_occurred_at_desc_then_created_at_desc() =
        runTest {
            val older = insert(accountId = accountA, occurredAt = 2 * day, createdAt = 100L)
            // same occurred_at, different created_at -> created_at DESC decides order
            val sameDayEarlierCreated = insert(accountId = accountA, occurredAt = 5 * day, createdAt = 100L)
            val sameDayLaterCreated = insert(accountId = accountA, occurredAt = 5 * day, createdAt = 200L)

            val page =
                loadAll(
                    db.transactionDao().pagedByPeriod(accountA, categoryId = null, from = 0L, to = 10 * day),
                )

            assertEquals(
                listOf(sameDayLaterCreated, sameDayEarlierCreated, older),
                page.map { it.id },
            )
        }

    @Test
    fun pagedByPeriod_returns_empty_page_when_nothing_matches() =
        runTest {
            insert(accountId = accountA, occurredAt = 50 * day)

            val page =
                loadAll(
                    db.transactionDao().pagedByPeriod(accountA, categoryId = null, from = 0L, to = 10 * day),
                )

            assertTrue("expected empty page but got $page", page.isEmpty())
        }

    @Test
    fun restore_flips_is_deleted_back_to_zero_and_updates_updated_at() =
        runTest {
            val id = insert(accountId = accountA, occurredAt = 5 * day, isDeleted = true, createdAt = 1_000L)
            val before = db.transactionDao().findById(id)
            assertNotNull(before)
            assertTrue(before!!.isDeleted)

            db.transactionDao().restore(id, now = 9_999L)

            val after = db.transactionDao().findById(id)
            assertNotNull(after)
            assertFalse("restore must clear is_deleted", after!!.isDeleted)
            assertEquals(9_999L, after.updatedAt)
        }

    @Test
    fun restore_makes_the_row_visible_to_pagedByPeriod_again() =
        runTest {
            val id = insert(accountId = accountA, occurredAt = 5 * day, isDeleted = true)

            val beforeRestore =
                loadAll(
                    db.transactionDao().pagedByPeriod(accountA, categoryId = null, from = 0L, to = 10 * day),
                )
            assertTrue(beforeRestore.isEmpty())

            db.transactionDao().restore(id, now = 1L)

            val afterRestore =
                loadAll(
                    db.transactionDao().pagedByPeriod(accountA, categoryId = null, from = 0L, to = 10 * day),
                )
            assertEquals(listOf(id), afterRestore.map { it.id })
        }
}
