package com.kshavrin.mymoney.feature.transactionslist.search

import com.kshavrin.mymoney.core.domain.model.Currency

data class SearchState(
    val query: String = "",
    val history: List<String> = emptyList(),
    val results: List<SearchRow> = emptyList(),
    val currencies: List<Currency> = emptyList(),
    val phase: SearchPhase = SearchPhase.Empty,
) {
    fun currencyFor(id: Long): Currency? = currencies.firstOrNull { it.id == id }
}

sealed interface SearchPhase {
    data object Empty : SearchPhase

    data object Loading : SearchPhase

    data object Results : SearchPhase

    data object EmptyResults : SearchPhase

    data object Error : SearchPhase
}
