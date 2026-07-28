package com.kshavrin.mymoney.core.network.shared

import com.kshavrin.mymoney.core.domain.repository.SharedJournalRepository
import com.kshavrin.mymoney.core.domain.sync.EntityKind
import com.kshavrin.mymoney.core.domain.sync.SharedConflict
import com.kshavrin.mymoney.core.domain.sync.SharedOperation
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseSharedJournalApi
    @Inject
    constructor(
        private val rpc: SharedJournalRpc,
        private val json: Json,
    ) : SharedJournalRepository {
        override suspend fun push(
            workspaceId: String,
            idempotencyKey: String,
            baseSequence: Long,
            deviceId: String,
            entityKind: EntityKind,
            entityId: String,
            payload: String?,
            tombstone: Boolean,
        ): Result<SharedOperation> = runCatching {
            rpc.pushOperation(
                workspaceId = workspaceId,
                idempotencyKey = idempotencyKey,
                baseSequence = baseSequence,
                deviceId = deviceId,
                entityKind = entityKind.toRpcValue(),
                entityId = entityId,
                payload = payload?.let { json.parseToJsonElement(it) },
                tombstone = tombstone,
            ).getOrThrow().toDomain()
        }

        override suspend fun pull(
            workspaceId: String,
            afterSequence: Long,
            limit: Int,
        ): Result<List<SharedOperation>> =
            rpc.pullOperations(
                workspaceId = workspaceId,
                afterSequence = afterSequence,
                limit = limit,
            ).mapCatching { dtos -> dtos.map { it.toDomain() } }

        override suspend fun listPendingConflicts(workspaceId: String): Result<List<SharedConflict>> =
            rpc.listPendingConflicts(workspaceId).mapCatching { dtos -> dtos.map { it.toDomain() } }

        override suspend fun resolveConflict(
            conflictId: String,
            winnerOperationId: String,
        ): Result<SharedOperation> =
            rpc.resolveConflict(
                conflictId = conflictId,
                winnerOperationId = winnerOperationId,
            ).mapCatching { it.toDomain() }
    }
