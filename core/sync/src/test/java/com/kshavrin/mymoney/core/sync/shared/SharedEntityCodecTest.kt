package com.kshavrin.mymoney.core.sync.shared

import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class SharedEntityCodecTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val codec = SharedEntityCodec(json)

    // ── Transaction round-trips ────────────────────────────────────────────

    @Test
    fun `encodeTransaction and decodeTransaction round-trip all scalar fields`() {
        val tx = sampleTransaction()
        val payload = codec.encodeTransaction(
            tx, TX_UUID, CURRENCY_CODE, ACCOUNT_UUID, CATEGORY_UUID, null,
        )
        val decoded = codec.decodeTransaction(payload)

        assertEquals(tx.id, decoded.id)
        assertEquals(tx.kind, decoded.kind)
        assertEquals(0, tx.amount.compareTo(decoded.amount))
        assertEquals(tx.note, decoded.note)
        assertEquals(tx.occurredAt, decoded.occurredAt)
        assertEquals(tx.createdAt, decoded.createdAt)
        assertEquals(tx.updatedAt, decoded.updatedAt)
        assertEquals(tx.isDeleted, decoded.isDeleted)
    }

    @Test
    fun `decodeTransaction yields placeholder currencyId zero because caller must remap`() {
        val payload = codec.encodeTransaction(
            sampleTransaction(), TX_UUID, CURRENCY_CODE, ACCOUNT_UUID, null, null,
        )
        assertEquals(0L, codec.decodeTransaction(payload).currencyId)
    }

    @Test
    fun `decodeTransactionRefs extracts currencyCode accountUuid categoryUuid and toAccountUuid`() {
        val tx = sampleTransaction()
        val toAccountUuid = "to-acct-uuid-99"
        val payload = codec.encodeTransaction(
            tx, TX_UUID, CURRENCY_CODE, ACCOUNT_UUID, CATEGORY_UUID, toAccountUuid,
        )
        val refs = codec.decodeTransactionRefs(payload)

        assertEquals(CURRENCY_CODE, refs.currencyCode)
        assertEquals(ACCOUNT_UUID, refs.accountUuid)
        assertEquals(CATEGORY_UUID, refs.categoryUuid)
        assertEquals(toAccountUuid, refs.toAccountUuid)
    }

    @Test
    fun `decodeTransactionRefs returns null for absent optional uuids`() {
        val tx = sampleTransaction().copy(categoryId = null, toAccountId = null)
        val payload = codec.encodeTransaction(
            tx, TX_UUID, CURRENCY_CODE, ACCOUNT_UUID, null, null,
        )
        val refs = codec.decodeTransactionRefs(payload)

        assertNull(refs.categoryUuid)
        assertNull(refs.toAccountUuid)
    }

    @Test
    fun `amount is encoded as plain decimal string not as lossy IEEE double`() {
        val precise = BigDecimal("123456789.12345")
        val tx = sampleTransaction().copy(amount = precise)
        val payload = codec.encodeTransaction(tx, TX_UUID, CURRENCY_CODE, ACCOUNT_UUID, null, null)

        // The raw JSON must contain the exact decimal string, not a floating-point approximation.
        assert(payload.contains("123456789.12345")) {
            "Expected exact decimal '123456789.12345' in payload, got: $payload"
        }
        assertEquals(0, precise.compareTo(codec.decodeTransaction(payload).amount))
    }

    @Test
    fun `toAmount round-trips when present`() {
        val tx = sampleTransaction().copy(toAmount = BigDecimal("200.50"), toAccountId = 99L)
        val payload = codec.encodeTransaction(
            tx, TX_UUID, CURRENCY_CODE, ACCOUNT_UUID, null, "to-uuid",
        )
        val decoded = codec.decodeTransaction(payload)
        assertEquals(0, BigDecimal("200.50").compareTo(decoded.toAmount))
    }

    @Test
    fun `exchangeRate round-trips when present`() {
        val tx = sampleTransaction().copy(exchangeRate = 1.23456)
        val payload = codec.encodeTransaction(
            tx, TX_UUID, CURRENCY_CODE, ACCOUNT_UUID, null, null,
        )
        val decoded = codec.decodeTransaction(payload)
        assertEquals(1.23456, decoded.exchangeRate!!, 0.00001)
    }

    @Test
    fun `decodeTransaction defaults isDeleted to false when key absent`() {
        // Payload emitted by an older client that didn't include isDeleted
        val payload = """{"id":1,"kind":"Expense","amount":"10.00","currencyCode":"USD",""" +
            """"accountUuid":"a","accountId":1,"occurredAt":1000000,"createdAt":1000000,"updatedAt":1000000}"""
        assertEquals(false, codec.decodeTransaction(payload).isDeleted)
    }

    // ── Account round-trips ────────────────────────────────────────────────

    @Test
    fun `encodeAccount and decodeAccount round-trip all fields`() {
        val account = sampleAccount()
        val payload = codec.encodeAccount(account, TX_UUID, CURRENCY_CODE)
        val decoded = codec.decodeAccount(payload)

        assertEquals(account.name, decoded.name)
        assertEquals(0, account.initialBalance.compareTo(decoded.initialBalance))
        assertEquals(account.type, decoded.type)
        assertEquals(account.colorHex, decoded.colorHex)
        assertEquals(account.iconKey, decoded.iconKey)
        assertEquals(account.isDefault, decoded.isDefault)
        assertEquals(account.sortOrder, decoded.sortOrder)
        assertEquals(account.createdAt, decoded.createdAt)
        assertEquals(account.updatedAt, decoded.updatedAt)
        assertEquals(account.isArchived, decoded.isArchived)
    }

    @Test
    fun `decodeAccount yields placeholder currencyId zero because caller must remap`() {
        val payload = codec.encodeAccount(sampleAccount(), TX_UUID, CURRENCY_CODE)
        assertEquals(0L, codec.decodeAccount(payload).currencyId)
    }

    @Test
    fun `decodeAccountCurrencyCode extracts the ISO code`() {
        val payload = codec.encodeAccount(sampleAccount(), TX_UUID, CURRENCY_CODE)
        assertEquals(CURRENCY_CODE, codec.decodeAccountCurrencyCode(payload))
    }

    @Test
    fun `canonical currency payload round-trips with a portable account reference`() {
        val custom =
            Currency(
                id = 0L,
                code = "XYZ",
                symbol = "¤",
                name = "Test currency",
                decimalDigits = 2,
                isActive = true,
                sortOrder = 7,
            )
        val reference = codec.decodeAccountCurrencyReference(codec.encodeAccount(sampleAccount(), TX_UUID, custom))

        assertEquals("XYZ", reference.code)
        assertEquals(custom, reference.currency)
    }

    // ── Category round-trips ───────────────────────────────────────────────

    @Test
    fun `encodeCategory and decodeCategory round-trip all fields`() {
        val category = sampleCategory()
        val payload = codec.encodeCategory(category, TX_UUID)
        val decoded = codec.decodeCategory(payload)

        assertEquals(category.name, decoded.name)
        assertEquals(category.kind, decoded.kind)
        assertEquals(category.iconKey, decoded.iconKey)
        assertEquals(category.colorHex, decoded.colorHex)
        assertEquals(category.textColor, decoded.textColor)
        assertEquals(category.sortOrder, decoded.sortOrder)
        assertEquals(category.isDefault, decoded.isDefault)
        assertEquals(category.isArchived, decoded.isArchived)
        assertEquals(category.createdAt, decoded.createdAt)
    }

    @Test
    fun `encodeCategory preserves archived flag true`() {
        val category = sampleCategory().copy(isArchived = true)
        val payload = codec.encodeCategory(category, TX_UUID)
        assertEquals(true, codec.decodeCategory(payload).isArchived)
    }

    @Test
    fun `canonicalPayload is independent of top-level key order`() {
        val first = """{"z":3,"a":1,"middle":"x"}"""
        val second = """{"middle":"x","z":3,"a":1}"""

        assertEquals("""{"a":1,"middle":"x","z":3}""", codec.canonicalPayload(first))
        assertEquals(codec.canonicalPayload(first), codec.canonicalPayload(second))
    }

    @Test
    fun `canonicalPayload recursively sorts nested object keys`() {
        val first = """{"top":"v","outer":{"z":{"b":2,"a":1},"a":{"y":9,"x":8}}}"""
        val second = """{"outer":{"a":{"x":8,"y":9},"z":{"a":1,"b":2}},"top":"v"}"""

        assertEquals(
            """{"outer":{"a":{"x":8,"y":9},"z":{"a":1,"b":2}},"top":"v"}""",
            codec.canonicalPayload(first),
        )
        assertEquals(codec.canonicalPayload(first), codec.canonicalPayload(second))
    }

    @Test
    fun `canonicalPayload preserves array element order while canonicalizing each element`() {
        val ordered = """{"items":[{"z":3,"a":1},{"b":2,"a":4}]}"""
        val reversed = """{"items":[{"b":2,"a":4},{"z":3,"a":1}]}"""

        assertEquals(
            """{"items":[{"a":1,"z":3},{"a":4,"b":2}]}""",
            codec.canonicalPayload(ordered),
        )
        assertNotEquals(codec.canonicalPayload(ordered), codec.canonicalPayload(reversed))
    }

    @Test
    fun `canonicalPayload removes local id fields recursively`() {
        val payload = """{
            "id":1,
            "currencyId":2,
            "accountId":3,
            "categoryId":4,
            "toAccountId":5,
            "name":"kept",
            "nested":{"id":10,"categoryId":40,"keep":"yes"},
            "items":[{"accountId":30,"toAccountId":50,"value":"v"}]
        }"""

        assertEquals(
            """{"items":[{"value":"v"}],"name":"kept","nested":{"keep":"yes"}}""",
            codec.canonicalPayload(payload),
        )
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun sampleTransaction() = Transaction(
        id = 42L,
        kind = TransactionKind.Expense,
        amount = BigDecimal("99.99"),
        currencyId = 5L,
        accountId = 10L,
        categoryId = 20L,
        note = "lunch",
        occurredAt = Instant.ofEpochMilli(1_700_000_000_000L),
        createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
        updatedAt = Instant.ofEpochMilli(1_700_000_000_001L),
        isDeleted = false,
        toAccountId = null,
        toAmount = null,
        exchangeRate = null,
    )

    private fun sampleAccount() = Account(
        id = 7L,
        name = "Cash",
        currencyId = 5L,
        initialBalance = BigDecimal("500.00"),
        type = AccountType.Cash,
        colorHex = "#4CAF50",
        iconKey = "ic_cash",
        isDefault = true,
        sortOrder = 0,
        createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
        updatedAt = Instant.ofEpochMilli(1_700_000_000_001L),
        isArchived = false,
    )

    private fun sampleCategory() = Category(
        id = 3L,
        name = "Food",
        kind = CategoryKind.Expense,
        iconKey = "ic_food",
        colorHex = "#FF5722",
        textColor = "#FFFFFF",
        sortOrder = 1,
        isDefault = true,
        isArchived = false,
        createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
    )

    private companion object {
        const val TX_UUID = "test-uuid-1234"
        const val CURRENCY_CODE = "USD"
        const val ACCOUNT_UUID = "account-uuid-abc"
        const val CATEGORY_UUID = "category-uuid-xyz"
    }
}
