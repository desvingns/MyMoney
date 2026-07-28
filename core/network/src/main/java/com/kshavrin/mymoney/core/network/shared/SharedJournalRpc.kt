package com.kshavrin.mymoney.core.network.shared

import kotlinx.serialization.json.JsonElement

// Transport seam over the Supabase PostgREST `/rest/v1/rpc/<fn>` endpoints.
// Each method maps 1:1 onto a SECURITY DEFINER function in
// supabase/migrations/0002_shared_operations.sql; parameter names mirror the SQL
// `p_*` arguments. The concrete HTTP implementation lands in a later Shared-mode
// SPEC — this contract fixes the protocol so callers cannot bypass the server-sequence
// and idempotency guarantees.
interface SharedJournalRpc {
    // Maps to push_operation(p_workspace_id, p_idempotency_key, p_base_sequence,
    //   p_device_id, p_entity_kind, p_entity_id, p_payload, p_tombstone).
    suspend fun pushOperation(
        workspaceId: String,
        idempotencyKey: String,
        baseSequence: Long,
        deviceId: String,
        entityKind: String,
        entityId: String,
        payload: JsonElement?,
        tombstone: Boolean,
    ): Result<SharedOperationDto>

    // Maps to pull_operations(p_workspace_id, p_after_sequence, p_limit).
    suspend fun pullOperations(
        workspaceId: String,
        afterSequence: Long,
        limit: Int,
    ): Result<List<SharedOperationDto>>

    // Maps to list_pending_conflicts(p_workspace_id).
    suspend fun listPendingConflicts(workspaceId: String): Result<List<SharedConflictDto>>

    // Maps to resolve_conflict(p_conflict_id, p_winner_operation_id).
    suspend fun resolveConflict(
        conflictId: String,
        winnerOperationId: String,
    ): Result<SharedOperationDto>
}
