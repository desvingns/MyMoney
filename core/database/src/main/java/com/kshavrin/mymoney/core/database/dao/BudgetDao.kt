package com.kshavrin.mymoney.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kshavrin.mymoney.core.database.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budget WHERE is_active = 1")
    fun observeActive(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budget WHERE category_id = :id AND is_active = 1 LIMIT 1")
    suspend fun findForCategory(id: Long): BudgetEntity?

    @Query("SELECT * FROM budget WHERE category_id IS NULL AND is_active = 1 LIMIT 1")
    suspend fun findTotalBudget(): BudgetEntity?

    @Upsert
    suspend fun upsert(budget: BudgetEntity): Long

    @Query("UPDATE budget SET is_active = 0 WHERE id = :id")
    suspend fun deactivate(id: Long)
}
