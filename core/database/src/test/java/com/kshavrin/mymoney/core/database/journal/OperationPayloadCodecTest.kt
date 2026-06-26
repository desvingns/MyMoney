package com.kshavrin.mymoney.core.database.journal

import com.kshavrin.mymoney.core.database.entity.AccountEntity
import com.kshavrin.mymoney.core.database.entity.CategoryEntity
import com.kshavrin.mymoney.core.database.entity.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationPayloadCodecTest {
    private val codec = OperationPayloadCodec()

    @Test
    fun `transaction snapshot round-trips money as strings and keeps uuid references`() {
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
        assertEquals("tx-uuid", snapshot.uuid)
        assertEquals("device-123", snapshot.deviceId)
        assertEquals("1234.56", snapshot.amount)
        assertEquals("1200.01", snapshot.toAmount)
        assertEquals("account-uuid", snapshot.accountUuid)
        assertEquals("category-uuid", snapshot.categoryUuid)
        assertEquals("to-account-uuid", snapshot.toAccountUuid)
        assertEquals(0.9725, snapshot.exchangeRate)
    }

    @Test
    fun `category snapshot round-trips all persisted fields`() {
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
    fun `account snapshot round-trips money as string`() {
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
        assertEquals("account-uuid", snapshot.uuid)
        assertEquals("device-123", snapshot.deviceId)
        assertEquals("Cash", snapshot.name)
        assertEquals(3L, snapshot.currencyId)
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
