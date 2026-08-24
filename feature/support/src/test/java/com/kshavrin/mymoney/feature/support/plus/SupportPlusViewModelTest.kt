package com.kshavrin.mymoney.feature.support.plus

import androidx.lifecycle.ViewModelStore
import com.kshavrin.mymoney.core.domain.analytics.AnalyticsEvent
import com.kshavrin.mymoney.core.domain.billing.PlusCatalogState
import com.kshavrin.mymoney.core.domain.billing.PlusPlanId
import com.kshavrin.mymoney.core.domain.billing.PlusPurchaseOutcome
import com.kshavrin.mymoney.core.domain.billing.PlusSubscriptionCoordinator
import com.kshavrin.mymoney.core.domain.billing.PlusSubscriptionState
import com.kshavrin.mymoney.core.domain.model.EntitlementSource
import com.kshavrin.mymoney.core.domain.model.EntitlementState
import com.kshavrin.mymoney.core.domain.model.UserEntitlement
import com.kshavrin.mymoney.core.testing.fake.FakeAnalyticsGateway
import com.kshavrin.mymoney.core.ui.navigation.PaywallEntryPoint
import com.kshavrin.mymoney.feature.support.paywall.PaywallCatalogState
import com.kshavrin.mymoney.feature.support.paywall.PaywallPlan
import com.kshavrin.mymoney.feature.support.paywall.PaywallPlanId
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

