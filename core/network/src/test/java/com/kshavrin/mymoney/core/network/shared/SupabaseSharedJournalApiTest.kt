package com.kshavrin.mymoney.core.network.shared

import com.kshavrin.mymoney.core.domain.sync.ConflictStatus
import com.kshavrin.mymoney.core.domain.sync.EntityKind
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SupabaseSharedJournalApiTest {
    private class FakeSharedJournalRpc : SharedJournalRpc {
        var pushOperationResult: Result<SharedOperationDto> = defaultFailure()
        var pullOperationsResult: Result<List<SharedOperationDto>> = defaultFailure()
        var listPendingConflictsResult: Result<List<SharedConflictDto>> = defaultFailure()
        var resolveConflictResult: Result<SharedOperationDto> = defaultFailure()

        var lastPushWorkspaceId: String? = null
        var lastPushIdempotencyKey: String? = null
        var lastPushBaseSequence: Long? = null
        var lastPushDeviceId: String? = null
        var lastPushEntityKind: String? = null
        var lastPushEntityId: String? = null
        var lastPushPayload: JsonElement? = null
        var lastPushTombstone: Boolean? = null
        var pushCallCount: Int = 0

        var lastPullWorkspaceId: String? = null
        var lastPullAfterSequence: Long? = null
        var lastPullLimit: Int? = null

        var lastListConflictsWorkspaceId: String? = null

        var lastResolveConflictId: String? = null
        var lastResolveWinnerOperationId: String? = null

        override suspend fun pushOperation(
            workspaceId: String,
            idempotencyKey: String,
            baseSequence: Long,
            deviceId: String,
            entityKind: String,
            entityId: String,
            payload: JsonElement?,
            tombstone: Boolean,
        ): Result<SharedOperationDto> {
            pushCallCount++
            lastPushWorkspaceId = workspaceId
            lastPushIdempotencyKey = idempotencyKey
            lastPushBaseSequence = baseSequence
            lastPushDeviceId = deviceId
            lastPushEntityKind = entityKind
            lastPushEntityId = entityId
            lastPushPayload = payload
            lastPushTombstone = tombstone
            return pushOperationResult
        }

        override suspend fun pullOperations(
            workspaceId: String,
            afterSequence: Long,
            limit: Int,
        ): Result<List<SharedOperationDto>> {
            lastPullWorkspaceId = workspaceId
            lastPullAfterSequence = afterSequence
            lastPullLimit = limit
            return pullOperationsResult
        }

        override suspend fun listPendingConflicts(workspaceId: String): Result<List<SharedConflictDto>> {
            lastListConflictsWorkspaceId = workspaceId
            return listPendingConflictsResult
        }

        override suspend fun resolveConflict(
            conflictId: String,
            winnerOperationId: String,
        ): Result<SharedOperationDto> {
            lastResolveConflictId = conflictId
            lastResolveWinnerOperationId = winnerOperationId
            return resolveConflictResult
        }

        private companion object {
            fun <T> defaultFailure(): Result<T> = Result.failure(NotImplementedError("stub not set"))
        }
    }

    private val fakeRpc = FakeSharedJournalRpc()
    private val api = SupabaseSharedJournalApi(rpc = fakeRpc, json = Json)

    private val createdAtStr = "2025-01-15T12:00:00Z"
    private val createdAt: Instant = Instant.parse(createdAtStr)

    private fun stubOpDto(
        id: String = "op-1",
        workspaceId: String = "ws-1",
        idempotencyKey: String = "idem-1",
        serverSequence: Long = 1L,
        baseSequence: Long = 0L,
        deviceId: String = "dev-1",
        entityKind: String = "transaction",
        entityId: String = "entity-1",
        payload: JsonElement? = null,
        tombstone: Boolean = false,
    ) = SharedOperationDto(
        id = id,
        workspaceId = workspaceId,
        idempotencyKey = idempotencyKey,
        serverSequence = serverSequence,
        baseSequence = baseSequence,
        deviceId = deviceId,
        entityKind = entityKind,
        entityId = entityId,
        payload = payload,
        tombstone = tombstone,
        createdAt = createdAtStr,
    )

    private fun stubConflictDto(
        id: String = "conflict-1",
        workspaceId: String = "ws-1",
        entityKind: String = "transaction",
        entityId: String = "entity-1",
        operationA: SharedOperationDto = stubOpDto(id = "op-A"),
        operationB: SharedOperationDto = stubOpDto(id = "op-B"),
        authorAId: String = "user-A",
        authorBId: String = "user-B",
        status: String = "pending",
        resolverId: String? = null,
        resolvedIntoId: String? = null,
        resolvedAt: String? = null,
    ) = SharedConflictDto(
        id = id,
        workspaceId = workspaceId,
        entityKind = entityKind,
        entityId = entityId,
        operationA = operationA,
        operationB = operationB,
        authorAId = authorAId,
        authorBId = authorBId,
        status = status,
        resolverId = resolverId,
        resolvedIntoId = resolvedIntoId,
        createdAt = createdAtStr,
        resolvedAt = resolvedAt,
    )

    // --- push ---

    @Test
    fun `push returns same domain operation on idempotent retry`() =
        runTest {
            fakeRpc.pushOperationResult = Result.success(stubOpDto(idempotencyKey = "idem-repeat"))

            val first =
                api
                    .push(
                        workspaceId = "ws-1",
                        idempotencyKey = "idem-repeat",
                        baseSequence = 0L,
                        deviceId = "dev-1",
                        entityKind = EntityKind.Transaction,
                        entityId = "entity-1",
                        payload = null,
                        tombstone = false,
                    ).getOrThrow()

            val second =
                api
                    .push(
                        workspaceId = "ws-1",
                        idempotencyKey = "idem-repeat",
                        baseSequence = 0L,
                        deviceId = "dev-1",
                        entityKind = EntityKind.Transaction,
                        entityId = "entity-1",
                        payload = null,
                        tombstone = false,
                    ).getOrThrow()

            assertEquals(first, second)
            assertEquals(2, fakeRpc.pushCallCount)
        }

    @Test
    fun `push maps dto fields to domain operation correctly`() =
        runTest {
            val dto =
                stubOpDto(
                    id = "op-abc",
                    workspaceId = "ws-2",
                    idempotencyKey = "idem-xyz",
                    serverSequence = 42L,
                    baseSequence = 41L,
                    deviceId = "dev-007",
                    entityKind = "account",
                    entityId = "acct-99",
                    tombstone = true,
                )
            fakeRpc.pushOperationResult = Result.success(dto)

            val result =
                api
                    .push(
                        workspaceId = "ws-2",
                        idempotencyKey = "idem-xyz",
                        baseSequence = 41L,
                        deviceId = "dev-007",
                        entityKind = EntityKind.Account,
                        entityId = "acct-99",
                        payload = null,
                        tombstone = true,
                    ).getOrThrow()

            assertEquals("op-abc", result.id)
            assertEquals("ws-2", result.workspaceId)
            assertEquals("idem-xyz", result.idempotencyKey)
            assertEquals(42L, result.serverSequence)
            assertEquals(41L, result.baseSequence)
            assertEquals("dev-007", result.deviceId)
            assertEquals(EntityKind.Account, result.entityKind)
            assertEquals("acct-99", result.entityId)
            assertTrue(result.tombstone)
            assertEquals(createdAt, result.createdAt)
        }

    @Test
    fun `push converts entity kind to correct rpc string`() =
        runTest {
            fakeRpc.pushOperationResult = Result.success(stubOpDto(entityKind = "category"))

            api.push(
                workspaceId = "ws-1",
                idempotencyKey = "idem-1",
                baseSequence = 0L,
                deviceId = "dev-1",
                entityKind = EntityKind.Category,
                entityId = "cat-1",
                payload = null,
                tombstone = false,
            )

            assertEquals("category", fakeRpc.lastPushEntityKind)
        }

    @Test
    fun `push parses json string payload and passes it to rpc`() =
        runTest {
            fakeRpc.pushOperationResult = Result.success(stubOpDto())

            api.push(
                workspaceId = "ws-1",
                idempotencyKey = "idem-1",
                baseSequence = 0L,
                deviceId = "dev-1",
                entityKind = EntityKind.Transaction,
                entityId = "entity-1",
                payload = """{"amount":100}""",
                tombstone = false,
            )

            assertNotNull(fakeRpc.lastPushPayload)
        }

    @Test
    fun `push with null payload passes null to rpc`() =
        runTest {
            fakeRpc.pushOperationResult = Result.success(stubOpDto())

            api.push(
                workspaceId = "ws-1",
                idempotencyKey = "idem-1",
                baseSequence = 0L,
                deviceId = "dev-1",
                entityKind = EntityKind.Transaction,
                entityId = "entity-1",
                payload = null,
                tombstone = false,
            )

            assertNull(fakeRpc.lastPushPayload)
        }

    @Test
    fun `push propagates rpc failure as Result failure`() =
        runTest {
            val error = RuntimeException("push failed")
            fakeRpc.pushOperationResult = Result.failure(error)

            val result =
                api.push(
                    workspaceId = "ws-1",
                    idempotencyKey = "idem-1",
                    baseSequence = 0L,
                    deviceId = "dev-1",
                    entityKind = EntityKind.Transaction,
                    entityId = "entity-1",
                    payload = null,
                    tombstone = false,
                )

            assertTrue(result.isFailure)
            assertEquals(error, result.exceptionOrNull())
        }

    // --- pull ---

    @Test
    fun `pull maps dto list to domain operations preserving order`() =
        runTest {
            val dtos =
                listOf(
                    stubOpDto(id = "op-1", serverSequence = 1L, entityKind = "transaction"),
                    stubOpDto(id = "op-2", serverSequence = 2L, entityKind = "account"),
                    stubOpDto(id = "op-3", serverSequence = 3L, entityKind = "category"),
                )
            fakeRpc.pullOperationsResult = Result.success(dtos)

            val result = api.pull(workspaceId = "ws-1", afterSequence = 0L).getOrThrow()

            assertEquals(3, result.size)
            assertEquals("op-1", result[0].id)
            assertEquals(EntityKind.Transaction, result[0].entityKind)
            assertEquals("op-2", result[1].id)
            assertEquals(EntityKind.Account, result[1].entityKind)
            assertEquals("op-3", result[2].id)
            assertEquals(EntityKind.Category, result[2].entityKind)
        }

    @Test
    fun `pull passes workspace id after sequence and limit to rpc`() =
        runTest {
            fakeRpc.pullOperationsResult = Result.success(emptyList())

            api.pull(workspaceId = "ws-xyz", afterSequence = 100L, limit = 50)

            assertEquals("ws-xyz", fakeRpc.lastPullWorkspaceId)
            assertEquals(100L, fakeRpc.lastPullAfterSequence)
            assertEquals(50, fakeRpc.lastPullLimit)
        }

    @Test
    fun `pull returns failure when rpc fails`() =
        runTest {
            val error = RuntimeException("network timeout")
            fakeRpc.pullOperationsResult = Result.failure(error)

            val result = api.pull(workspaceId = "ws-1", afterSequence = 0L)

            assertTrue(result.isFailure)
            assertEquals(error, result.exceptionOrNull())
        }

    @Test
    fun `pull returns failure when dto contains unknown entity kind`() =
        runTest {
            fakeRpc.pullOperationsResult = Result.success(listOf(stubOpDto(entityKind = "unknown_entity_kind")))

            val result = api.pull(workspaceId = "ws-1", afterSequence = 0L)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        }

    // --- listPendingConflicts ---

    @Test
    fun `listPendingConflicts maps pending conflict dto to domain correctly`() =
        runTest {
            val dto =
                stubConflictDto(
                    id = "conflict-42",
                    entityKind = "account",
                    entityId = "acct-1",
                    authorAId = "user-alice",
                    authorBId = "user-bob",
                    status = "pending",
                )
            fakeRpc.listPendingConflictsResult = Result.success(listOf(dto))

            val result = api.listPendingConflicts("ws-1").getOrThrow()

            assertEquals(1, result.size)
            val conflict = result[0]
            assertEquals("conflict-42", conflict.id)
            assertEquals(EntityKind.Account, conflict.entityKind)
            assertEquals("acct-1", conflict.entityId)
            assertEquals("user-alice", conflict.authorAId)
            assertEquals("user-bob", conflict.authorBId)
            assertEquals(ConflictStatus.Pending, conflict.status)
            assertNull(conflict.resolverId)
            assertNull(conflict.resolvedAt)
            assertEquals("op-A", conflict.operationA.id)
            assertEquals("op-B", conflict.operationB.id)
        }

    @Test
    fun `listPendingConflicts maps resolved conflict with resolver and resolution time`() =
        runTest {
            val resolvedAt = "2025-02-01T08:00:00Z"
            val dto =
                stubConflictDto(
                    status = "resolved",
                    resolverId = "user-alice",
                    resolvedIntoId = "op-winner",
                    resolvedAt = resolvedAt,
                )
            fakeRpc.listPendingConflictsResult = Result.success(listOf(dto))

            val conflict = api.listPendingConflicts("ws-1").getOrThrow()[0]

            assertEquals(ConflictStatus.Resolved, conflict.status)
            assertEquals("user-alice", conflict.resolverId)
            assertEquals("op-winner", conflict.resolvedIntoId)
            assertEquals(Instant.parse(resolvedAt), conflict.resolvedAt)
        }

    @Test
    fun `listPendingConflicts passes workspace id to rpc`() =
        runTest {
            fakeRpc.listPendingConflictsResult = Result.success(emptyList())

            api.listPendingConflicts("ws-test")

            assertEquals("ws-test", fakeRpc.lastListConflictsWorkspaceId)
        }

    @Test
    fun `listPendingConflicts returns failure when rpc fails`() =
        runTest {
            val error = RuntimeException("rpc error")
            fakeRpc.listPendingConflictsResult = Result.failure(error)

            val result = api.listPendingConflicts("ws-1")

            assertTrue(result.isFailure)
            assertEquals(error, result.exceptionOrNull())
        }

    @Test
    fun `listPendingConflicts returns failure when conflict dto has unknown entity kind`() =
        runTest {
            fakeRpc.listPendingConflictsResult = Result.success(listOf(stubConflictDto(entityKind = "unknown_kind")))

            val result = api.listPendingConflicts("ws-1")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        }

    @Test
    fun `listPendingConflicts returns failure when conflict dto has unknown status`() =
        runTest {
            fakeRpc.listPendingConflictsResult = Result.success(listOf(stubConflictDto(status = "in_progress")))

            val result = api.listPendingConflicts("ws-1")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        }

    // --- resolveConflict ---

    @Test
    fun `resolveConflict maps superseding operation dto to domain correctly`() =
        runTest {
            val supersedingDto =
                stubOpDto(
                    id = "op-resolution",
                    serverSequence = 10L,
                    entityKind = "category",
                )
            fakeRpc.resolveConflictResult = Result.success(supersedingDto)

            val result =
                api
                    .resolveConflict(
                        conflictId = "conflict-1",
                        winnerOperationId = "op-A",
                    ).getOrThrow()

            assertEquals("op-resolution", result.id)
            assertEquals(10L, result.serverSequence)
            assertEquals(EntityKind.Category, result.entityKind)
        }

    @Test
    fun `resolveConflict forwards conflict id and winner operation id to rpc`() =
        runTest {
            fakeRpc.resolveConflictResult = Result.success(stubOpDto())

            api.resolveConflict(conflictId = "conflict-99", winnerOperationId = "op-winner-1")

            assertEquals("conflict-99", fakeRpc.lastResolveConflictId)
            assertEquals("op-winner-1", fakeRpc.lastResolveWinnerOperationId)
        }

    @Test
    fun `resolveConflict propagates rpc failure`() =
        runTest {
            val error = RuntimeException("resolve failed")
            fakeRpc.resolveConflictResult = Result.failure(error)

            val result = api.resolveConflict(conflictId = "c-1", winnerOperationId = "op-1")

            assertTrue(result.isFailure)
            assertEquals(error, result.exceptionOrNull())
        }

    @Test
    fun `resolveConflict returns failure when returned dto has unknown entity kind`() =
        runTest {
            fakeRpc.resolveConflictResult = Result.success(stubOpDto(entityKind = "bad_kind"))

            val result = api.resolveConflict(conflictId = "conflict-1", winnerOperationId = "op-A")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        }
}
