package com.kshavrin.mymoney.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kshavrin.mymoney.core.database.entity.CurrencyRateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CurrencyRateDao {
    @Query("SELECT * FROM currency_rate WHERE from_currency_id = :from AND to_currency_id = :to LIMIT 1")
    suspend fun findRate(
        from: Long,
        to: Long,
    ): CurrencyRateEntity?

    @Query("SELECT * FROM currency_rate")
    fun observeAll(): Flow<List<CurrencyRateEntity>>

    @Upsert
    suspend fun upsert(rate: CurrencyRateEntity): Long

    @Query("DELETE FROM currency_rate WHERE id = :id")
    suspend fun deleteById(id: Long)
}
