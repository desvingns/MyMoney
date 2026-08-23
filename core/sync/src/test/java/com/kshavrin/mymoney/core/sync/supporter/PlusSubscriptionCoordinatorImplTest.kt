package com.kshavrin.mymoney.core.sync.supporter

import com.kshavrin.mymoney.core.domain.billing.BillingAvailability
import com.kshavrin.mymoney.core.domain.billing.BillingGateway
import com.kshavrin.mymoney.core.domain.billing.PlusCatalogState
import com.kshavrin.mymoney.core.domain.billing.PlusPlanId
import com.kshavrin.mymoney.core.domain.billing.PlusPurchaseOutcome
import com.kshavrin.mymoney.core.domain.billing.PlusPurchaseState
import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import com.kshavrin.mymoney.core.domain.billing.SupportProduct
import com.kshavrin.mymoney.core.domain.model.EntitlementSource
import com.kshavrin.mymoney.core.domain.model.EntitlementState
import com.kshavrin.mymoney.core.domain.model.UserEntitlement
import com.kshavrin.mymoney.core.domain.repository.EntitlementRepository
import com.kshavrin.mymoney.core.domain.usecase.ObserveEntitlementUseCase
import com.kshavrin.mymoney.core.testing.fake.FakeBillingGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PlusSubscriptionCoordinatorImplTest {

    private val monthlyProduct = SupportProduct(PlusPlanId.Monthly.productId, "€1.99 / month", "Monthly")
    private val yearlyProduct = SupportProduct(PlusPlanId.Yearly.productId, "€12.99 / year", "Yearly")

    // region refreshCatalog

    @Test
    fun `refreshCatalog transitions to Available with correct prices for all plans`() = runTest {
        val billing = FakeBillingGateway().apply {
            seedAvailability(BillingAvailability.Available)
            seedSubscriptions(monthlyProduct, yearlyProduct)
        }
        val coordinator = coordinator(billing)

        coordinator.refreshCatalog()
        advanceUntilIdle()

        assertEquals(PlusCatalogState.Available, coordinator.state.value.catalog)
        assertEquals(
            mapOf(PlusPlanId.Monthly to "€1.99 / month", PlusPlanId.Yearly to "€12.99 / year"),
            coordinator.state.value.prices,
        )
    }

    @Test
    fun `refreshCatalog transitions to UnavailableInRegion when billing reports region restriction`() = runTest {
        val billing = FakeBillingGateway().apply {
            seedAvailability(BillingAvailability.UnavailableInRegion)
        }
        val coordinator = coordinator(billing)

        coordinator.refreshCatalog()
        advanceUntilIdle()

        assertEquals(PlusCatalogState.UnavailableInRegion, coordinator.state.value.catalog)
    }

    @Test
    fun `refreshCatalog transitions to Unavailable when billing is disabled in build`() = runTest {
        val billing = FakeBillingGateway().apply {
            seedAvailability(BillingAvailability.DisabledInBuild)
        }
        val coordinator = coordinator(billing)

        coordinator.refreshCatalog()
        advanceUntilIdle()

        assertEquals(PlusCatalogState.Unavailable, coordinator.state.value.catalog)
    }

    @Test
    fun `refreshCatalog transitions to Unavailable when billing is unavailable on device`() = runTest {
        val billing = FakeBillingGateway().apply {
            seedAvailability(BillingAvailability.UnavailableOnDevice)
        }
        val coordinator = coordinator(billing)

        coordinator.refreshCatalog()
        advanceUntilIdle()

        assertEquals(PlusCatalogState.Unavailable, coordinator.state.value.catalog)
    }

    @Test
    fun `refreshCatalog transitions to Error when network is unavailable`() = runTest {
        val billing = FakeBillingGateway().apply {
            seedAvailability(BillingAvailability.NetworkUnavailable)
        }
        val coordinator = coordinator(billing)

        coordinator.refreshCatalog()
        advanceUntilIdle()

        assertEquals(PlusCatalogState.Error, coordinator.state.value.catalog)
    }

    @Test
    fun `refreshCatalog transitions to Error when service is unavailable`() = runTest {
        val billing = FakeBillingGateway().apply {
            seedAvailability(BillingAvailability.ServiceUnavailable)
        }
        val coordinator = coordinator(billing)

        coordinator.refreshCatalog()
        advanceUntilIdle()

        assertEquals(PlusCatalogState.Error, coordinator.state.value.catalog)
    }

    @Test
    fun `refreshCatalog transitions to Error when subscription query fails`() = runTest {
        val billing = FakeBillingGateway().apply {
            seedAvailability(BillingAvailability.Available)
            seedSubscriptionsFailure(RuntimeException("network error"))
        }
        val coordinator = coordinator(billing)

        coordinator.refreshCatalog()
        advanceUntilIdle()

        assertEquals(PlusCatalogState.Error, coordinator.state.value.catalog)
        assertTrue("prices cleared on catalog error", coordinator.state.value.prices.isEmpty())
    }

    @Test
    fun `refreshCatalog clears stale prices when a later region check reports UnavailableInRegion`() = runTest {
        val billing = FakeBillingGateway().apply {
            seedAvailability(BillingAvailability.Available)
            seedSubscriptions(monthlyProduct, yearlyProduct)
        }
        val coordinator = coordinator(billing)
        coordinator.refreshCatalog()
        advanceUntilIdle()
        assertTrue("prices must be populated from the first successful load", coordinator.state.value.prices.isNotEmpty())

        billing.seedAvailability(BillingAvailability.UnavailableInRegion)
        coordinator.refreshCatalog()
        advanceUntilIdle()

        assertEquals(PlusCatalogState.UnavailableInRegion, coordinator.state.value.catalog)
        assertTrue(
            "prices from a prior successful session must not survive into UnavailableInRegion",
            coordinator.state.value.prices.isEmpty(),
        )
    }

    @Test
    fun `refreshCatalog clears stale prices when billing later reports Unavailable`() = runTest {
        val billing = FakeBillingGateway().apply {
            seedAvailability(BillingAvailability.Available)
            seedSubscriptions(monthlyProduct, yearlyProduct)
        }
        val coordinator = coordinator(billing)
        coordinator.refreshCatalog()
        advanceUntilIdle()
        assertTrue("prices must be populated from the first successful load", coordinator.state.value.prices.isNotEmpty())

        billing.seedAvailability(BillingAvailability.UnavailableOnDevice)
        coordinator.refreshCatalog()
        advanceUntilIdle()

        assertEquals(PlusCatalogState.Unavailable, coordinator.state.value.catalog)
        assertTrue(
            "prices from a prior successful session must not survive into Unavailable",
            coordinator.state.value.prices.isEmpty(),
        )
    }

    // endregion

    // region purchase – happy path

    @Test
    fun `purchase Purchased outcome drives state through InProgress then ReconcilingEntitlement then Idle`() = runTest {
        val entitlementRepo = FakeEntitlementRepository()
        // Seed only from the second refresh so the first reconciliation loop iteration must
        // fall through to delay() — a genuine suspension point the collector can interleave
        // with, instead of resolving inline within the same dispatch and conflating away the
        // ReconcilingEntitlement value before the collector ever observes it.
        entitlementRepo.onRefresh = {
            if (entitlementRepo.refreshCalls >= 2) entitlementRepo.seed(activeEntitlement())
        }

        val coordinator = coordinator(
            TestBillingGateway(flowOf(PurchaseOutcome.Purchased("plus_monthly", "tok-monthly", 0L))),
            entitlementRepo,
        )
        coordinator.refreshCatalog()
        advanceUntilIdle()

        val purchaseStates = mutableListOf<PlusPurchaseState>()
        val stateCollector = launch { coordinator.state.collect { purchaseStates += it.purchase } }
        runCurrent()

        coordinator.purchase(PlusPlanId.Monthly)
        advanceUntilIdle()
        stateCollector.cancel()

        assertTrue("InProgress must appear in purchase state sequence", PlusPurchaseState.InProgress in purchaseStates)
        assertTrue("ReconcilingEntitlement must appear in purchase state sequence", PlusPurchaseState.ReconcilingEntitlement in purchaseStates)
        assertEquals("final purchase state must be Idle after successful reconciliation", PlusPurchaseState.Idle, purchaseStates.last())
    }

    @Test
    fun `purchase Purchased outcome reconciles entitlement and resolves to Idle when refresh confirms`() = runTest {
        val entitlementRepo = FakeEntitlementRepository()
        val active = activeEntitlement()
        entitlementRepo.onRefresh = { entitlementRepo.seed(active) }

        val coordinator = coordinator(
            TestBillingGateway(flowOf(PurchaseOutcome.Purchased("plus_monthly", "tok-monthly", 0L))),
            entitlementRepo,
        )
        coordinator.refreshCatalog()
        advanceUntilIdle()

        val outcome = coordinator.purchase(PlusPlanId.Monthly)
        advanceUntilIdle()

        assertEquals(PlusPurchaseOutcome.Purchased, outcome)
        assertEquals(PlusPurchaseState.Idle, coordinator.state.value.purchase)
        assertEquals(active, coordinator.state.value.entitlement)
    }

    @Test
    fun `purchase Purchased outcome transitions to AwaitingEntitlement when entitlement never confirms`() = runTest {
        val entitlementRepo = FakeEntitlementRepository()

        val coordinator = coordinator(
            TestBillingGateway(flowOf(PurchaseOutcome.Purchased("plus_monthly", "tok-monthly", 0L))),
            entitlementRepo,
        )
        coordinator.refreshCatalog()
        advanceUntilIdle()

        val purchaseJob = launch { coordinator.purchase(PlusPlanId.Monthly) }
        advanceUntilIdle()

        assertEquals(PlusPurchaseState.AwaitingEntitlement, coordinator.state.value.purchase)
        assertEquals(4, entitlementRepo.refreshCalls)

        purchaseJob.cancel()
    }

    @Test
    fun `purchase AwaitingEntitlement state resets to Idle when entitlement repository emits active Plus`() = runTest {
        val entitlementRepo = FakeEntitlementRepository()
        val coordinator = coordinator(
            TestBillingGateway(flowOf(PurchaseOutcome.Purchased("plus_monthly", "tok-monthly", 0L))),
            entitlementRepo,
        )
        coordinator.refreshCatalog()
        advanceUntilIdle()

        val purchaseJob = launch { coordinator.purchase(PlusPlanId.Monthly) }
        advanceUntilIdle()

        assertEquals(PlusPurchaseState.AwaitingEntitlement, coordinator.state.value.purchase)

        entitlementRepo.seed(activeEntitlement())
        runCurrent()

        assertEquals(PlusPurchaseState.Idle, coordinator.state.value.purchase)

        purchaseJob.cancel()
    }

    // endregion

    // region purchase – guard conditions

    @Test
    fun `concurrent purchase call while first is in flight returns NotStarted`() = runTest {
        val coordinator = coordinator(TestBillingGateway(flow { awaitCancellation() }))
        coordinator.refreshCatalog()
        advanceUntilIdle()

        val firstJob = launch { coordinator.purchase(PlusPlanId.Monthly) }
        runCurrent()

        assertEquals(PlusPurchaseState.InProgress, coordinator.state.value.purchase)

        val outcome = coordinator.purchase(PlusPlanId.Yearly)

        assertEquals(PlusPurchaseOutcome.NotStarted, outcome)

        firstJob.cancelAndJoin()
    }

    @Test
    fun `purchase when catalog is not Available returns NotStarted without touching billing`() = runTest {
        val billing = FakeBillingGateway().apply {
            seedAvailability(BillingAvailability.NetworkUnavailable)
        }
        val coordinator = coordinator(billing)
        coordinator.refreshCatalog()
        advanceUntilIdle()

        assertEquals(PlusCatalogState.Error, coordinator.state.value.catalog)

        val outcome = coordinator.purchase(PlusPlanId.Monthly)

        assertEquals(PlusPurchaseOutcome.NotStarted, outcome)
        assertEquals(PlusPurchaseState.Idle, coordinator.state.value.purchase)
    }

    // endregion

    // region purchase – billing outcomes

    @Test
    fun `purchase Cancelled outcome resets purchase state to Idle`() = runTest {
        val coordinator = coordinator(TestBillingGateway(flowOf(PurchaseOutcome.Cancelled)))
        coordinator.refreshCatalog()
        advanceUntilIdle()

        val outcome = coordinator.purchase(PlusPlanId.Monthly)

        assertEquals(PlusPurchaseOutcome.Cancelled, outcome)
        assertEquals(PlusPurchaseState.Idle, coordinator.state.value.purchase)
    }

    @Test
    fun `purchase NetworkError outcome resets purchase state to Idle and returns Failed`() = runTest {
        val coordinator = coordinator(TestBillingGateway(flowOf(PurchaseOutcome.NetworkError)))
        coordinator.refreshCatalog()
        advanceUntilIdle()

        val outcome = coordinator.purchase(PlusPlanId.Monthly)

        assertEquals(PlusPurchaseOutcome.Failed, outcome)
        assertEquals(PlusPurchaseState.Idle, coordinator.state.value.purchase)
    }

    @Test
    fun `purchase Unavailable outcome resets purchase state to Idle and triggers catalog refresh`() = runTest {
        val coordinator = coordinator(TestBillingGateway(flowOf(PurchaseOutcome.Unavailable("quota"))))
        coordinator.refreshCatalog()
        advanceUntilIdle()

        val outcome = coordinator.purchase(PlusPlanId.Monthly)
        advanceUntilIdle()

        assertEquals(PlusPurchaseOutcome.Unavailable, outcome)
        assertEquals(PlusPurchaseState.Idle, coordinator.state.value.purchase)
        assertEquals(PlusCatalogState.Available, coordinator.state.value.catalog)
    }

    // endregion

    // region critical fixes from commit 5f8b3b0c

    @Test
    fun `purchase state resets to Idle when calling coroutine is cancelled while InProgress before lock transfer`() =
        runTest {
            val coordinator = coordinator(TestBillingGateway(flow { awaitCancellation() }))
            coordinator.refreshCatalog()
            advanceUntilIdle()

            val purchaseJob = launch { coordinator.purchase(PlusPlanId.Monthly) }
            runCurrent()

            assertEquals(
                "purchase should be InProgress while billing flow is suspended",
                PlusPurchaseState.InProgress,
                coordinator.state.value.purchase,
            )

            purchaseJob.cancelAndJoin()

            assertEquals(
                "cancellation before lock transfer must reset purchase to Idle, not leave it stuck",
                PlusPurchaseState.Idle,
                coordinator.state.value.purchase,
            )
        }

    @Test
    fun `Pending purchase state resets to Idle when entitlement emits active Plus`() = runTest {
        val entitlementRepo = FakeEntitlementRepository()
        val coordinator = coordinator(
            TestBillingGateway(
                flow {
                    emit(PurchaseOutcome.Pending)
                    awaitCancellation()
                },
            ),
            entitlementRepo,
        )
        coordinator.refreshCatalog()
        advanceUntilIdle()

        val purchaseJob = launch { coordinator.purchase(PlusPlanId.Monthly) }
        runCurrent()

        assertEquals(
            "billing Pending outcome should set coordinator purchase to Pending",
            PlusPurchaseState.Pending,
            coordinator.state.value.purchase,
        )

        entitlementRepo.seed(activeEntitlement())
        runCurrent()

        assertEquals(
            "active Plus entitlement arriving while Pending must reset purchase to Idle",
            PlusPurchaseState.Idle,
            coordinator.state.value.purchase,
        )

        purchaseJob.cancel()
    }

    // endregion

    // region critical fixes from independent-critic review (stale state across screen visits)

    @Test
    fun `refreshCatalog resets a stalled AwaitingEntitlement purchase to Idle on next screen visit`() = runTest {
        val entitlementRepo = FakeEntitlementRepository()
        val coordinator = coordinator(
            TestBillingGateway(flowOf(PurchaseOutcome.Purchased("plus_monthly", "tok-monthly", 0L))),
            entitlementRepo,
        )
        coordinator.refreshCatalog()
        advanceUntilIdle()

        val purchaseJob = launch { coordinator.purchase(PlusPlanId.Monthly) }
        advanceUntilIdle()
        purchaseJob.cancel()

        assertEquals(
            "reconciliation must exhaust retries and land on AwaitingEntitlement when entitlement never confirms",
            PlusPurchaseState.AwaitingEntitlement,
            coordinator.state.value.purchase,
        )

        // The coordinator is a Singleton: this simulates the user leaving the Paywall screen
        // (destroying the ViewModel) and reopening it later (a fresh ViewModel calling
        // refreshCatalog() on init, same as the pre-extraction per-instance ViewModel did).
        coordinator.refreshCatalog()
        advanceUntilIdle()

        assertEquals(
            "reopening the screen must not leave the user permanently unable to purchase",
            PlusPurchaseState.Idle,
            coordinator.state.value.purchase,
        )
    }

    @Test
    fun `refreshCatalog resets a stalled Pending purchase to Idle on next screen visit`() = runTest {
        val coordinator = coordinator(
            TestBillingGateway(
                flow {
                    emit(PurchaseOutcome.Pending)
                    awaitCancellation()
                },
            ),
        )
        coordinator.refreshCatalog()
        advanceUntilIdle()

        val purchaseJob = launch { coordinator.purchase(PlusPlanId.Monthly) }
        runCurrent()
        assertEquals(PlusPurchaseState.Pending, coordinator.state.value.purchase)
        purchaseJob.cancel()

        coordinator.refreshCatalog()
        advanceUntilIdle()

        assertEquals(
            "a family-approval Pending purchase abandoned by the user must not block the paywall forever",
            PlusPurchaseState.Idle,
            coordinator.state.value.purchase,
        )
    }

    @Test
    fun `refreshCatalog does not disturb an actively reconciling purchase`() = runTest {
        val entitlementRepo = FakeEntitlementRepository()
        val coordinator = coordinator(
            TestBillingGateway(flowOf(PurchaseOutcome.Purchased("plus_monthly", "tok-monthly", 0L))),
            entitlementRepo,
        )
        coordinator.refreshCatalog()
        advanceUntilIdle()

        val purchaseJob = launch { coordinator.purchase(PlusPlanId.Monthly) }
        runCurrent()

        assertEquals(
            "reconciliation should be live (not yet exhausted) right after a Purchased outcome",
            PlusPurchaseState.ReconcilingEntitlement,
            coordinator.state.value.purchase,
        )

        coordinator.refreshCatalog()
        runCurrent()

        assertEquals(
            "a refresh triggered while reconciliation is actively in flight must not reset it out from under itself",
            PlusPurchaseState.ReconcilingEntitlement,
            coordinator.state.value.purchase,
        )

        purchaseJob.cancel()
    }

    // endregion

    // region helpers

    private fun TestScope.coordinator(
        billing: BillingGateway = FakeBillingGateway(),
        entitlementRepo: FakeEntitlementRepository = FakeEntitlementRepository(),
    ) = PlusSubscriptionCoordinatorImpl(
        billingGateway = billing,
        observeEntitlement = ObserveEntitlementUseCase(entitlementRepo),
        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        // TestScope.backgroundScope does not get pumped by advanceUntilIdle() in this
        // project's coroutines-test setup (verified empirically) — a manually built scope
        // on the same testScheduler behaves correctly. StandardTestDispatcher (not Unconfined)
        // keeps queued work cooperatively interleaved with the state-collector coroutine so
        // transient StateFlow values (e.g. InProgress) are observable instead of being skipped.
        applicationScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob()),
    )

    private fun activeEntitlement() =
        UserEntitlement.Plus(
            source = EntitlementSource.SUBSCRIPTION_MONTHLY,
            state = EntitlementState.ACTIVE,
            startsAt = Instant.parse("2026-08-01T00:00:00Z"),
            expiresAt = Instant.parse("2026-09-01T00:00:00Z"),
            graceEndsAt = null,
        )

    private class FakeEntitlementRepository(
        initialEntitlement: UserEntitlement = UserEntitlement.Free,
    ) : EntitlementRepository {
        private val _entitlement = MutableStateFlow<UserEntitlement>(initialEntitlement)
        override val entitlement: StateFlow<UserEntitlement> = _entitlement.asStateFlow()

        var refreshCalls: Int = 0
        var onRefresh: () -> Unit = {}

        override suspend fun refresh(): Result<Unit> {
            refreshCalls++
            onRefresh()
            return Result.success(Unit)
        }

        fun seed(entitlement: UserEntitlement) {
            _entitlement.value = entitlement
        }
    }

    private class TestBillingGateway(
        private val subscriptionFlow: Flow<PurchaseOutcome>,
    ) : BillingGateway {
        override fun availability(): Flow<BillingAvailability> = flowOf(BillingAvailability.Available)

        override fun products(): Result<List<SupportProduct>> = Result.success(emptyList())

        override fun purchase(productId: String): Flow<PurchaseOutcome> = flowOf(PurchaseOutcome.Cancelled)

        override fun resolvePendingPurchases(): Result<List<PurchaseOutcome>> = Result.success(emptyList())

        override fun querySubscriptions(): Result<List<SupportProduct>> =
            Result.success(
                listOf(
                    SupportProduct(PlusPlanId.Monthly.productId, "€1.99 / month", "Monthly"),
                    SupportProduct(PlusPlanId.Yearly.productId, "€12.99 / year", "Yearly"),
                ),
            )

        override fun launchSubscriptionFlow(productId: String): Flow<PurchaseOutcome> = subscriptionFlow

        override suspend fun acknowledge(purchaseToken: String): Result<Unit> = Result.success(Unit)

        override fun resolveSubscriptionPurchases(): Result<List<PurchaseOutcome>> = Result.success(emptyList())
    }

    // endregion
}
