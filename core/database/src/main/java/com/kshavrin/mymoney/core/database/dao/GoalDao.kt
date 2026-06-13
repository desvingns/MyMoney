package com.kshavrin.mymoney.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kshavrin.mymoney.core.database.entity.GoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class GoalDao {
    @Upsert
    abstract suspend fun upsert(e: GoalEntity): Long

    @Query("SELECT * FROM goal WHERE is_archived = 0 ORDER BY created_at DESC")
    abstract fun observeActive(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goal WHERE id = :id")
    abstract suspend fun findById(id: Long): GoalEntity?

    @Query("UPDATE goal SET is_archived = 1, updated_at = :now WHERE id = :id")
    abstract suspend fun archive(
        id: Long,
        now: Long,
    )
}
