package com.kshavrin.mymoney.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.kshavrin.mymoney.core.database.entity.SharedEntityStateEntity
import com.kshavrin.mymoney.core.database.entity.SharedPendingOperationEntity

@Dao
interface SharedOutboxDao {
    @Query(
        """
        SELECT * FROM shared_pending_operation
        WHERE workspace_id = :workspaceId
        ORDER BY CASE entity_kind
            WHEN 'Account' THEN 0
            WHEN 'Category' THEN 1
            ELSE 2
        END, created_at ASC, idempotency_key ASC
        """,
    )
    suspend fun pendingForWorkspace(workspaceId: String): List<SharedPendingOperationEntity>

    @Query(
        """
        SELECT * FROM shared_pending_operation
        WHERE workspace_id = :workspaceId
          AND entity_kind = :entityKind
          AND entity_id = :entityId
        ORDER BY created_at ASC, idempotency_key ASC
        """,
    )
    suspend fun pendingForEntity(
        workspaceId: String,
        entityKind: String,
        entityId: String,
    ): List<SharedPendingOperationEntity>

    @Query(
        """
        SELECT * FROM shared_entity_state
        WHERE workspace_id = :workspaceId
          AND entity_kind = :entityKind
          AND entity_id = :entityId
        LIMIT 1
        """,
    )
    suspend fun stateForEntity(
        workspaceId: String,
        entityKind: String,
        entityId: String,
    ): SharedEntityStateEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPending(operation: SharedPendingOperationEntity)

    @Upsert
    suspend fun upsertState(state: SharedEntityStateEntity)

    @Query("DELETE FROM shared_pending_operation WHERE idempotency_key = :idempotencyKey")
    suspend fun deletePending(idempotencyKey: String)

    @Query("DELETE FROM shared_pending_operation")
    suspend fun clearPending()

    @Query("DELETE FROM shared_entity_state")
    suspend fun clearStates()
}
