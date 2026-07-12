package com.kshavrin.mymoney.core.testing.fake

import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeCurrencyRepository : CurrencyRepository {
    private val currencies = MutableStateFlow<List<Currency>>(emptyList())

    fun seed(vararg items: Currency) {
        currencies.value = (currencies.value + items).distinctBy(Currency::id)
    }

    override fun observeActive(): Flow<List<Currency>> =
        currencies.map { items -> items.filter(Currency::isActive).sortedBy(Currency::sortOrder) }

    override fun observeAll(): Flow<List<Currency>> = currencies.map { items -> items.sortedBy(Currency::sortOrder) }

    override suspend fun findById(id: Long): Currency? = currencies.value.firstOrNull { it.id == id }

    override suspend fun findByCode(code: String): Currency? =
        currencies.value.firstOrNull { it.code.equals(code, ignoreCase = true) }

    override suspend fun upsert(currency: Currency): Long {
        val id = currency.id.takeUnless { it == 0L } ?: (currencies.value.maxOfOrNull(Currency::id) ?: 0L) + 1L
        currencies.value = currencies.value.filterNot { it.id == id } + currency.copy(id = id)
        return id
    }

    override suspend fun upsertAll(currencies: List<Currency>) {
        currencies.forEach { currency -> upsert(currency) }
    }

    override suspend fun setActive(
        id: Long,
        active: Boolean,
    ) {
        currencies.value =
            currencies.value.map { currency ->
                if (currency.id == id) currency.copy(isActive = active) else currency
            }
    }
}
