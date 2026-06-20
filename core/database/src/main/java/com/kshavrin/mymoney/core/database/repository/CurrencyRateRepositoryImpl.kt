package com.kshavrin.mymoney.core.database.repository

import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.database.dao.CurrencyRateDao
import com.kshavrin.mymoney.core.database.mapper.toDomain
import com.kshavrin.mymoney.core.database.mapper.toEntity
import com.kshavrin.mymoney.core.domain.model.CurrencyRate
import com.kshavrin.mymoney.core.domain.repository.CurrencyRateRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.network.ConnectivityChecker
import com.kshavrin.mymoney.core.network.ExchangeRateApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrencyRateRepositoryImpl
    @Inject
    constructor(
        private val dao: CurrencyRateDao,
        private val exchangeRateApi: ExchangeRateApi,
        private val connectivityChecker: ConnectivityChecker,
        private val currencyRepository: CurrencyRepository,
        private val clock: Clock,
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

        override suspend fun refreshRatesFromNetwork(): Result<Int> =
            withContext(ioDispatcher) {
                if (!connectivityChecker.isOnline()) {
                    return@withContext Result.failure(SyncException(SyncError.Network))
                }
                runCatching {
                    val response = exchangeRateApi.latest()
                    if (response.result != RESULT_SUCCESS) {
                        throw SyncException(SyncError.Server)
                    }
                    val euro =
                        currencyRepository.findByCode(BASE_CODE)
                            ?: throw SyncException(SyncError.Unknown)
                    val now = clock.instant()
                    var saved = 0
                    for ((code, rate) in response.rates) {
                        if (code == BASE_CODE || rate <= 0.0) continue
                        val target = currencyRepository.findByCode(code) ?: continue
                        if (target.id == euro.id) continue
                        dao.upsert(
                            CurrencyRate(
                                id = 0L,
                                fromCurrencyId = euro.id,
                                toCurrencyId = target.id,
                                rate = rate,
                                updatedAt = now,
                            ).toEntity(),
                        )
                        saved++
                    }
                    saved
                }.recoverNetworkFailure()
            }

        private fun <T> Result<T>.recoverNetworkFailure(): Result<T> =
            fold(
                onSuccess = { Result.success(it) },
                onFailure = { cause ->
                    val error = (cause as? SyncException)?.syncError ?: SyncError.Network
                    Result.failure(SyncException(error))
                },
            )

        private companion object {
            const val RESULT_SUCCESS = "success"
            const val BASE_CODE = "EUR"
        }
    }
