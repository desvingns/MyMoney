package com.kshavrin.mymoney.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.kshavrin.mymoney.core.database.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY used_at DESC LIMIT 20")
    fun observe(): Flow<List<SearchHistoryEntity>>

    @Query("INSERT OR REPLACE INTO search_history(`query`, used_at) VALUES(:q, :now)")
    suspend fun upsertQuery(
        q: String,
        now: Long,
    )

    @Query("DELETE FROM search_history WHERE id NOT IN (SELECT id FROM search_history ORDER BY used_at DESC LIMIT 20)")
    suspend fun pruneToLimit()
}
