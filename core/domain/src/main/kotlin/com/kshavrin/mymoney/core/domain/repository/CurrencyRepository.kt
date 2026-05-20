package com.kshavrin.mymoney.core.domain.repository

import com.kshavrin.mymoney.core.domain.model.Currency
import kotlinx.coroutines.flow.Flow

interface CurrencyRepository {
    fun observeActive(): Flow<List<Currency>>
    fun observeAll(): Flow<List<Currency>>
    suspend fun findById(id: Long): Currency?
    suspend fun findByCode(code: String): Currency?
    suspend fun upsert(currency: Currency): Long
    suspend fun upsertAll(currencies: List<Currency>)
    suspend fun setActive(id: Long, active: Boolean)
}
