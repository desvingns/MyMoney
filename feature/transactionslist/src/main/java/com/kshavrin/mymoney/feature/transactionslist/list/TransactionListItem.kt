package com.kshavrin.mymoney.feature.transactionslist.list

import com.kshavrin.mymoney.core.domain.model.TransactionKind
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

sealed interface TransactionListItem {
    data class DayHeader(val date: LocalDate) : TransactionListItem

    data class Row(
        val id: Long,
        val kind: TransactionKind,
        val amount: BigDecimal,
        val currencyId: Long,
        val categoryId: Long?,
        val note: String?,
        val occurredAt: Instant,
    ) : TransactionListItem
}
