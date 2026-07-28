package com.kshavrin.mymoney.core.domain.repository

import com.kshavrin.mymoney.core.domain.sync.EntityKind
import com.kshavrin.mymoney.core.domain.sync.SharedConflict
import com.kshavrin.mymoney.core.domain.sync.SharedOperation

interface SharedJournalRepository {
    suspend fun push(
        workspaceId: String,
        idempotencyKey: String,
        baseSequence: Long,
        deviceId: String,
        entityKind: EntityKind,
        entityId: String,
        payload: String?,
        tombstone: Boolean,
    ): Result<SharedOperation>

    suspend fun pull(
        workspaceId: String,
        afterSequence: Long,
        limit: Int = 100,
    ): Result<List<SharedOperation>>

    suspend fun listPendingConflicts(workspaceId: String): Result<List<SharedConflict>>

    suspend fun resolveConflict(
        conflictId: String,
        winnerOperationId: String,
    ): Result<SharedOperation>
}
