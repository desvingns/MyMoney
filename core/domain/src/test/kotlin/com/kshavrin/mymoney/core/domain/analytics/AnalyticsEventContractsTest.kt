package com.kshavrin.mymoney.core.domain.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsEventContractsTest {
    @Test
    fun `analytics event contract exposes every support and subscription event`() {
        val events =
            listOf(
                AnalyticsEvent.SupportOpened,
                AnalyticsEvent.SupportPurchaseStarted(productId = "support_coffee"),
                AnalyticsEvent.SupportPurchaseCompleted(
                    productId = "support_coffee",
                    outcome = "purchased",
                ),
                AnalyticsEvent.PaywallShown(entryPoint = "support"),
                AnalyticsEvent.TrialStarted(productId = "plus_monthly"),
                AnalyticsEvent.SubscriptionPurchased(
                    productId = "plus_monthly",
                    isTrialConversion = true,
                ),
                AnalyticsEvent.SubscriptionCancelled(
                    productId = "plus_monthly",
                    reason = "user_requested",
                ),
                AnalyticsEvent.GraceEntered(productId = "plus_monthly"),
                AnalyticsEvent.SharedDetached(reason = "account_removed"),
            )

        assertEquals(9, events.size)
        assertEquals(9, events.toSet().size)
    }
}
