package com.kshavrin.mymoney.feature.transactionslist.fake

import com.kshavrin.mymoney.core.domain.model.CurrencyRate
import com.kshavrin.mymoney.core.domain.repository.CurrencyRateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake at the repository boundary for the S13 detail ViewModel transfer-rate tests.
 *
 * - `seed(...)` pre-loads existing rates so [findRate] can return one (the ViewModel reuses its id
 *   on upsert to update-in-place rather than insert a duplicate row).
 * - [upserts] records every [upsert] argument so tests can assert whether a rate row was written
 *   (AC5: a cross-currency rate change writes one; a same-currency transfer writes none).
 */
class FakeCurrencyRateRepository : CurrencyRateRepository {
    val upserts: MutableList<CurrencyRate> = mutableListOf()
    val deletedIds: MutableList<Long> = mutableListOf()

    private val rates = MutableStateFlow<List<CurrencyRate>>(emptyList())

    fun seed(vararg items: CurrencyRate) {
        rates.value = (rates.value + items).distinctBy { it.id }
    }

    override suspend fun findRate(
        fromCurrencyId: Long,
        toCurrencyId: Long,
    ): CurrencyRate? =
        rates.value.firstOrNull {
            it.fromCurrencyId == fromCurrencyId && it.toCurrencyId == toCurrencyId
        }

    override fun observeAll(): Flow<List<CurrencyRate>> = rates.asStateFlow()

    override suspend fun upsert(rate: CurrencyRate): Long {
        upserts.add(rate)
        val id = if (rate.id == 0L) (rates.value.maxOfOrNull { it.id } ?: 0L) + 1L else rate.id
        rates.value = rates.value.filterNot { it.id == id } + rate.copy(id = id)
        return id
    }

    override suspend fun deleteById(id: Long) {
        deletedIds.add(id)
        rates.value = rates.value.filterNot { it.id == id }
    }
}
