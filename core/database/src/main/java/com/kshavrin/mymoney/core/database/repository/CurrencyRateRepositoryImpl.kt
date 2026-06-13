package com.kshavrin.mymoney.core.database.repository

import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.database.dao.CurrencyRateDao
import com.kshavrin.mymoney.core.database.mapper.toDomain
import com.kshavrin.mymoney.core.database.mapper.toEntity
import com.kshavrin.mymoney.core.domain.model.CurrencyRate
import com.kshavrin.mymoney.core.domain.repository.CurrencyRateRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrencyRateRepositoryImpl
    @Inject
    constructor(
        private val dao: CurrencyRateDao,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : CurrencyRateRepository {
        override suspend fun findRate(
            fromCurrencyId: Long,
            toCurrencyId: Long,
        ): CurrencyRate? =
            withContext(ioDispatcher) {
                dao.findRate(fromCurrencyId, toCurrencyId)?.toDomain()
            }

        override fun observeAll(): Flow<List<CurrencyRate>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

        override suspend fun upsert(rate: CurrencyRate): Long =
            withContext(ioDispatcher) {
                require(rate.rate > 0) { "rate must be > 0; got ${rate.rate}" }
                require(rate.fromCurrencyId != rate.toCurrencyId) { "fromCurrencyId must differ from toCurrencyId" }
                dao.upsert(rate.toEntity())
            }

        override suspend fun deleteById(id: Long) =
            withContext(ioDispatcher) {
                dao.deleteById(id)
            }
    }
