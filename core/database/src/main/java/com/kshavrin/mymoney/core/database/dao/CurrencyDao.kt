package com.kshavrin.mymoney.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kshavrin.mymoney.core.database.entity.CurrencyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CurrencyDao {
    @Query("SELECT * FROM currency WHERE is_active = 1 ORDER BY sort_order")
    fun observeActive(): Flow<List<CurrencyEntity>>

    @Query("SELECT * FROM currency ORDER BY sort_order")
    fun observeAll(): Flow<List<CurrencyEntity>>

    @Query("SELECT * FROM currency WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): CurrencyEntity?

    @Query("SELECT * FROM currency WHERE code = :code COLLATE NOCASE LIMIT 1")
    suspend fun findByCode(code: String): CurrencyEntity?

    @Upsert
    suspend fun upsert(item: CurrencyEntity): Long

    @Upsert
    suspend fun upsertAll(items: List<CurrencyEntity>)

    @Query("UPDATE currency SET is_active = :active WHERE id = :id")
    suspend fun setActive(
        id: Long,
        active: Boolean,
    )
}
