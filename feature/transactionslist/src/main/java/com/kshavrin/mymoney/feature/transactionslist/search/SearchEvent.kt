package com.kshavrin.mymoney.feature.transactionslist.search

sealed interface SearchEvent {
    data class QueryChanged(
        val text: String,
    ) : SearchEvent

    data class SuggestionClicked(
        val query: String,
    ) : SearchEvent

    data class VoiceResult(
        val text: String,
    ) : SearchEvent

    data class ResultClicked(
        val transactionId: Long,
    ) : SearchEvent

    data object QuerySubmitted : SearchEvent

    data object QueryCleared : SearchEvent

    data object VoiceUnavailable : SearchEvent

    data object BackClicked : SearchEvent
}
