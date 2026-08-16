package com.kshavrin.mymoney.feature.support.paywall

import androidx.lifecycle.ViewModelStore
import com.kshavrin.mymoney.core.domain.billing.BillingAvailability
import com.kshavrin.mymoney.core.domain.billing.BillingGateway
import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import com.kshavrin.mymoney.core.domain.billing.SupportProduct
import com.kshavrin.mymoney.core.domain.model.EntitlementSource
import com.kshavrin.mymoney.core.domain.model.EntitlementState
import com.kshavrin.mymoney.core.domain.model.UserEntitlement
import com.kshavrin.mymoney.core.domain.usecase.ObserveEntitlementUseCase
import com.kshavrin.mymoney.feature.support.util.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class PaywallViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val viewModelStore = ViewModelStore()

    @After
    fun tearDown() {
        viewModelStore.clear()
    }

    @Test
    fun `catalog loads both subscription plans with Play prices`() = runTest {
        val billing = billing()
        val viewModel = createViewModel(billing, FakeEntitlementRepository())

        runCurrent()

        assertEquals(PaywallCatalogState.Available, viewModel.state.value.catalogState)
        assertEquals(
            listOf(
                PaywallPlan(PaywallPlanId.Monthly, "€1.99 / month"),
                PaywallPlan(PaywallPlanId.Yearly, "€12.99 / year"),
            ),
            viewModel.state.value.plans,
        )
    }

    @Test
    fun `purchased plan refreshes entitlement and returns to idle after active confirmation`() = runTest {
        val entitlementRepository = FakeEntitlementRepository()
        val activeEntitlement = subscriptionEntitlement(EntitlementState.ACTIVE)
        entitlementRepository.onRefresh = { entitlementRepository.seed(activeEntitlement) }
        val viewModel = createViewModel(
            billing(
                PurchaseOutcome.Purchased(
                    productId = PaywallPlanId.Monthly.productId,
                    purchaseToken = "monthly-token",
                    purchasedAtMillis = 1_700_000_000_000L,
                ),
            ),
            entitlementRepository,
        )

        runCurrent()
        viewModel.onEvent(PaywallEvent.PlanSelected(PaywallPlanId.Monthly))
        advanceUntilIdle()

        assertEquals(PaywallPurchaseState.Idle, viewModel.state.value.purchaseState)
        assertEquals(activeEntitlement, viewModel.state.value.entitlement)
        assertEquals(1, entitlementRepository.refreshCalls)
    }

    @Test
    fun `purchased plan waits for entitlement and resumes when repository publishes Plus`() = runTest {
        val entitlementRepository = FakeEntitlementRepository()
        val viewModel = createViewModel(
            billing(
                PurchaseOutcome.Purchased(
                    productId = PaywallPlanId.Monthly.productId,
                    purchaseToken = "monthly-token",
                    purchasedAtMillis = 1_700_000_000_000L,
                ),
            ),
            entitlementRepository,
        )

        runCurrent()
        viewModel.onEvent(PaywallEvent.PlanSelected(PaywallPlanId.Monthly))
        advanceUntilIdle()

        assertEquals(PaywallPurchaseState.AwaitingEntitlement, viewModel.state.value.purchaseState)
        assertEquals(4, entitlementRepository.refreshCalls)

        entitlementRepository.seed(subscriptionEntitlement(EntitlementState.ACTIVE))
        runCurrent()

        assertEquals(PaywallPurchaseState.Idle, viewModel.state.value.purchaseState)
    }

    @Test
    fun `double tapping a plan launches only one subscription flow`() = runTest {
        val billing = RecordingBillingGateway()
        val viewModel = createViewModel(billing, FakeEntitlementRepository())

        runCurrent()
        viewModel.onEvent(PaywallEvent.PlanSelected(PaywallPlanId.Monthly))
        runCurrent()
        viewModel.onEvent(PaywallEvent.PlanSelected(PaywallPlanId.Yearly))
        runCurrent()

        assertEquals(listOf(PaywallPlanId.Monthly.productId), billing.subscriptionLaunches)
        assertEquals(PaywallPurchaseState.InProgress, viewModel.state.value.purchaseState)
    }

    @Test
    fun `back event emits a one-shot navigation action`() = runTest {
        val viewModel = createViewModel(billing(), FakeEntitlementRepository())
        runCurrent()

        var action: PaywallAction? = null
        val collector = launch {
            viewModel.actions.collect { action = it }
        }
        runCurrent()
        viewModel.onEvent(PaywallEvent.BackClicked)
        runCurrent()
        collector.cancel()

        assertEquals(PaywallAction.NavigateBack, action)
        assertTrue(viewModel.actions.replayCache.isEmpty())
    }

    @Test
    fun `successful purchase emits RequestNotificationPermission action`() = runTest {
        val entitlementRepository = FakeEntitlementRepository()
        val viewModel =
            createViewModel(
                billing(
                    PurchaseOutcome.Purchased(
                        productId = PaywallPlanId.Monthly.productId,
                        purchaseToken = "monthly-token",
                        purchasedAtMillis = 1_700_000_000_000L,
                    ),
                ),
                entitlementRepository,
            )
        runCurrent()

        val collectedActions = mutableListOf<PaywallAction>()
        val collector = launch { viewModel.actions.collect { collectedActions += it } }
        runCurrent()

        viewModel.onEvent(PaywallEvent.PlanSelected(PaywallPlanId.Monthly))
        advanceUntilIdle()
        collector.cancel()

        assertTrue(
            "Expected RequestNotificationPermission in $collectedActions",
            PaywallAction.RequestNotificationPermission in collectedActions,
        )
    }

    private fun createViewModel(
        billingGateway: BillingGateway,
        entitlementRepository: FakeEntitlementRepository,
    ): PaywallViewModel {
        val viewModel =
            PaywallViewModel(
                billingGateway = billingGateway,
                observeEntitlement = ObserveEntitlementUseCase(entitlementRepository),
                ioDispatcher = mainDispatcherRule.testDispatcher,
            )
        viewModelStore.put("paywall", viewModel)
        return viewModel
    }

    private fun billing(outcome: PurchaseOutcome? = null): com.kshavrin.mymoney.core.testing.fake.FakeBillingGateway =
        com.kshavrin.mymoney.core.testing.fake.FakeBillingGateway().apply {
            seedAvailability(BillingAvailability.Available)
            seedSubscriptions(
                SupportProduct(PaywallPlanId.Monthly.productId, "€1.99 / month", "Monthly"),
                SupportProduct(PaywallPlanId.Yearly.productId, "€12.99 / year", "Yearly"),
            )
            outcome?.let { seedSubscriptionOutcome(PaywallPlanId.Monthly.productId, it) }
        }

    private fun subscriptionEntitlement(state: EntitlementState) =
        UserEntitlement.Plus(
            source = EntitlementSource.SUBSCRIPTION_MONTHLY,
            state = state,
            startsAt = Instant.parse("2026-08-14T00:00:00Z"),
            expiresAt = Instant.parse("2026-09-14T00:00:00Z"),
            graceEndsAt = null,
        )

    private class RecordingBillingGateway : BillingGateway {
        private val availabilityState = MutableStateFlow<BillingAvailability>(BillingAvailability.Available)
        private val subscriptionFlow = MutableSharedFlow<PurchaseOutcome>(extraBufferCapacity = 1)

        val subscriptionLaunches = mutableListOf<String>()

        override fun availability(): StateFlow<BillingAvailability> = availabilityState.asStateFlow()

        override fun products(): Result<List<SupportProduct>> = Result.success(emptyList())

        override fun purchase(productId: String): Flow<PurchaseOutcome> = flowOf(PurchaseOutcome.Cancelled)

        override fun resolvePendingPurchases(): Result<List<PurchaseOutcome>> = Result.success(emptyList())

        override fun querySubscriptions(): Result<List<SupportProduct>> =
            Result.success(
                listOf(
                    SupportProduct(PaywallPlanId.Monthly.productId, "€1.99 / month", "Monthly"),
                    SupportProduct(PaywallPlanId.Yearly.productId, "€12.99 / year", "Yearly"),
                ),
            )

        override fun launchSubscriptionFlow(productId: String): Flow<PurchaseOutcome> {
            subscriptionLaunches += productId
            return subscriptionFlow
        }

        override suspend fun acknowledge(purchaseToken: String): Result<Unit> = Result.success(Unit)

        override fun resolveSubscriptionPurchases(): Result<List<PurchaseOutcome>> = Result.success(emptyList())
    }
}
