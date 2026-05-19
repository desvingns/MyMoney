package com.kshavrin.mymoney.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kshavrin.mymoney.core.database.entity.RecurringTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringTemplateDao {
    @Query("SELECT * FROM recurring_template WHERE is_active = 1 AND next_run_at <= :now")
    suspend fun findDue(now: Long): List<RecurringTemplateEntity>

    @Query("SELECT * FROM recurring_template ORDER BY next_run_at ASC")
    fun observeAll(): Flow<List<RecurringTemplateEntity>>

    @Upsert
    suspend fun upsert(template: RecurringTemplateEntity): Long

    @Query("UPDATE recurring_template SET next_run_at = :nextRunAt WHERE id = :id")
    suspend fun updateNextRun(id: Long, nextRunAt: Long)

    @Query("UPDATE recurring_template SET is_active = 0 WHERE id = :id")
    suspend fun deactivate(id: Long)
}
