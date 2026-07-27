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
import java.util.UUID

/**
 * Contract tests for [TransactionDao.getTransfers].
 *
 * Acceptance scenarios:
 * - Returns only kind='transfer' rows (expenses/incomes excluded).
 * - Filters by period (occurred_at between from..to).
 * - Filters by accountId matching source OR recipient.
 * - Null accountId returns all transfers in period.
 * - Soft-deleted transfers are excluded.
 * - Returns fromAccountName and toAccountName via JOIN.
 */
@RunWith(AndroidJUnit4::class)
class TransactionDaoGetTransfersTest {
    private lateinit var db: MoneyDatabase

    private var currencyId: Long = 0L
    private var accountCash: Long = 0L
    private var accountCard: Long = 0L
    private var accountSavings: Long = 0L
    private var categoryExpense: Long = 0L
    private var transactionUuidCounter = 0

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
                    CurrencyEntity(code = "RUB", symbol = "₽", name = "Russian Ruble", decimalDigits = 2, isActive = true, sortOrder = 0),
                )
            accountCash = db.accountDao().upsert(account("Наличные"))
            accountCard = db.accountDao().upsert(account("Карта"))
            accountSavings = db.accountDao().upsert(account("Накопления"))
            categoryExpense = db.categoryDao().upsert(category("Food"))
        }

    @After
    fun tearDown() {
        db.close()
    }

    private fun account(name: String) =
        AccountEntity(
            uuid = fixtureUuid("account", name),
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
            uuid = fixtureUuid("category", name),
            name = name,
            kind = "expense",
            iconKey = "ic_cat_food",
            colorHex = "#E07AAE",
            textColor = "#FFFFFF",
            sortOrder = 0,
            isDefault = false,
            isArchived = false,
            createdAt = 0L,
        )

    private suspend fun insertTransfer(
        fromAccountId: Long,
        toAccountId: Long,
        amount: Double = 500.0,
        occurredAt: Long = 5 * day,
        isDeleted: Boolean = false,
        note: String? = null,
    ): Long =
        db.transactionDao().upsert(
            TransactionEntity(
                uuid = fixtureUuid("transaction", transactionUuidCounter++),
                kind = "transfer",
                amount = amount,
                currencyId = currencyId,
                accountId = fromAccountId,
                categoryId = null,
                note = note,
                occurredAt = occurredAt,
                createdAt = occurredAt,
                updatedAt = occurredAt,
                isDeleted = isDeleted,
                toAccountId = toAccountId,
                toAmount = null,
                exchangeRate = null,
            ),
        )

    private suspend fun insertExpense(
        accountId: Long,
        amount: Double = 100.0,
        occurredAt: Long = 5 * day,
    ): Long =
        db.transactionDao().upsert(
            TransactionEntity(
                uuid = fixtureUuid("transaction", transactionUuidCounter++),
                kind = "expense",
                amount = amount,
                currencyId = currencyId,
                accountId = accountId,
                categoryId = categoryExpense,
                note = null,
                occurredAt = occurredAt,
                createdAt = occurredAt,
                updatedAt = occurredAt,
                isDeleted = false,
                toAccountId = null,
                toAmount = null,
                exchangeRate = null,
            ),
        )

    private fun fixtureUuid(
        type: String,
        key: Any,
    ): String =
        UUID.nameUUIDFromBytes("$type:$key".toByteArray(Charsets.UTF_8)).toString()

    @Test
    fun getTransfers_returns_only_kind_transfer_rows() =
        runTest {
            insertTransfer(fromAccountId = accountCash, toAccountId = accountCard)
            insertExpense(accountId = accountCash)

            val rows = db.transactionDao().getTransfers(accountId = null, from = 0L, to = 10 * day)

            assertEquals(1, rows.size)
            assertEquals(500.0, rows.single().amount, 0.0001)
        }

    @Test
    fun getTransfers_excludes_soft_deleted_transfers() =
        runTest {
            insertTransfer(fromAccountId = accountCash, toAccountId = accountCard, isDeleted = true)
            insertTransfer(fromAccountId = accountCash, toAccountId = accountSavings, isDeleted = false)

            val rows = db.transactionDao().getTransfers(accountId = null, from = 0L, to = 10 * day)

            assertEquals(1, rows.size)
            assertEquals("Накопления", rows.single().toAccountName)
        }

    @Test
    fun getTransfers_filters_by_period() =
        runTest {
            insertTransfer(fromAccountId = accountCash, toAccountId = accountCard, occurredAt = 2 * day)
            insertTransfer(fromAccountId = accountCash, toAccountId = accountSavings, occurredAt = 8 * day)

            val rowsNarrow = db.transactionDao().getTransfers(accountId = null, from = 3 * day, to = 10 * day)
            val rowsWide = db.transactionDao().getTransfers(accountId = null, from = 0L, to = 10 * day)

            assertEquals(1, rowsNarrow.size)
            assertEquals(2, rowsWide.size)
        }

    @Test
    fun getTransfers_with_null_accountId_returns_all_transfers_in_period() =
        runTest {
            insertTransfer(fromAccountId = accountCash, toAccountId = accountCard)
            insertTransfer(fromAccountId = accountCard, toAccountId = accountSavings)

            val rows = db.transactionDao().getTransfers(accountId = null, from = 0L, to = 10 * day)

            assertEquals(2, rows.size)
        }

    @Test
    fun getTransfers_with_accountId_matches_source_account() =
        runTest {
            insertTransfer(fromAccountId = accountCash, toAccountId = accountCard)
            insertTransfer(fromAccountId = accountSavings, toAccountId = accountCard)

            val rows = db.transactionDao().getTransfers(accountId = accountCash, from = 0L, to = 10 * day)

            assertEquals(1, rows.size)
            assertEquals("Наличные", rows.single().fromAccountName)
        }

    @Test
    fun getTransfers_with_accountId_matches_recipient_account() =
        runTest {
            insertTransfer(fromAccountId = accountCash, toAccountId = accountCard)
            insertTransfer(fromAccountId = accountSavings, toAccountId = accountCard)

            val rows = db.transactionDao().getTransfers(accountId = accountCard, from = 0L, to = 10 * day)

            assertEquals(2, rows.size)
            assertTrue(rows.all { it.toAccountName == "Карта" })
        }

    @Test
    fun getTransfers_populates_fromAccountName_and_toAccountName_via_join() =
        runTest {
            insertTransfer(fromAccountId = accountCash, toAccountId = accountCard, amount = 1000.0)

            val rows = db.transactionDao().getTransfers(accountId = null, from = 0L, to = 10 * day)

            val row = rows.single()
            assertEquals("Наличные", row.fromAccountName)
            assertEquals("Карта", row.toAccountName)
            assertEquals(1000.0, row.amount, 0.0001)
            assertEquals(currencyId, row.currencyId)
        }

    @Test
    fun getTransfers_returns_empty_list_when_no_transfers_in_period() =
        runTest {
            insertTransfer(fromAccountId = accountCash, toAccountId = accountCard, occurredAt = 1 * day)

            val rows = db.transactionDao().getTransfers(accountId = null, from = 5 * day, to = 10 * day)

            assertTrue(rows.isEmpty())
        }

    @Test
    fun getTransfers_orders_by_occurred_at_desc() =
        runTest {
            val id1 = insertTransfer(fromAccountId = accountCash, toAccountId = accountCard, amount = 100.0, occurredAt = 1 * day)
            val id2 = insertTransfer(fromAccountId = accountCash, toAccountId = accountCard, amount = 200.0, occurredAt = 3 * day)
            val id3 = insertTransfer(fromAccountId = accountCash, toAccountId = accountCard, amount = 150.0, occurredAt = 2 * day)

            val rows = db.transactionDao().getTransfers(accountId = null, from = 0L, to = 10 * day)

            assertEquals(listOf(id2, id3, id1), rows.map { it.id })
        }

    @Test
    fun getTransfers_returns_non_null_note_when_transfer_has_note() =
        runTest {
            insertTransfer(
                fromAccountId = accountCash,
                toAccountId = accountCard,
                note = "зарплата",
            )

            val rows = db.transactionDao().getTransfers(accountId = null, from = 0L, to = 10 * day)

            assertEquals(1, rows.size)
            assertEquals("зарплата", rows.single().note)
        }

    @Test
    fun getTransfers_returns_null_note_when_transfer_note_is_null() =
        runTest {
            insertTransfer(
                fromAccountId = accountCash,
                toAccountId = accountCard,
                note = null,
            )

            val rows = db.transactionDao().getTransfers(accountId = null, from = 0L, to = 10 * day)

            assertEquals(1, rows.size)
            assertEquals(null, rows.single().note)
        }
}
