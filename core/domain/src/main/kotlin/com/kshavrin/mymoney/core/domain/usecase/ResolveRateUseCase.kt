package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.CurrencyRate
import com.kshavrin.mymoney.core.domain.repository.CurrencyRateRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

private const val BASE_CURRENCY_CODE = "EUR"

data class RateInfo(
    val crossRate: BigDecimal?,
    val lastUpdated: LocalDate?,
    val refreshedNow: Boolean,
    val stale: Boolean,
    val missing: Boolean,
)

class ResolveRateUseCase
    @Inject
    constructor(
        private val currencyRateRepository: CurrencyRateRepository,
        private val currencyRepository: CurrencyRepository,
        private val convertMoney: ConvertMoneyUseCase,
        private val clock: Clock,
        private val zoneId: ZoneId = ZoneId.systemDefault(),
    ) {
        suspend operator fun invoke(
            from: Currency,
            to: Currency,
        ): RateInfo {
            val today = LocalDate.now(clock.withZone(zoneId))

            if (from.id == to.id) {
                return RateInfo(
                    crossRate = BigDecimal.ONE,
                    lastUpdated = null,
                    refreshedNow = false,
                    stale = false,
                    missing = false,
                )
            }

            val euroId = currencyRepository.findByCode(BASE_CURRENCY_CODE)?.id

            val initialLastUpdated = relevantLastUpdated(euroId, from, to)
            val stale = initialLastUpdated == null || initialLastUpdated != today

            val refreshedNow =
                if (stale) {
                    currencyRateRepository.refreshRatesFromNetwork().isSuccess
                } else {
                    false
                }

            val resolvedEuroId =
                euroId ?: currencyRepository.findByCode(BASE_CURRENCY_CODE)?.id
            val lastUpdated = relevantLastUpdated(resolvedEuroId, from, to)
            val crossRate = computeCrossRate(resolvedEuroId, from, to)

            return RateInfo(
                crossRate = crossRate,
                lastUpdated = lastUpdated,
                refreshedNow = refreshedNow,
                stale = lastUpdated == null || lastUpdated != today,
                missing = crossRate == null,
            )
        }

        private suspend fun relevantLastUpdated(
            euroId: Long?,
            from: Currency,
            to: Currency,
        ): LocalDate? {
            val dates =
                listOfNotNull(
                    storedRate(euroId, from)?.updatedAt,
                    storedRate(euroId, to)?.updatedAt,
                ).map { it.atZone(zoneId).toLocalDate() }
            return dates.minOrNull()
        }

        private suspend fun computeCrossRate(
            euroId: Long?,
            from: Currency,
            to: Currency,
        ): BigDecimal? {
            val rateFrom = rateFromEur(euroId, from) ?: return null
            val rateTo = rateFromEur(euroId, to) ?: return null
            val lookup =
                RatesLookup { currency ->
                    when (currency.id) {
                        from.id -> rateFrom
                        to.id -> rateTo
                        else -> null
                    }
                }
            return when (val result = convertMoney(BigDecimal.ONE, from, to, lookup)) {
                is ConversionResult.Converted -> result.crossRate
                is ConversionResult.RateMissing -> null
            }
        }

        private suspend fun rateFromEur(
            euroId: Long?,
            currency: Currency,
        ): BigDecimal? =
            if (currency.code == BASE_CURRENCY_CODE) {
                BigDecimal.ONE
            } else {
                storedRate(euroId, currency)?.rate?.let { BigDecimal.valueOf(it) }
            }

        private suspend fun storedRate(
            euroId: Long?,
            currency: Currency,
        ): CurrencyRate? {
            if (currency.code == BASE_CURRENCY_CODE || euroId == null) return null
            return currencyRateRepository.findRate(euroId, currency.id)
        }
    }
