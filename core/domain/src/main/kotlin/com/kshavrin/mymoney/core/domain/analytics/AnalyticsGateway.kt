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

    data class PaywallShown(
        val entryPoint: String,
    ) : AnalyticsEvent

    data class TrialStarted(
        val productId: String,
    ) : AnalyticsEvent

    data class SubscriptionPurchased(
        val productId: String,
        val isTrialConversion: Boolean,
    ) : AnalyticsEvent

    data class SubscriptionCancelled(
        val productId: String,
        val reason: String,
    ) : AnalyticsEvent

    data class GraceEntered(
        val productId: String,
    ) : AnalyticsEvent

    data class SharedDetached(
        val reason: String,
    ) : AnalyticsEvent
}
