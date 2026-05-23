package com.kshavrin.mymoney.feature.transactionslist.search

import com.kshavrin.mymoney.core.domain.model.TransactionKind
import java.math.BigDecimal
import java.time.Instant

data class SearchRow(
    val id: Long,
    val kind: TransactionKind,
    val amount: BigDecimal,
    val currencyId: Long,
    val categoryId: Long?,
    val note: String?,
    val occurredAt: Instant,
)