// Robolectric supplies a real android.os.Bundle so hiltViewModel() navigation arguments work;
// the android.jar stub throws "not mocked" in plain JUnit (see SPEC-19 memory entry).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SupportPlusViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val viewModelStore = ViewModelStore()

    @After
    fun tearDown() {
        viewModelStore.clear()
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private fun createViewModel(
        coordinator: FakePlusSubscriptionCoordinator = FakePlusSubscriptionCoordinator(),
        analytics: FakeAnalyticsGateway = FakeAnalyticsGateway(),
    ): SupportPlusViewModel {
        val viewModel = SupportPlusViewModel(coordinator = coordinator, analytics = analytics)
        viewModelStore.put("support_plus", viewModel)
        return viewModel
    }

    private fun activeSubscriptionEntitlement(): UserEntitlement.Plus =
        UserEntitlement.Plus(
            source = EntitlementSource.SUBSCRIPTION_MONTHLY,
            state = EntitlementState.ACTIVE,
            startsAt = Instant.parse("2026-08-01T00:00:00Z"),
            expiresAt = Instant.parse("2026-09-01T00:00:00Z"),
            graceEndsAt = null,
        )

    // ─── Analytics ──────────────────────────────────────────────────────────────

    @Test
    fun `paywall shown analytics event is logged once with SupportSection entry point`() =
        runTest {
            val analytics = FakeAnalyticsGateway()
            createViewModel(analytics = analytics)
            runCurrent()

            assertEquals(
                listOf(AnalyticsEvent.PaywallShown(PaywallEntryPoint.SupportSection.name)),
                analytics.events,
            )
        }

    // ─── Initial state ───────────────────────────────────────────────────────────

    @Test
    fun `initial state reflects coordinator loading catalog and free entitlement`() =
        runTest {
            val coordinator = FakePlusSubscriptionCoordinator()
            val viewModel = createViewModel(coordinator)

            assertEquals(PaywallCatalogState.Loading, viewModel.state.value.catalogState)
            assertEquals(UserEntitlement.Free, viewModel.state.value.entitlement)
            assertNull(viewModel.state.value.errorMessageRes)
        }

    // ─── Catalog state mapping ───────────────────────────────────────────────────

    @Test
    fun `coordinator available catalog with prices maps to Available state and PaywallPlan list`() =
        runTest {
            val coordinator = FakePlusSubscriptionCoordinator()
            val viewModel = createViewModel(coordinator)

            coordinator.emitState(
                PlusSubscriptionState(
                    catalog = PlusCatalogState.Available,
                    prices =
                        mapOf(
                            PlusPlanId.Monthly to "€2.49 / month",
                            PlusPlanId.Yearly to "€19.99 / year",
                        ),
                ),
            )
            runCurrent()

            assertEquals(PaywallCatalogState.Available, viewModel.state.value.catalogState)
            assertEquals(
                listOf(
                    PaywallPlan(PaywallPlanId.Monthly, "€2.49 / month"),
                    PaywallPlan(PaywallPlanId.Yearly, "€19.99 / year"),
                ),
                viewModel.state.value.plans,
            )
        }

    @Test
    fun `coordinator error catalog maps to PaywallCatalogState Error`() =
        runTest {
            val coordinator = FakePlusSubscriptionCoordinator()
            val viewModel = createViewModel(coordinator)

            coordinator.emitState(PlusSubscriptionState(catalog = PlusCatalogState.Error))
            runCurrent()

            assertEquals(PaywallCatalogState.Error, viewModel.state.value.catalogState)
        }

    // ─── Entitled status ─────────────────────────────────────────────────────────

    @Test
    fun `active Plus entitlement from coordinator is surfaced in SupportPlusState`() =
        runTest {
            val coordinator = FakePlusSubscriptionCoordinator()
            val viewModel = createViewModel(coordinator)

            val entitlement = activeSubscriptionEntitlement()
            coordinator.emitState(PlusSubscriptionState(entitlement = entitlement))
            runCurrent()

            assertEquals(entitlement, viewModel.state.value.entitlement)
        }

    // ─── onPlanSelected ─────────────────────────────────────────────────────────

    @Test
    fun `onPlanSelected Monthly maps PaywallPlanId Monthly to PlusPlanId Monthly in coordinator`() =
        runTest {
            val coordinator = FakePlusSubscriptionCoordinator()
            val viewModel = createViewModel(coordinator)
            runCurrent()

            viewModel.onPlanSelected(PaywallPlanId.Monthly)
            runCurrent()

            assertEquals(PlusPlanId.Monthly, coordinator.lastPurchasedPlanId)
        }

    @Test
    fun `onPlanSelected Yearly maps PaywallPlanId Yearly to PlusPlanId Yearly in coordinator`() =
        runTest {
            val coordinator = FakePlusSubscriptionCoordinator()
            val viewModel = createViewModel(coordinator)
            runCurrent()

            viewModel.onPlanSelected(PaywallPlanId.Yearly)
            runCurrent()

            assertEquals(PlusPlanId.Yearly, coordinator.lastPurchasedPlanId)
        }

    // ─── Purchase outcomes ───────────────────────────────────────────────────────

    @Test
    fun `successful purchase emits RequestNotificationPermission one-shot action`() =
        runTest {
            val coordinator =
                FakePlusSubscriptionCoordinator().apply {
                    purchaseOutcome = PlusPurchaseOutcome.Purchased
                }
            val viewModel = createViewModel(coordinator)
            runCurrent()

            val collectedActions = mutableListOf<SupportPlusAction>()
            val collector = launch { viewModel.actions.collect { collectedActions += it } }
            runCurrent()

            viewModel.onPlanSelected(PaywallPlanId.Monthly)
            advanceUntilIdle()
            collector.cancel()

            assertTrue(
                "Expected RequestNotificationPermission in $collectedActions",
                SupportPlusAction.RequestNotificationPermission in collectedActions,
            )
        }

    @Test
    fun `failed purchase outcome sets errorMessageRes in state`() =
        runTest {
            val coordinator =
                FakePlusSubscriptionCoordinator().apply {
                    purchaseOutcome = PlusPurchaseOutcome.Failed
                }
            val viewModel = createViewModel(coordinator)
            runCurrent()

            viewModel.onPlanSelected(PaywallPlanId.Monthly)
            advanceUntilIdle()

            assertNotNull(
                "errorMessageRes must be set after a Failed purchase outcome",
                viewModel.state.value.errorMessageRes,
            )
        }

    @Test
    fun `cancelled purchase outcome leaves errorMessageRes null`() =
        runTest {
            val coordinator =
                FakePlusSubscriptionCoordinator().apply {
                    purchaseOutcome = PlusPurchaseOutcome.Cancelled
                }
            val viewModel = createViewModel(coordinator)
            runCurrent()

            viewModel.onPlanSelected(PaywallPlanId.Monthly)
            advanceUntilIdle()

            assertNull(viewModel.state.value.errorMessageRes)
        }

    // ─── onRetryClicked ──────────────────────────────────────────────────────────

    @Test
    fun `onRetryClicked delegates to coordinator refreshCatalog`() =
        runTest {
            val coordinator = FakePlusSubscriptionCoordinator()
            val viewModel = createViewModel(coordinator)
            runCurrent()
            val callsBefore = coordinator.refreshCatalogCalls

            viewModel.onRetryClicked()
            runCurrent()

            assertEquals(callsBefore + 1, coordinator.refreshCatalogCalls)
        }

    // ─── Action replay contract ──────────────────────────────────────────────────

    @Test
    fun `actions SharedFlow has replay 0 — late subscriber misses past emissions`() =
        runTest {
            val coordinator =
                FakePlusSubscriptionCoordinator().apply {
                    purchaseOutcome = PlusPurchaseOutcome.Purchased
                }
            val viewModel = createViewModel(coordinator)
            runCurrent()

            viewModel.onPlanSelected(PaywallPlanId.Monthly)
            advanceUntilIdle()

            val lateCollected = mutableListOf<SupportPlusAction>()
            val collector = launch { viewModel.actions.collect { lateCollected += it } }
            runCurrent()
            collector.cancel()

            assertTrue(
                "Late subscriber must not receive replayed actions but got $lateCollected",
                lateCollected.isEmpty(),
            )
        }

    // ─── Fake ───────────────────────────────────────────────────────────────────

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
