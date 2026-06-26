package com.kshavrin.mymoney.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kshavrin.mymoney.core.database.entity.OperationEntity

@Dao
interface OperationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(op: OperationEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(ops: List<OperationEntity>)

    @Query("SELECT * FROM op_journal WHERE synced_to_remote = 0 AND applied_from_remote = 0 ORDER BY updated_at ASC")
    suspend fun unsyncedLocal(): List<OperationEntity>

    @Query("UPDATE op_journal SET synced_to_remote = 1 WHERE op_id IN (:opIds)")
    suspend fun markSynced(opIds: List<String>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertApplied(ops: List<OperationEntity>)

    @Query("SELECT op_id FROM op_journal")
    suspend fun knownOpIds(): List<String>

    @Query("SELECT DISTINCT entity_uuid FROM op_journal")
    suspend fun knownEntityUuids(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM op_journal WHERE op_id = :opId)")
    suspend fun existsByOpId(opId: String): Boolean

    @Query("SELECT * FROM op_journal WHERE entity_uuid = :entityUuid ORDER BY updated_at ASC")
    suspend fun opsForEntity(entityUuid: String): List<OperationEntity>
}
