package com.kshavrin.mymoney.core.domain.analytics

interface AnalyticsGateway {
    fun log(event: AnalyticsEvent)
}

sealed interface AnalyticsEvent {
    data object SupportOpened : AnalyticsEvent

    data class SupportPurchaseStarted(
        val productId: String,
    ) : AnalyticsEvent

    data class SupportPurchaseCompleted(
        val productId: String,
        val outcome: String,
    ) : AnalyticsEvent
}
