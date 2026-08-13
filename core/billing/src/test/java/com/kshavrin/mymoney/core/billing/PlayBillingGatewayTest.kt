package com.kshavrin.mymoney.core.billing

import android.app.Application
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.PurchasesUpdatedListener
import com.kshavrin.mymoney.core.domain.billing.BillingAvailability
import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import com.kshavrin.mymoney.core.domain.billing.SupportProduct
import com.kshavrin.mymoney.core.domain.model.UserEntitlement
import com.kshavrin.mymoney.core.domain.repository.EntitlementRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayBillingGatewayTest {
    @Test
    fun `disabled build returns disabled outcomes without creating the Play client`() = runTest {
        assertFalse(BuildConfig.BILLING_ENABLED)
        val fixture = fixture()

        assertEquals(BillingAvailability.DisabledInBuild, fixture.gateway.availability().first())
        assertEquals(emptyList<SupportProduct>(), fixture.gateway.products().getOrThrow())
        assertEquals(emptyList<PurchaseOutcome>(), fixture.gateway.resolvePendingPurchases().getOrThrow())
        assertEquals(
            PurchaseOutcome.Unavailable("billing_disabled_in_build"),
            fixture.gateway.purchase("coffee_small").first(),
        )
        assertEquals(0, fixture.factory.createCalls)
    }

    @Test
    fun `pending outcome keeps the bridge open for a later purchased outcome`() = runTest {
        val bridge = PurchaseOutcomeBridge()
        val purchased =
            PurchaseOutcome.Purchased(
                productId = "coffee_small",
                purchaseToken = "purchase-token",
                purchasedAtMillis = 1_723_456_789_000L,
            )

        bridge.emit(PurchaseOutcome.Pending)
        assertEquals(PurchaseOutcome.Pending, bridge.awaitNext())

        bridge.emit(purchased)
        assertEquals(purchased, bridge.awaitNext())
        assertEquals(null, bridge.awaitNext())
    }

    @Test
    fun `billing response codes map to distinct device service network and unknown availability`() {
        val cases =
            listOf(
                BillingClient.BillingResponseCode.BILLING_UNAVAILABLE to BillingAvailability.UnavailableOnDevice,
                BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE to BillingAvailability.ServiceUnavailable,
                BillingClient.BillingResponseCode.SERVICE_DISCONNECTED to BillingAvailability.NetworkUnavailable,
                BillingClient.BillingResponseCode.NETWORK_ERROR to BillingAvailability.NetworkUnavailable,
                BillingClient.BillingResponseCode.ERROR to BillingAvailability.UnknownFailure(
                    BillingClient.BillingResponseCode.ERROR,
                ),
            )

        cases.forEach { (responseCode, expected) ->
            assertEquals(expected, billingAvailabilityForResponseCode(responseCode))
        }
    }

    @Test
    fun `user cancellation maps to Cancelled instead of an error`() {
        assertEquals(
            PurchaseOutcome.Cancelled,
            purchaseOutcomeForResponseCode(BillingClient.BillingResponseCode.USER_CANCELED),
        )
    }

    @Test
    fun `every availability state maps to its purchase outcome`() {
        val expected =
            linkedMapOf(
                BillingAvailability.Available to PurchaseOutcome.Unavailable("unknown_availability"),
                BillingAvailability.UnavailableOnDevice to PurchaseOutcome.Unavailable("billing_unavailable"),
                BillingAvailability.ServiceUnavailable to PurchaseOutcome.NetworkError,
                BillingAvailability.NetworkUnavailable to PurchaseOutcome.NetworkError,
                BillingAvailability.UnavailableInRegion to
                    PurchaseOutcome.Unavailable("billing_unavailable_in_region"),
                BillingAvailability.UnknownFailure(503) to PurchaseOutcome.Unavailable("503"),
                BillingAvailability.DisabledInBuild to PurchaseOutcome.Unavailable("billing_disabled_in_build"),
            )

        expected.forEach { (availability, outcome) ->
            assertEquals(outcome, purchaseOutcomeForAvailability(availability))
        }
    }

    @Test
    fun `RU billing config is a permanent regional unavailability`() {
        assertEquals(BillingAvailability.UnavailableInRegion, billingAvailabilityForCountryCode("RU"))
    }

    @Test
    fun `non-RU billing config remains available`() {
        assertEquals(BillingAvailability.Available, billingAvailabilityForCountryCode("HU"))
    }

    @Test
    fun `pending purchase is returned without acknowledge or consume`() = runTest {
        val events = mutableListOf<String>()

        val outcome = processor(events).process(purchase(state = PurchaseProcessingState.Pending))

        assertEquals(PurchaseOutcome.Pending, outcome)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `unacknowledged purchase is acknowledged before it is consumed`() = runTest {
        val events = mutableListOf<String>()

        val outcome = processor(events).process(purchase(token = "new-token"))

        assertEquals(
            PurchaseOutcome.Purchased(
                productId = "coffee_small",
                purchaseToken = "new-token",
                purchasedAtMillis = 1_723_456_789_000L,
            ),
            outcome,
        )
        assertEquals(listOf("acknowledge:new-token", "consume:new-token"), events)
    }

    @Test
    fun `already acknowledged purchase is consumed without a second acknowledge`() = runTest {
        val events = mutableListOf<String>()

        val outcome =
            processor(events).process(
                purchase(
                    token = "acknowledged-token",
                    isAcknowledged = true,
                ),
            )

        assertEquals(
            PurchaseOutcome.Purchased(
                productId = "coffee_small",
                purchaseToken = "acknowledged-token",
                purchasedAtMillis = 1_723_456_789_000L,
            ),
            outcome,
        )
        assertEquals(listOf("consume:acknowledged-token"), events)
    }

    @Test
    fun `acknowledge failure prevents consume`() = runTest {
        val events = mutableListOf<String>()

        val outcome =
            processor(
                events = events,
                acknowledgeResponseCode = BillingClient.BillingResponseCode.NETWORK_ERROR,
            ).process(purchase(token = "ack-failure-token"))

        assertEquals(PurchaseOutcome.NetworkError, outcome)
        assertEquals(listOf("acknowledge:ack-failure-token"), events)
    }

    @Test
    fun `consume failure is returned after successful acknowledge`() = runTest {
        val events = mutableListOf<String>()

        val outcome =
            processor(
                events = events,
                consumeResponseCode = BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            ).process(purchase(token = "consume-failure-token"))

        assertEquals(PurchaseOutcome.NetworkError, outcome)
        assertEquals(
            listOf("acknowledge:consume-failure-token", "consume:consume-failure-token"),
            events,
        )
    }

    @Test
    fun `purchase processing is serialized without deadlock`() = runTest {
        val events = mutableListOf<String>()
        val callbackLock = Any()
        var concurrentCallbacks = 0
        var maxConcurrentCallbacks = 0

        suspend fun record(event: String) {
            synchronized(callbackLock) {
                concurrentCallbacks += 1
                maxConcurrentCallbacks = maxOf(maxConcurrentCallbacks, concurrentCallbacks)
                events += event
            }
            yield()
            synchronized(callbackLock) {
                concurrentCallbacks -= 1
            }
        }

        val processor =
            PurchaseProcessor(
                acknowledge = { token ->
                    record("acknowledge:$token")
                    BillingClient.BillingResponseCode.OK
                },
                consume = { token ->
                    record("consume:$token")
                    BillingClient.BillingResponseCode.OK
                },
            )

        val outcomes =
            withTimeout(2_000) {
                listOf("first-token", "second-token")
                    .map { token ->
                        async {
                            processor.process(purchase(token = token))
                        }
                    }.awaitAll()
            }

        assertEquals(2, outcomes.count { it is PurchaseOutcome.Purchased })
        assertEquals(4, events.size)
        val firstToken = events.first().substringAfter(":")
        val secondToken = events[2].substringAfter(":")
        assertEquals("acknowledge:$firstToken", events[0])
        assertEquals("consume:$firstToken", events[1])
        assertEquals("acknowledge:$secondToken", events[2])
        assertEquals("consume:$secondToken", events[3])
        assertEquals(1, maxConcurrentCallbacks)
    }

    private fun fixture(): Fixture {
        val factory = FailingBillingClientFactory()
        val gateway =
            PlayBillingGateway(
                billingClientFactory = factory,
                foregroundActivityProvider = ForegroundActivityProvider(TestApplication()),
                applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
                ioDispatcher = Dispatchers.Unconfined,
                mainDispatcher = Dispatchers.Unconfined,
                plusSubscriptionClient = PlusSubscriptionClient(),
                entitlementRepository =
                    object : EntitlementRepository {
                        override val entitlement = MutableStateFlow<UserEntitlement>(UserEntitlement.Free)

                        override suspend fun refresh(): Result<Unit> = Result.success(Unit)
                    },
            )
        return Fixture(gateway, factory)
    }

    private fun processor(
        events: MutableList<String>,
        acknowledgeResponseCode: Int = BillingClient.BillingResponseCode.OK,
        consumeResponseCode: Int = BillingClient.BillingResponseCode.OK,
    ): PurchaseProcessor =
        PurchaseProcessor(
            acknowledge = { token ->
                events += "acknowledge:$token"
                acknowledgeResponseCode
            },
            consume = { token ->
                events += "consume:$token"
                consumeResponseCode
            },
        )

    private fun purchase(
        token: String = "pending-token",
        state: PurchaseProcessingState = PurchaseProcessingState.Purchased,
        isAcknowledged: Boolean = false,
    ): PurchaseProcessingInput =
        PurchaseProcessingInput(
            productId = "coffee_small",
            purchaseToken = token,
            purchasedAtMillis = 1_723_456_789_000L,
            state = state,
            isAcknowledged = isAcknowledged,
        )

    private data class Fixture(
        val gateway: PlayBillingGateway,
        val factory: FailingBillingClientFactory,
    )

    private class TestApplication : Application() {
        override fun registerActivityLifecycleCallbacks(callback: Application.ActivityLifecycleCallbacks) = Unit
    }

    private class FailingBillingClientFactory : BillingClientFactory {
        var createCalls = 0

        override fun create(listener: PurchasesUpdatedListener): BillingClient {
            createCalls += 1
            error("Play Billing must not initialize in a disabled build")
        }
    }
}
