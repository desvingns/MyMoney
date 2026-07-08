package com.kshavrin.mymoney.feature.transactionslist.list

sealed interface TransactionsListAction {
    data class OpenDetail(
        val transactionId: Long,
    ) : TransactionsListAction
}
