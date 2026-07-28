package com.kshavrin.mymoney.core.network.shared

import com.kshavrin.mymoney.core.domain.sync.EntityKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SharedOperationDtoTest {

    private val createdAtStr = "2025-03-10T09:30:00Z"

    private fun minimalDto(entityKind: String = "transaction") = SharedOperationDto(
        id = "op-1",
        workspaceId = "ws-1",
        idempotencyKey = "idem-1",
        serverSequence = 1L,
        baseSequence = 0L,
        deviceId = "dev-1",
        entityKind = entityKind,
        entityId = "entity-1",
        payload = null,
        tombstone = false,
        createdAt = createdAtStr,
    )

    // --- toEntityKind() ---

    @Test
    fun `toEntityKind maps transaction string to Transaction`() {
        assertEquals(EntityKind.Transaction, "transaction".toEntityKind())
    }

    @Test
    fun `toEntityKind maps account string to Account`() {
        assertEquals(EntityKind.Account, "account".toEntityKind())
    }

    @Test
    fun `toEntityKind maps category string to Category`() {
        assertEquals(EntityKind.Category, "category".toEntityKind())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `toEntityKind throws IllegalArgumentException for unknown value`() {
        "wallet".toEntityKind()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `toEntityKind throws for empty string`() {
        "".toEntityKind()
    }

    // --- toRpcValue() round-trip ---

    @Test
    fun `toRpcValue maps Transaction to transaction string`() {
        assertEquals("transaction", EntityKind.Transaction.toRpcValue())
    }

    @Test
    fun `toRpcValue maps Account to account string`() {
        assertEquals("account", EntityKind.Account.toRpcValue())
    }

    @Test
    fun `toRpcValue maps Category to category string`() {
        assertEquals("category", EntityKind.Category.toRpcValue())
    }

    @Test
    fun `toRpcValue and toEntityKind are mutual inverses for all EntityKind values`() {
        for (kind in EntityKind.entries) {
            assertEquals(kind, kind.toRpcValue().toEntityKind())
        }
    }

    // --- SharedOperationDto.toDomain() ---

    @Test
    fun `toDomain maps all scalar fields from dto to domain operation`() {
        val dto = SharedOperationDto(
            id = "op-xyz",
            workspaceId = "ws-2",
            idempotencyKey = "idem-abc",
            serverSequence = 42L,
            baseSequence = 41L,
            deviceId = "dev-007",
            entityKind = "account",
            entityId = "acct-88",
            payload = null,
            tombstone = false,
            createdAt = createdAtStr,
        )

        val domain = dto.toDomain()

        assertEquals("op-xyz", domain.id)
        assertEquals("ws-2", domain.workspaceId)
        assertEquals("idem-abc", domain.idempotencyKey)
        assertEquals(42L, domain.serverSequence)
        assertEquals(41L, domain.baseSequence)
        assertEquals("dev-007", domain.deviceId)
        assertEquals(EntityKind.Account, domain.entityKind)
        assertEquals("acct-88", domain.entityId)
        assertNull(domain.payload)
        assertFalse(domain.tombstone)
        assertEquals(Instant.parse(createdAtStr), domain.createdAt)
    }

    @Test
    fun `toDomain maps tombstone true to domain tombstone true`() {
        val dto = minimalDto().copy(tombstone = true)

        assertTrue(dto.toDomain().tombstone)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `toDomain throws IllegalArgumentException when entity kind is unknown`() {
        minimalDto(entityKind = "unknown_kind").toDomain()
    }

    @Test
    fun `toDomain maps all three known entity kinds without throwing`() {
        for (kindStr in listOf("transaction", "account", "category")) {
            val domain = minimalDto(entityKind = kindStr).toDomain()
            assertEquals(kindStr.toEntityKind(), domain.entityKind)
        }
    }
}
