package com.kshavrin.mymoney.core.domain.model

import java.math.BigDecimal
import java.time.Instant

data class Transaction(
    val id: Long,
    val kind: TransactionKind,
    val amount: BigDecimal,
    val currencyId: Long,
    val accountId: Long,
    val categoryId: Long?,
    val note: String?,
    val occurredAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
    val isDeleted: Boolean,
    val toAccountId: Long?,
    val toAmount: BigDecimal?,
    val exchangeRate: Double?,
)
