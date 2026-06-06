package com.kshavrin.mymoney.feature.transactionslist.list

sealed interface TransactionsListEvent {
    data class CategoryClicked(val categoryId: Long) : TransactionsListEvent
    data object CategoryFilterCleared : TransactionsListEvent
    data class RowClicked(val id: Long) : TransactionsListEvent
    data class SwipeDeleted(val id: Long) : TransactionsListEvent
    data class UndoDeleteClicked(val id: Long) : TransactionsListEvent
    data object SortClicked : TransactionsListEvent
}
