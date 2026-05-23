package com.kshavrin.mymoney.feature.transactionslist.detail

sealed interface TransactionDetailAction {
    data object NavigateBack : TransactionDetailAction
    data class ShowUndoSnackbar(val transactionId: Long) : TransactionDetailAction
}
