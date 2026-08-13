package com.kshavrin.mymoney.core.testing.fake

import com.kshavrin.mymoney.core.domain.analytics.AnalyticsEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeAnalyticsGatewayTest {
    @Test
    fun `records exactly the three typed support events in order with their payloads`() {
        val gateway = FakeAnalyticsGateway()

        gateway.log(AnalyticsEvent.SupportOpened)
        gateway.log(AnalyticsEvent.SupportPurchaseStarted(productId = "coffee_small"))
        gateway.log(
            AnalyticsEvent.SupportPurchaseCompleted(
                productId = "coffee_small",
                outcome = "purchased",
            ),
        )

        assertEquals(
            listOf(
                AnalyticsEvent.SupportOpened,
                AnalyticsEvent.SupportPurchaseStarted(productId = "coffee_small"),
                AnalyticsEvent.SupportPurchaseCompleted(
                    productId = "coffee_small",
                    outcome = "purchased",
                ),
            ),
            gateway.events,
        )
    }

    @Test
    fun `typed event payloads expose no amount currency or user identifier`() {
        val events =
            listOf(
                AnalyticsEvent.SupportOpened,
                AnalyticsEvent.SupportPurchaseStarted(productId = "coffee_large"),
                AnalyticsEvent.SupportPurchaseCompleted(
                    productId = "coffee_large",
                    outcome = "cancelled",
                ),
            )
        val forbiddenNames = setOf("amount", "currency", "userId", "user_id", "accountId", "account_id")
        val payloadNames =
            events.flatMap { event ->
                event::class.java.declaredFields
                    .filterNot { it.isSynthetic || it.name == "INSTANCE" }
                    .map { it.name }
            }

        assertTrue(forbiddenNames.intersect(payloadNames).isEmpty())
    }
}
