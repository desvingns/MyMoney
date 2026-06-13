package com.kshavrin.mymoney.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kshavrin.mymoney.core.database.entity.SyncLogEntity

@Dao
interface SyncLogDao {
    @Insert
    suspend fun insert(entry: SyncLogEntity): Long

    @Query("SELECT * FROM sync_log WHERE target = :target ORDER BY performed_at DESC LIMIT :limit")
    suspend fun recentByTarget(
        target: String,
        limit: Int = 100,
    ): List<SyncLogEntity>

    @Query("DELETE FROM sync_log WHERE target = :target AND id NOT IN (SELECT id FROM sync_log WHERE target = :target ORDER BY performed_at DESC LIMIT 100)")
    suspend fun pruneOld(target: String)
}
