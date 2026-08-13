package com.kshavrin.mymoney.core.testing.fake

import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.billing.BillingAvailability
import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import com.kshavrin.mymoney.core.domain.billing.SupportProduct
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeBillingGatewayTest {
    private val product = SupportProduct(
        id = "support_coffee",
        formattedPrice = "€1.99",
        title = "Buy me a coffee",
    )

    @Test
    fun `availability is a StateFlow and emits all four seeded states`() = runTest {
        val gateway = FakeBillingGateway()
        val availability = gateway.availability()

        assertTrue(availability is StateFlow<*>)
        availability.test {
            assertEquals(BillingAvailability.Available, awaitItem())

            listOf(
                BillingAvailability.UnavailableOnDevice,
                BillingAvailability.UnavailableInRegion,
                BillingAvailability.DisabledInBuild,
                BillingAvailability.Available,
            ).forEach { state ->
                gateway.seedAvailability(state)
                assertEquals(state, awaitItem())
            }
        }
    }

    @Test
    fun `products returns seeded Play data including its formatted price`() {
        val gateway = FakeBillingGateway()
        gateway.seedProducts(product)

        val result = gateway.products()

        assertTrue(result.isSuccess)
        assertEquals(listOf(product), result.getOrThrow())
        assertEquals("€1.99", result.getOrThrow().single().formattedPrice)
    }

    @Test
    fun `products returns the seeded failure`() {
        val gateway = FakeBillingGateway()
        val failure = IllegalStateException("products unavailable")
        gateway.seedProductsFailure(failure)

        val result = gateway.products()

        assertTrue(result.isFailure)
        assertSame(failure, result.exceptionOrNull())
    }

    @Test
    fun `purchase returns a successful default outcome for an available seeded product`() = runTest {
        val gateway = FakeBillingGateway()
        gateway.seedProducts(product)

        val outcome = gateway.purchase(product.id).first()

        assertEquals(
            PurchaseOutcome.Purchased(
                productId = product.id,
                purchaseToken = "${product.id}-purchase-token",
                purchasedAtMillis = 0L,
            ),
            outcome,
        )
    }

    @Test
    fun `purchase is a StateFlow and emits a newly seeded cancellation`() = runTest {
        val gateway = FakeBillingGateway()
        gateway.seedProducts(product)
        val purchase = gateway.purchase(product.id)

        assertTrue(purchase is StateFlow<*>)
        purchase.test {
            assertEquals(
                PurchaseOutcome.Purchased(
                    productId = product.id,
                    purchaseToken = "${product.id}-purchase-token",
                    purchasedAtMillis = 0L,
                ),
                awaitItem(),
            )

            gateway.seedPurchaseOutcome(product.id, PurchaseOutcome.Cancelled)

            assertEquals(PurchaseOutcome.Cancelled, awaitItem())
        }
    }

    @Test
    fun `purchase exposes every seeded outcome unchanged`() = runTest {
        val gateway = FakeBillingGateway()
        val outcomes = listOf(
            PurchaseOutcome.Purchased(product.id, "seeded-token", 42L),
            PurchaseOutcome.Pending,
            PurchaseOutcome.Cancelled,
            PurchaseOutcome.NetworkError,
            PurchaseOutcome.Unavailable("region is unsupported"),
        )

        outcomes.forEach { expected ->
            gateway.seedPurchaseOutcome(product.id, expected)

            assertEquals(expected, gateway.purchase(product.id).first())
        }
    }

    @Test
    fun `default purchase is unavailable for missing products and unavailable states`() = runTest {
        val missingProductGateway = FakeBillingGateway()
        assertEquals(
            PurchaseOutcome.Unavailable("missing_product"),
            missingProductGateway.purchase("missing_product").first(),
        )

        listOf(
            BillingAvailability.UnavailableOnDevice,
            BillingAvailability.UnavailableInRegion,
            BillingAvailability.DisabledInBuild,
        ).forEach { availability ->
            val gateway = FakeBillingGateway()
            gateway.seedProducts(product)
            gateway.seedAvailability(availability)

            assertEquals(
                PurchaseOutcome.Unavailable(product.id),
                gateway.purchase(product.id).first(),
            )
        }
    }

    @Test
    fun `pending purchases returns seeded outcomes`() {
        val gateway = FakeBillingGateway()
        val outcomes = listOf(PurchaseOutcome.Pending, PurchaseOutcome.Purchased(product.id, "pending-token", 7L))
        gateway.seedPendingPurchases(*outcomes.toTypedArray())

        val result = gateway.resolvePendingPurchases()

        assertTrue(result.isSuccess)
        assertEquals(outcomes, result.getOrThrow())
    }

    @Test
    fun `pending purchases returns the seeded failure`() {
        val gateway = FakeBillingGateway()
        val failure = RuntimeException("pending query failed")
        gateway.seedPendingPurchasesFailure(failure)

        val result = gateway.resolvePendingPurchases()

        assertTrue(result.isFailure)
        assertSame(failure, result.exceptionOrNull())
    }
}
