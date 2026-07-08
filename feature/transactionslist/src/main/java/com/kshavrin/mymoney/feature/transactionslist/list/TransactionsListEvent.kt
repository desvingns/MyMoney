package com.kshavrin.mymoney.feature.transactionslist.list

sealed interface TransactionsListEvent {
    data object CategoryFilterCleared : TransactionsListEvent

    data class RowClicked(
        val id: Long,
    ) : TransactionsListEvent
}
