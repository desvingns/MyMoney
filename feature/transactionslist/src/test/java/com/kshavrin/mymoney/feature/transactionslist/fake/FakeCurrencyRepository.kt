package com.kshavrin.mymoney.feature.transactionslist.fake

import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeCurrencyRepository : CurrencyRepository {
    private val currencies = MutableStateFlow<List<Currency>>(emptyList())

    fun seed(vararg items: Currency) {
        currencies.value = (currencies.value + items).distinctBy { it.id }
    }

    override fun observeActive(): Flow<List<Currency>> = currencies.asStateFlow()

    override fun observeAll(): Flow<List<Currency>> = currencies.asStateFlow()

    override suspend fun findById(id: Long): Currency? = currencies.value.firstOrNull { it.id == id }

    override suspend fun findByCode(code: String): Currency? = currencies.value.firstOrNull { it.code == code }

    override suspend fun upsert(currency: Currency): Long = currency.id

    override suspend fun upsertAll(currencies: List<Currency>) = Unit

    override suspend fun setActive(
        id: Long,
        active: Boolean,
    ) = Unit
}
