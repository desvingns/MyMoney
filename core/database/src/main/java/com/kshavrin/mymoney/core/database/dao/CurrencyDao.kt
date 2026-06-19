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

    // A currency that owns live transactions must stay visible: imports auto-resolve currencies via
    // findByCode, which ignores is_active, so importing into a switched-off currency would otherwise
    // leave its accounts and the import-focus selection invisible on the dashboard.
    @Query(
        """
        UPDATE currency SET is_active = 1
        WHERE is_active = 0
          AND id IN (SELECT DISTINCT currency_id FROM `transaction` WHERE is_deleted = 0)
    """,
    )
    suspend fun activateCurrenciesWithLiveTransactions(): Int
}
