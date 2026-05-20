package com.kshavrin.mymoney.core.database.repository

import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.database.dao.CurrencyDao
import com.kshavrin.mymoney.core.database.mapper.toDomain
import com.kshavrin.mymoney.core.database.mapper.toEntity
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrencyRepositoryImpl @Inject constructor(
    private val dao: CurrencyDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CurrencyRepository {

    override fun observeActive(): Flow<List<Currency>> = dao.observeActive().map { list -> list.map { it.toDomain() } }

    override fun observeAll(): Flow<List<Currency>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun findById(id: Long): Currency? = withContext(ioDispatcher) {
        dao.findById(id)?.toDomain()
    }

    override suspend fun findByCode(code: String): Currency? = withContext(ioDispatcher) {
        dao.findByCode(code)?.toDomain()
    }

    override suspend fun upsert(currency: Currency): Long = withContext(ioDispatcher) {
        require(currency.code.matches(CODE_REGEX)) { "code must match ${CODE_REGEX.pattern}; got: ${currency.code}" }
        require(currency.symbol.isNotBlank() && currency.symbol.length <= 4) { "symbol must be non-blank and <= 4 chars" }
        require(currency.decimalDigits in 0..8) { "decimalDigits must be in 0..8" }
        dao.upsert(currency.toEntity())
    }

    override suspend fun upsertAll(currencies: List<Currency>) = withContext(ioDispatcher) {
        currencies.forEach { c ->
            require(c.code.matches(CODE_REGEX)) { "code must match ${CODE_REGEX.pattern}; got: ${c.code}" }
        }
        dao.upsertAll(currencies.map { it.toEntity() })
    }

    override suspend fun setActive(id: Long, active: Boolean) = withContext(ioDispatcher) {
        dao.setActive(id, active)
    }

    private companion object {
        val CODE_REGEX = Regex("^[A-Z]{3}$")
    }
}
