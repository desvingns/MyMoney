package com.kshavrin.mymoney.core.domain.repository

import com.kshavrin.mymoney.core.domain.model.CurrencyRate
import kotlinx.coroutines.flow.Flow

interface CurrencyRateRepository {
    suspend fun findRate(fromCurrencyId: Long, toCurrencyId: Long): CurrencyRate?
    fun observeAll(): Flow<List<CurrencyRate>>
    suspend fun upsert(rate: CurrencyRate): Long
    suspend fun deleteById(id: Long)
}
