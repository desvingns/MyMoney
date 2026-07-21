package com.kshavrin.mymoney.core.database.journal

import com.kshavrin.mymoney.core.database.dao.CurrencyDao
import com.kshavrin.mymoney.core.database.entity.AccountEntity
import com.kshavrin.mymoney.core.database.entity.CategoryEntity
import com.kshavrin.mymoney.core.database.entity.CurrencyEntity
import com.kshavrin.mymoney.core.database.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationPayloadCodecTest {
    private val currencyDao =
        FakeCurrencyDao(
            listOf(
                CurrencyEntity(id = 3L, code = "GBP", symbol = "£", name = "Pound", decimalDigits = 2, isActive = true, sortOrder = 1),
                CurrencyEntity(id = 7L, code = "EUR", symbol = "€", name = "Euro", decimalDigits = 2, isActive = true, sortOrder = 2),
            ),
        )
    private val codec = OperationPayloadCodec(currencyDao)

    @Test
    fun `transaction snapshot round-trips money as strings and resolves currency to its portable code`() =
        runTest {
            val entity =
                TransactionEntity(
                    id = 11L,
                    uuid = "tx-uuid",
                    deviceId = "device-123",
                    kind = "transfer",
                    amount = 1234.56,
                    currencyId = 7L,
                    accountId = 3L,
                    categoryId = 4L,
                    note = "move",
                    occurredAt = 1_700_000_000_000L,
                    createdAt = 1_700_000_100_000L,
                    updatedAt = 1_700_000_200_000L,
                    isDeleted = false,
                    toAccountId = 8L,
                    toAmount = 1200.01,
                    exchangeRate = 0.9725,
                )

            val payload =
                codec.encodeTransaction(
                    entity = entity,
                    accountUuid = "account-uuid",
                    categoryUuid = "category-uuid",
                    toAccountUuid = "to-account-uuid",
                )
            val snapshot = codec.decodeTransaction(payload)

            assertTrue(payload.contains("\"amount\":\"1234.56\""))
            assertTrue(payload.contains("\"toAmount\":\"1200.01\""))
            assertTrue("payload must carry the portable currency code, not the local row id", payload.contains("\"currencyCode\":\"EUR\""))
            assertEquals("tx-uuid", snapshot.uuid)
            assertEquals("device-123", snapshot.deviceId)
            assertEquals("1234.56", snapshot.amount)
            assertEquals("1200.01", snapshot.toAmount)
            assertEquals("EUR", snapshot.currencyCode)
            assertEquals("account-uuid", snapshot.accountUuid)
            assertEquals("category-uuid", snapshot.categoryUuid)
            assertEquals("to-account-uuid", snapshot.toAccountUuid)
            assertEquals(0.9725, snapshot.exchangeRate)
        }

    @Test
    fun `category snapshot round-trips all persisted fields`() =
        runTest {
            val payload =
                codec.encodeCategory(
                    CategoryEntity(
                        id = 5L,
                        uuid = "category-uuid",
                        deviceId = "device-123",
                        name = "Food",
                        kind = "expense",
                        iconKey = "ic_cat_food",
                        colorHex = "#E07AAE",
                        textColor = "#FFFFFF",
                        sortOrder = 9,
                        isDefault = true,
                        isArchived = false,
                        createdAt = 1_700_000_000_000L,
                        updatedAt = 1_700_000_100_000L,
                    ),
                )

            val snapshot = codec.decodeCategory(payload)

            assertEquals("category-uuid", snapshot.uuid)
            assertEquals("device-123", snapshot.deviceId)
            assertEquals("Food", snapshot.name)
            assertEquals("expense", snapshot.kind)
            assertEquals("ic_cat_food", snapshot.iconKey)
            assertEquals("#E07AAE", snapshot.colorHex)
            assertEquals("#FFFFFF", snapshot.textColor)
            assertEquals(9, snapshot.sortOrder)
            assertEquals(true, snapshot.isDefault)
            assertEquals(false, snapshot.isArchived)
            assertEquals(1_700_000_000_000L, snapshot.createdAt)
            assertEquals(1_700_000_100_000L, snapshot.updatedAt)
        }

    @Test
    fun `account snapshot round-trips money as string and resolves currency to its portable code`() =
        runTest {
            val payload =
                codec.encodeAccount(
                    AccountEntity(
                        id = 7L,
                        uuid = "account-uuid",
                        deviceId = "device-123",
                        name = "Cash",
                        currencyId = 3L,
                        initialBalance = 1500.25,
                        type = "cash",
                        colorHex = "#7AC794",
                        iconKey = "ic_account_cash",
                        isDefault = false,
                        sortOrder = 4,
                        createdAt = 1_700_000_000_000L,
                        updatedAt = 1_700_000_100_000L,
                        isArchived = false,
                    ),
                )

            val snapshot = codec.decodeAccount(payload)

            assertTrue(payload.contains("\"initialBalance\":\"1500.25\""))
            assertTrue("payload must carry the portable currency code, not the local row id", payload.contains("\"currencyCode\":\"GBP\""))
            assertEquals("account-uuid", snapshot.uuid)
            assertEquals("device-123", snapshot.deviceId)
            assertEquals("Cash", snapshot.name)
            assertEquals("GBP", snapshot.currencyCode)
            assertEquals("1500.25", snapshot.initialBalance)
            assertEquals("cash", snapshot.type)
            assertEquals("#7AC794", snapshot.colorHex)
            assertEquals("ic_account_cash", snapshot.iconKey)
            assertEquals(false, snapshot.isDefault)
            assertEquals(4, snapshot.sortOrder)
            assertEquals(1_700_000_000_000L, snapshot.createdAt)
            assertEquals(1_700_000_100_000L, snapshot.updatedAt)
            assertEquals(false, snapshot.isArchived)
        }
}

private class FakeCurrencyDao(
    seed: List<CurrencyEntity>,
) : CurrencyDao {
    private val byId = seed.associateBy { it.id }.toMutableMap()

    override fun observeActive(): Flow<List<CurrencyEntity>> = flowOf(byId.values.filter { it.isActive })

    override fun observeAll(): Flow<List<CurrencyEntity>> = flowOf(byId.values.toList())

    override suspend fun findById(id: Long): CurrencyEntity? = byId[id]

    override suspend fun findByCode(code: String): CurrencyEntity? = byId.values.firstOrNull { it.code.equals(code, ignoreCase = true) }

    override suspend fun upsert(item: CurrencyEntity): Long {
        val id = item.id.takeIf { it != 0L } ?: (byId.keys.maxOrNull() ?: 0L) + 1
        byId[id] = item.copy(id = id)
        return id
    }

    override suspend fun upsertAll(items: List<CurrencyEntity>) {
        items.forEach { upsert(it) }
    }

    override suspend fun setActive(
        id: Long,
        active: Boolean,
    ) {
        byId[id]?.let { byId[id] = it.copy(isActive = active) }
    }

    override suspend fun activateCurrenciesWithLiveTransactions(): Int = 0
}
