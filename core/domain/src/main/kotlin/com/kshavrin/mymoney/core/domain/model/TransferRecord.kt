package com.kshavrin.mymoney.core.domain.model

import java.time.Instant

data class TransferRecord(
    val id: Long,
    val fromAccountName: String,
    val toAccountName: String,
    val amount: Money,
    val toAmount: Money?,
    val occurredAt: Instant,
    val note: String?,
)
