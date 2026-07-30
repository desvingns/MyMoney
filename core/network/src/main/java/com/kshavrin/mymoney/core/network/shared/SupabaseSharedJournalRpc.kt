package com.kshavrin.mymoney.core.network.shared

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseSharedJournalRpc
    @Inject
    constructor(
        private val auth: SharedAuth,
        private val http: SupabaseHttpTransport,
    ) : SharedJournalRpc {
        override suspend fun pushOperation(
            workspaceId: String,
            idempotencyKey: String,
            baseSequence: Long,
            deviceId: String,
            entityKind: String,
            entityId: String,
            payload: JsonElement?,
            tombstone: Boolean,
        ): Result<SharedOperationDto> =
            rpc(
                name = "push_operation",
                payload =
                    buildJsonObject {
                        put("p_workspace_id", workspaceId)
                        put("p_idempotency_key", idempotencyKey)
                        put("p_base_sequence", baseSequence)
                        put("p_device_id", deviceId)
                        put("p_entity_kind", entityKind)
                        put("p_entity_id", entityId)
                        put("p_payload", payload ?: JsonNull)
                        put("p_tombstone", tombstone)
                    },
            ) { response -> Json.decodeFromJsonElement(SharedOperationDto.serializer(), response.jsonObject) }

        override suspend fun pullOperations(
            workspaceId: String,
            afterSequence: Long,
            limit: Int,
        ): Result<List<SharedOperationDto>> =
            rpc(
                name = "pull_operations",
                payload =
                    buildJsonObject {
                        put("p_workspace_id", workspaceId)
                        put("p_after_sequence", afterSequence)
                        put("p_limit", limit)
                    },
            ) { response ->
                response.jsonArray.map {
                    Json.decodeFromJsonElement(SharedOperationDto.serializer(), it.jsonObject)
                }
            }

        override suspend fun listPendingConflicts(workspaceId: String): Result<List<SharedConflictDto>> =
            rpc(
                name = "list_pending_conflicts",
                payload = buildJsonObject { put("p_workspace_id", workspaceId) },
            ) { response ->
                response.jsonArray.map {
                    Json.decodeFromJsonElement(SharedConflictDto.serializer(), it.jsonObject)
                }
            }

        override suspend fun resolveConflict(
            conflictId: String,
            winnerOperationId: String,
        ): Result<SharedOperationDto> =
            rpc(
                name = "resolve_conflict",
                payload =
                    buildJsonObject {
                        put("p_conflict_id", conflictId)
                        put("p_winner_operation_id", winnerOperationId)
                    },
            ) { response -> Json.decodeFromJsonElement(SharedOperationDto.serializer(), response.jsonObject) }

        private suspend fun <T> rpc(
            name: String,
            payload: kotlinx.serialization.json.JsonObject,
            decode: (JsonElement) -> T,
        ): Result<T> {
            val accessToken = auth.accessToken().getOrElse { return Result.failure(it) }
            return http
                .post(
                    path = "rest/v1/rpc/$name",
                    payload = payload,
                    accessToken = accessToken,
                    mapMembershipDeniedToAuth = true,
                ).mapCatching(decode)
        }
    }
