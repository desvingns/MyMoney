package com.kshavrin.mymoney.core.domain.model

import java.math.BigDecimal
import java.time.Instant

data class Budget(
    val id: Long,
    val categoryId: Long?,
    val periodKind: String,
    val periodStart: Instant,
    val amount: BigDecimal,
    val currencyId: Long,
    val alertThresholdPct: Int,
    val isActive: Boolean,
)
