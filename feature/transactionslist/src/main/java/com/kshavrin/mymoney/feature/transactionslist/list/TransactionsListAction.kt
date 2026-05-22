package com.kshavrin.mymoney.feature.transactionslist.list

sealed interface TransactionsListAction {
    data class ShowUndoSnackbar(val transactionId: Long) : TransactionsListAction
    data class OpenDetail(val transactionId: Long) : TransactionsListAction
}
