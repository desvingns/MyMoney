package com.kshavrin.mymoney.core.domain.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BillingContractsTest {
    @Test
    fun `billing availability exposes every distinct state`() {
        val states =
            listOf(
                BillingAvailability.Available,
                BillingAvailability.UnavailableOnDevice,
                BillingAvailability.ServiceUnavailable,
                BillingAvailability.NetworkUnavailable,
                BillingAvailability.UnavailableInRegion,
                BillingAvailability.UnknownFailure(responseCode = 42),
                BillingAvailability.DisabledInBuild,
            )

        assertEquals(7, states.toSet().size)
        assertNotEquals(BillingAvailability.UnavailableOnDevice, BillingAvailability.UnavailableInRegion)
        assertNotEquals(BillingAvailability.ServiceUnavailable, BillingAvailability.NetworkUnavailable)
        assertNotEquals(BillingAvailability.NetworkUnavailable, BillingAvailability.UnknownFailure(42))
        assertNotEquals(BillingAvailability.UnavailableInRegion, BillingAvailability.DisabledInBuild)
    }

    @Test
    fun `support product keeps the Play formatted price string unchanged`() {
        val product =
            SupportProduct(
                id = "support_coffee",
                formattedPrice = "€1.99",
                title = "Buy me a coffee",
            )

        assertEquals("support_coffee", product.id)
        assertEquals("€1.99", product.formattedPrice)
        assertEquals("Buy me a coffee", product.title)
    }

    @Test
    fun `purchased outcome keeps product receipt fields`() {
        val outcome =
            PurchaseOutcome.Purchased(
                productId = "support_coffee",
                purchaseToken = "token-123",
                purchasedAtMillis = 1_723_456_789_000L,
            )

        assertEquals("support_coffee", outcome.productId)
        assertEquals("token-123", outcome.purchaseToken)
        assertEquals(1_723_456_789_000L, outcome.purchasedAtMillis)
    }

    @Test
    fun `marker outcomes are stable singleton values and unavailable keeps its reason`() {
        assertSame(PurchaseOutcome.Pending, PurchaseOutcome.Pending)
        assertSame(PurchaseOutcome.Cancelled, PurchaseOutcome.Cancelled)
        assertSame(PurchaseOutcome.NetworkError, PurchaseOutcome.NetworkError)
        assertEquals("billing disabled", PurchaseOutcome.Unavailable("billing disabled").reason)
    }
}
