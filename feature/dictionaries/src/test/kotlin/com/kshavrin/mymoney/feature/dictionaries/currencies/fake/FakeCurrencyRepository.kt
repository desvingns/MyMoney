package com.kshavrin.mymoney.feature.dictionaries.currencies.fake

import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeCurrencyRepository : CurrencyRepository {
    private val state = MutableStateFlow<List<Currency>>(emptyList())

    fun seed(vararg currencies: Currency) {
        state.value = (state.value + currencies).distinctBy { it.id }
    }

    override fun observeActive(): Flow<List<Currency>> = state.asStateFlow()
    override fun observeAll(): Flow<List<Currency>> = state.asStateFlow()
    override suspend fun findById(id: Long): Currency? = state.value.firstOrNull { it.id == id }
    override suspend fun findByCode(code: String): Currency? =
        state.value.firstOrNull { it.code.equals(code, ignoreCase = true) }

    override suspend fun upsert(currency: Currency): Long {
        val id = if (currency.id == 0L) (state.value.maxOfOrNull { it.id } ?: 0L) + 1L else currency.id
        state.value = state.value.filterNot { it.id == id } + currency.copy(id = id)
        return id
    }

    override suspend fun upsertAll(currencies: List<Currency>) {
        currencies.forEach { upsert(it) }
    }

    override suspend fun setActive(id: Long, active: Boolean) {
        state.value = state.value.map { if (it.id == id) it.copy(isActive = active) else it }
    }
}
