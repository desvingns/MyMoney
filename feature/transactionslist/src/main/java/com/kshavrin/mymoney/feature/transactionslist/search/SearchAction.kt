package com.kshavrin.mymoney.feature.transactionslist.search

sealed interface SearchAction {
    data class OpenDetail(
        val transactionId: Long,
    ) : SearchAction

    data object NavigateBack : SearchAction

    data object ShowVoiceUnavailable : SearchAction
}
