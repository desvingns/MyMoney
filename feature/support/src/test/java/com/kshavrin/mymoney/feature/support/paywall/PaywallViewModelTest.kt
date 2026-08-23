package com.kshavrin.mymoney.feature.support.paywall

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.kshavrin.mymoney.core.domain.analytics.AnalyticsEvent
import com.kshavrin.mymoney.core.domain.analytics.AnalyticsGateway
import com.kshavrin.mymoney.core.domain.billing.PlusCatalogState
import com.kshavrin.mymoney.core.domain.billing.PlusPlanId
import com.kshavrin.mymoney.core.domain.billing.PlusPurchaseOutcome
import com.kshavrin.mymoney.core.domain.billing.PlusSubscriptionCoordinator
import com.kshavrin.mymoney.core.domain.billing.PlusSubscriptionState
import com.kshavrin.mymoney.core.testing.fake.FakeAnalyticsGateway
import com.kshavrin.mymoney.core.ui.navigation.PaywallEntryPoint
import com.kshavrin.mymoney.feature.support.util.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric supplies a real android.os.Bundle so savedStateHandle.toRoute<Destinations.Paywall>()
// can decode its route args; the android.jar stub throws "not mocked" at VM construction (SPEC-19).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PaywallViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val viewModelStore = ViewModelStore()

    @After
    fun tearDown() {
        viewModelStore.clear()
    }

    @Test
    fun `paywall shown analytics event is logged once with the route entry point`() = runTest {
        val analytics = FakeAnalyticsGateway()

        createViewModel(analytics = analytics)
        runCurrent()

        assertEquals(
            listOf(AnalyticsEvent.PaywallShown(PaywallEntryPoint.SupportSection.name)),
            analytics.events,
        )
    }

    @Test
    fun `coordinator prices map to PaywallPlan list through PlusPlanId to PaywallPlanId boundary`() = runTest {
        val coordinator = FakePlusSubscriptionCoordinator()
        val viewModel = createViewModel(coordinator)

        coordinator.emitState(
            PlusSubscriptionState(
                catalog = PlusCatalogState.Available,
                prices =
                    mapOf(
                        PlusPlanId.Monthly to "€1.99 / month",
                        PlusPlanId.Yearly to "€12.99 / year",
                    ),
            ),
        )
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
    fun `back event emits a one-shot NavigateBack action`() = runTest {
        val viewModel = createViewModel()
        runCurrent()

        var action: PaywallAction? = null
        val collector = launch { viewModel.actions.collect { action = it } }
        runCurrent()

        viewModel.onEvent(PaywallEvent.BackClicked)
        runCurrent()
        collector.cancel()

        assertEquals(PaywallAction.NavigateBack, action)
        assertTrue(viewModel.actions.replayCache.isEmpty())
    }

    @Test
    fun `PlanSelected maps PaywallPlanId to domain PlusPlanId before calling coordinator`() = runTest {
        val coordinator = FakePlusSubscriptionCoordinator()
        val viewModel = createViewModel(coordinator)
        runCurrent()

        viewModel.onEvent(PaywallEvent.PlanSelected(PaywallPlanId.Monthly))
        runCurrent()
        assertEquals(PlusPlanId.Monthly, coordinator.lastPurchasedPlanId)

        viewModel.onEvent(PaywallEvent.PlanSelected(PaywallPlanId.Yearly))
        runCurrent()
        assertEquals(PlusPlanId.Yearly, coordinator.lastPurchasedPlanId)
    }

    @Test
    fun `successful purchase emits RequestNotificationPermission action`() = runTest {
        val coordinator =
            FakePlusSubscriptionCoordinator().apply {
                purchaseOutcome = PlusPurchaseOutcome.Purchased
            }
        val viewModel = createViewModel(coordinator)
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

    @Test
    fun `Failed purchase outcome sets an error message in state`() = runTest {
        val coordinator =
            FakePlusSubscriptionCoordinator().apply {
                purchaseOutcome = PlusPurchaseOutcome.Failed
            }
        val viewModel = createViewModel(coordinator)
        runCurrent()

        viewModel.onEvent(PaywallEvent.PlanSelected(PaywallPlanId.Monthly))
        advanceUntilIdle()

        assertNotNull(
            "errorMessageRes must be set after a Failed purchase outcome",
            viewModel.state.value.errorMessageRes,
        )
    }

    @Test
    fun `retry clicked delegates to coordinator refreshCatalog`() = runTest {
        val coordinator = FakePlusSubscriptionCoordinator()
        val viewModel = createViewModel(coordinator)
        runCurrent()
        val callsBeforeRetry = coordinator.refreshCatalogCalls

        viewModel.onEvent(PaywallEvent.RetryClicked)
        runCurrent()

        assertEquals(callsBeforeRetry + 1, coordinator.refreshCatalogCalls)
    }

    private fun createViewModel(
        coordinator: FakePlusSubscriptionCoordinator = FakePlusSubscriptionCoordinator(),
        analytics: AnalyticsGateway = FakeAnalyticsGateway(),
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): PaywallViewModel {
        val viewModel =
            PaywallViewModel(
                coordinator = coordinator,
                analytics = analytics,
                savedStateHandle = savedStateHandle,
            )
        viewModelStore.put("paywall", viewModel)
        return viewModel
    }

    private class FakePlusSubscriptionCoordinator : PlusSubscriptionCoordinator {
        private val _state = MutableStateFlow(PlusSubscriptionState())
        override val state: StateFlow<PlusSubscriptionState> = _state.asStateFlow()

        var purchaseOutcome: PlusPurchaseOutcome = PlusPurchaseOutcome.Cancelled
        var refreshCatalogCalls: Int = 0
        var lastPurchasedPlanId: PlusPlanId? = null

        override suspend fun refreshCatalog() {
            refreshCatalogCalls++
        }

        override suspend fun purchase(planId: PlusPlanId): PlusPurchaseOutcome {
            lastPurchasedPlanId = planId
            return purchaseOutcome
        }

        fun emitState(state: PlusSubscriptionState) {
            _state.value = state
        }
    }
}
