package com.kshavrin.mymoney.core.domain.model

import java.math.BigDecimal
import java.time.Instant

data class RecurringTemplate(
    val id: Long,
    val baseKind: TransactionKind,
    val amount: BigDecimal,
    val currencyId: Long,
    val accountId: Long,
    val categoryId: Long?,
    val toAccountId: Long?,
    val note: String?,
    val recurrenceKind: String,
    val interval: Int,
    val byDay: String?,
    val startsAt: Instant,
    val endsAt: Instant?,
    val nextRunAt: Instant,
    val isActive: Boolean,
)
