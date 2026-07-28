package com.kshavrin.mymoney.core.network.shared

import com.kshavrin.mymoney.core.domain.sync.ConflictStatus
import com.kshavrin.mymoney.core.domain.sync.EntityKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class SharedConflictDtoTest {

    private val createdAtStr = "2025-04-01T14:00:00Z"

    private fun stubOpDto(id: String = "op-1", entityKind: String = "transaction") = SharedOperationDto(
        id = id,
        workspaceId = "ws-1",
        idempotencyKey = "idem-$id",
        serverSequence = 1L,
        baseSequence = 0L,
        deviceId = "dev-1",
        entityKind = entityKind,
        entityId = "entity-1",
        payload = null,
        tombstone = false,
        createdAt = createdAtStr,
    )

    private fun minimalConflictDto(
        status: String = "pending",
        entityKind: String = "transaction",
        operationA: SharedOperationDto = stubOpDto("op-A"),
        operationB: SharedOperationDto = stubOpDto("op-B"),
    ) = SharedConflictDto(
        id = "conflict-1",
        workspaceId = "ws-1",
        entityKind = entityKind,
        entityId = "entity-1",
        operationA = operationA,
        operationB = operationB,
        authorAId = "user-A",
        authorBId = "user-B",
        status = status,
        createdAt = createdAtStr,
    )

    // --- ConflictStatus mapping ---

    @Test
    fun `toDomain maps pending status to ConflictStatus Pending`() {
        assertEquals(ConflictStatus.Pending, minimalConflictDto(status = "pending").toDomain().status)
    }

    @Test
    fun `toDomain maps resolved status to ConflictStatus Resolved`() {
        assertEquals(ConflictStatus.Resolved, minimalConflictDto(status = "resolved").toDomain().status)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `toDomain throws IllegalArgumentException for unknown status value`() {
        minimalConflictDto(status = "in_review").toDomain()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `toDomain throws for empty status string`() {
        minimalConflictDto(status = "").toDomain()
    }

    // --- scalar field mapping ---

    @Test
    fun `toDomain maps all scalar fields from dto to domain correctly`() {
        val dto = SharedConflictDto(
            id = "conflict-complete",
            workspaceId = "ws-complete",
            entityKind = "account",
            entityId = "account-99",
            operationA = stubOpDto("op-A"),
            operationB = stubOpDto("op-B"),
            authorAId = "author-alice",
            authorBId = "author-bob",
            status = "pending",
            resolverId = null,
            resolvedIntoId = null,
            createdAt = createdAtStr,
            resolvedAt = null,
        )

        val domain = dto.toDomain()

        assertEquals("conflict-complete", domain.id)
        assertEquals("ws-complete", domain.workspaceId)
        assertEquals(EntityKind.Account, domain.entityKind)
        assertEquals("account-99", domain.entityId)
        assertEquals("author-alice", domain.authorAId)
        assertEquals("author-bob", domain.authorBId)
        assertEquals(ConflictStatus.Pending, domain.status)
        assertNull(domain.resolverId)
        assertNull(domain.resolvedIntoId)
        assertNull(domain.resolvedAt)
        assertEquals(Instant.parse(createdAtStr), domain.createdAt)
    }

    @Test
    fun `toDomain maps resolved fields correctly when resolvedAt is present`() {
        val resolvedAt = "2025-04-02T10:00:00Z"
        val dto = SharedConflictDto(
            id = "conflict-2",
            workspaceId = "ws-1",
            entityKind = "category",
            entityId = "cat-1",
            operationA = stubOpDto("op-A"),
            operationB = stubOpDto("op-B"),
            authorAId = "user-A",
            authorBId = "user-B",
            status = "resolved",
            resolverId = "user-A",
            resolvedIntoId = "op-A",
            createdAt = createdAtStr,
            resolvedAt = resolvedAt,
        )

        val domain = dto.toDomain()

        assertEquals(ConflictStatus.Resolved, domain.status)
        assertEquals("user-A", domain.resolverId)
        assertEquals("op-A", domain.resolvedIntoId)
        assertEquals(Instant.parse(resolvedAt), domain.resolvedAt)
    }

    // --- nested operation mapping ---

    @Test
    fun `toDomain maps nested operationA and operationB to domain operations`() {
        val dto = minimalConflictDto(
            operationA = stubOpDto("op-first", entityKind = "transaction"),
            operationB = stubOpDto("op-second", entityKind = "account"),
        )

        val domain = dto.toDomain()

        assertEquals("op-first", domain.operationA.id)
        assertEquals(EntityKind.Transaction, domain.operationA.entityKind)
        assertEquals("op-second", domain.operationB.id)
        assertEquals(EntityKind.Account, domain.operationB.entityKind)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `toDomain throws when nested operationA has unknown entity kind`() {
        minimalConflictDto(
            operationA = stubOpDto("op-A", entityKind = "bad_kind"),
        ).toDomain()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `toDomain throws when conflict dto itself has unknown entity kind`() {
        minimalConflictDto(entityKind = "unknown_entity").toDomain()
    }
}
