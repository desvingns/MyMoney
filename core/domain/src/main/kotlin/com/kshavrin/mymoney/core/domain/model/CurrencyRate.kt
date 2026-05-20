package com.kshavrin.mymoney.core.domain.model

import java.time.Instant

data class CurrencyRate(
    val id: Long,
    val fromCurrencyId: Long,
    val toCurrencyId: Long,
    val rate: Double,
    val updatedAt: Instant,
)
