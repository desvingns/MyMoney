package com.kshavrin.mymoney.core.domain.model

import java.math.BigDecimal
import java.time.Instant

sealed interface SummaryRecord {
    val id: Long
    val timestamp: Instant

    val occurredAt: Instant
        get() = timestamp

    data class Operation(
        val transaction: Transaction,
    ) : SummaryRecord {
        override val id: Long
            get() = transaction.id

        override val timestamp: Instant
            get() = transaction.occurredAt

        val amount: BigDecimal
            get() = transaction.amount

        val kind: TransactionKind
            get() = transaction.kind

        val accountId: Long
            get() = transaction.accountId

        val currencyId: Long
            get() = transaction.currencyId

        val categoryId: Long?
            get() = transaction.categoryId

        val note: String?
            get() = transaction.note
    }

    data class Transfer(
        val transfer: TransferRecord,
    ) : SummaryRecord {
        override val id: Long
            get() = transfer.id

        override val timestamp: Instant
            get() = transfer.occurredAt

        val fromAccountName: String
            get() = transfer.fromAccountName

        val toAccountName: String
            get() = transfer.toAccountName

        val amount: Money
            get() = transfer.amount

        val toAmount: Money?
            get() = transfer.toAmount

        val note: String?
            get() = transfer.note
    }
}
