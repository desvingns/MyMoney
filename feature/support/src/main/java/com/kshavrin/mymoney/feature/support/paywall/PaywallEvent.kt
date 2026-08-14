package com.kshavrin.mymoney.feature.support.paywall

sealed interface PaywallEvent {
    data object BackClicked : PaywallEvent

    data class PlanSelected(
        val planId: PaywallPlanId,
    ) : PaywallEvent

    data object RetryClicked : PaywallEvent
}
