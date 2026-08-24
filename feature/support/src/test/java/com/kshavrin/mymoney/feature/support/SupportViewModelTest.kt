package com.kshavrin.mymoney.feature.support

import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.ads.AdRewardState
import com.kshavrin.mymoney.core.domain.analytics.AnalyticsEvent
import com.kshavrin.mymoney.core.domain.billing.BillingAvailability
import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import com.kshavrin.mymoney.core.domain.billing.SupportProduct
import com.kshavrin.mymoney.core.domain.model.EntitlementSource
import com.kshavrin.mymoney.core.domain.model.EntitlementState
import com.kshavrin.mymoney.core.domain.model.UserEntitlement
import com.kshavrin.mymoney.core.domain.supporter.SupportPurchaseReconciliationCoordinator
import com.kshavrin.mymoney.core.domain.supporter.SupportPurchaseReconciliationState
import com.kshavrin.mymoney.core.domain.supporter.SupporterRepository
import com.kshavrin.mymoney.core.domain.supporter.SupporterState
import com.kshavrin.mymoney.core.domain.usecase.ObserveAdRewardStateUseCase
import com.kshavrin.mymoney.core.domain.usecase.ObserveEntitlementUseCase
import com.kshavrin.mymoney.core.domain.usecase.ObserveSupporterStateUseCase
import com.kshavrin.mymoney.core.domain.usecase.RecordSupportActivityUseCase
import com.kshavrin.mymoney.core.testing.fake.FakeAdRewardRepository
import com.kshavrin.mymoney.core.testing.fake.FakeAnalyticsGateway
import com.kshavrin.mymoney.core.testing.fake.FakeBillingGateway
import com.kshavrin.mymoney.core.testing.fake.FakeSupporterRepository
import com.kshavrin.mymoney.feature.support.paywall.FakeEntitlementRepository
import com.kshavrin.mymoney.feature.support.util.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class SupportViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val viewModelStore = ViewModelStore()

    @After
    fun tearDown() {
        viewModelStore.clear()
    }

    @Test
    fun `initial screen open logs analytics through the gateway`() =
        runTest {
            val fixtures = fixtures()
            createViewModel(fixtures)
            runCurrent()

            assertEquals(listOf(AnalyticsEvent.SupportOpened), fixtures.analytics.events)
        }

    @Test
    fun `without ad reward observer keeps ads watched total at zero`() =
        runTest {
            val viewModel = createViewModel(fixtures())

            runCurrent()

            assertEquals(0, viewModel.state.value.adsWatchedTotal)
        }

    @Test
    fun `fresh supporter with absent ad reward keeps all support counters at zero`() =
        runTest {
            val fixtures =
                fixtures(
                    supporterState = SupporterState(badgeEarned = false, purchaseCount = 0),
                    rewardState = null,
                )
            val viewModel = createViewModel(fixtures, observeAdRewardState = true)

            runCurrent()

            assertEquals(0, viewModel.state.value.adsWatchedTotal)
            assertFalse(viewModel.state.value.hasSupportActivity)
            assertEquals(0, fixtures.supporter.recordSupportActivityCalls)
            assertEquals(
                SupporterState(badgeEarned = false, purchaseCount = 0),
                viewModel.state.value.supporterState,
            )
        }

    @Test
    fun `fresh supporter with present ad reward exposes total watched`() =
        runTest {
            val fixtures =
                fixtures(
                    supporterState = SupporterState(badgeEarned = false, purchaseCount = 0),
                    rewardState = adRewardState(totalWatched = 7),
                )
            val viewModel = createViewModel(fixtures, observeAdRewardState = true)

            runCurrent()

            assertEquals(7, viewModel.state.value.adsWatchedTotal)
            assertTrue(viewModel.state.value.hasSupportActivity)
            assertEquals(1, fixtures.supporter.recordSupportActivityCalls)
            assertEquals(
                SupporterState(badgeEarned = false, purchaseCount = 0, hasSupportActivity = true),
                viewModel.state.value.supporterState,
            )
        }

    @Test
    fun `zero ad reward total does not record support activity`() =
        runTest {
            val fixtures = fixtures(rewardState = adRewardState(totalWatched = 0))
            val viewModel = createViewModel(fixtures, observeAdRewardState = true)

            runCurrent()

            assertFalse(viewModel.state.value.hasSupportActivity)
            assertEquals(0, fixtures.supporter.recordSupportActivityCalls)
        }

    @Test
    fun `active monthly and yearly subscriptions record support activity`() =
        runTest {
            listOf(
                EntitlementSource.SUBSCRIPTION_MONTHLY,
                EntitlementSource.SUBSCRIPTION_YEARLY,
            ).forEach { source ->
                viewModelStore.clear()
                val fixtures = fixtures(entitlement = plusEntitlement(source, EntitlementState.ACTIVE))
                val viewModel = createViewModel(fixtures)

                runCurrent()

                assertTrue("$source should open the support card", viewModel.state.value.hasSupportActivity)
                assertEquals(1, fixtures.supporter.recordSupportActivityCalls)
            }
        }

    @Test
    fun `non qualifying entitlement states and sources do not record support activity`() =
        runTest {
            listOf(
                plusEntitlement(EntitlementSource.AD_REWARD, EntitlementState.ACTIVE),
                plusEntitlement(EntitlementSource.WHITELIST, EntitlementState.ACTIVE),
                plusEntitlement(EntitlementSource.SUBSCRIPTION_MONTHLY, EntitlementState.EXPIRED),
                plusEntitlement(EntitlementSource.SUBSCRIPTION_MONTHLY, EntitlementState.TRIAL),
                plusEntitlement(EntitlementSource.SUBSCRIPTION_MONTHLY, EntitlementState.GRACE),
            ).forEach { entitlement ->
                viewModelStore.clear()
                val fixtures = fixtures(entitlement = entitlement)
                val viewModel = createViewModel(fixtures)

                runCurrent()

                assertFalse("$entitlement must not open the support card", viewModel.state.value.hasSupportActivity)
                assertEquals(0, fixtures.supporter.recordSupportActivityCalls)
            }
        }

    @Test
    fun `ad reward state emissions update watched total without reopening the screen`() =
        runTest {
            val fixtures = fixtures(rewardState = adRewardState(totalWatched = 7))
            val viewModel = createViewModel(fixtures, observeAdRewardState = true)

            runCurrent()
            assertEquals(7, viewModel.state.value.adsWatchedTotal)

            fixtures.adRewards.seedState(adRewardState(totalWatched = 8))
            runCurrent()

            assertEquals(8, viewModel.state.value.adsWatchedTotal)
        }

    @Test
    fun `supporter with purchases and absent ad reward preserves coffee counters and zero ads`() =
        runTest {
            val supporterState =
                SupporterState(
                    badgeEarned = true,
                    purchaseCount = 3,
                    smallCoffeeCount = 2,
                    largeCoffeeCount = 1,
                    hasSupportActivity = true,
                )
            val fixtures = fixtures(supporterState = supporterState, rewardState = null)
            val viewModel = createViewModel(fixtures, observeAdRewardState = true)

            runCurrent()

            assertEquals(0, viewModel.state.value.adsWatchedTotal)
            assertEquals(supporterState, viewModel.state.value.supporterState)
        }

    @Test
    fun `supporter with purchases and present ad reward exposes all support counters`() =
        runTest {
            val supporterState =
                SupporterState(
                    badgeEarned = true,
                    purchaseCount = 5,
                    smallCoffeeCount = 3,
                    largeCoffeeCount = 2,
                    hasSupportActivity = true,
                )
            val fixtures =
                fixtures(
                    supporterState = supporterState,
                    rewardState = adRewardState(totalWatched = 42),
                )
            val viewModel = createViewModel(fixtures, observeAdRewardState = true)

            runCurrent()

            assertEquals(42, viewModel.state.value.adsWatchedTotal)
            assertEquals(supporterState, viewModel.state.value.supporterState)
        }

    @Test
    fun `available billing exposes only coffee products in stable order`() =
        runTest {
            val small = SupportProduct(COFFEE_SMALL_PRODUCT_ID, "£1.99", "Small")
            val large = SupportProduct(COFFEE_LARGE_PRODUCT_ID, "£4.99", "Large")
            val irrelevant = SupportProduct("plus_monthly", "£2.99", "Plus")
            val fixtures = fixtures(products = listOf(large, irrelevant, small))
            val viewModel = createViewModel(fixtures)

            runCurrent()

            assertEquals(SupportBillingState.Available, viewModel.state.value.billingState)
            assertEquals(listOf(small, large), viewModel.state.value.products)
            assertFalse(viewModel.state.value.isPurchaseInProgress)
        }

    @Test
    fun `disabled build keeps the explanation distinct from region unavailability`() =
        runTest {
            val fixtures = fixtures(availability = BillingAvailability.DisabledInBuild)
            val viewModel = createViewModel(fixtures)

            runCurrent()

            assertEquals(
                SupportBillingState.Unavailable(SupportUnavailableReason.DisabledInBuild),
                viewModel.state.value.billingState,
            )
            assertTrue(
                viewModel.state.value.products
                    .isEmpty(),
            )
        }

    @Test
    fun `device and region billing restrictions map to their own user-facing states`() =
        runTest {
            val deviceViewModel =
                createViewModel(fixtures(availability = BillingAvailability.UnavailableOnDevice))
            runCurrent()
            assertEquals(
                SupportBillingState.Unavailable(SupportUnavailableReason.UnavailableOnDevice),
                deviceViewModel.state.value.billingState,
            )

            viewModelStore.clear()

            val regionViewModel =
                createViewModel(fixtures(availability = BillingAvailability.UnavailableInRegion))
            runCurrent()
            assertEquals(
                SupportBillingState.Unavailable(SupportUnavailableReason.UnavailableInRegion),
                regionViewModel.state.value.billingState,
            )
        }

    @Test
    fun `service and network availability failures show the retryable network state`() =
        runTest {
            val serviceViewModel =
                createViewModel(fixtures(availability = BillingAvailability.ServiceUnavailable))
            runCurrent()
            assertEquals(SupportBillingState.NetworkError, serviceViewModel.state.value.billingState)

            viewModelStore.clear()

            val networkViewModel =
                createViewModel(fixtures(availability = BillingAvailability.NetworkUnavailable))
            runCurrent()
            assertEquals(SupportBillingState.NetworkError, networkViewModel.state.value.billingState)
        }

    @Test
    fun `unknown billing failure is presented as generic unavailability`() =
        runTest {
            val viewModel =
                createViewModel(fixtures(availability = BillingAvailability.UnknownFailure(7)))

            runCurrent()

            assertEquals(
                SupportBillingState.Unavailable(SupportUnavailableReason.Unavailable),
                viewModel.state.value.billingState,
            )
        }

    @Test
    fun `product loading failure becomes a retryable network state`() =
        runTest {
            val fixtures = fixtures().apply { billing.seedProductsFailure(IllegalStateException("offline")) }
            val viewModel = createViewModel(fixtures)

            runCurrent()

            assertEquals(SupportBillingState.NetworkError, viewModel.state.value.billingState)
        }

    @Test
    fun `reconciliation pending state suppresses availability and remains pending`() =
        runTest {
            val fixtures =
                fixtures(
                    reconciliationState = SupportPurchaseReconciliationState.Pending,
                    products = listOf(SupportProduct(COFFEE_SMALL_PRODUCT_ID, "£1.99", "Small")),
                )
            val viewModel = createViewModel(fixtures)

            runCurrent()

            assertEquals(SupportBillingState.Pending, viewModel.state.value.billingState)
            assertFalse(viewModel.state.value.hasSupportActivity)
            assertEquals(0, fixtures.supporter.recordSupportActivityCalls)
            assertTrue(
                viewModel.state.value.products
                    .isEmpty(),
            )
        }

    @Test
    fun `back event emits one-shot navigation action`() =
        runTest {
            val viewModel = createViewModel(fixtures())
            runCurrent()

            viewModel.actions.test {
                viewModel.onEvent(SupportEvent.BackClicked)
                assertEquals(SupportAction.NavigateBack, awaitItem())
                assertTrue(viewModel.actions.replayCache.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `resuming the screen asks reconciliation to refresh billing`() =
        runTest {
            val fixtures = fixtures()
            val viewModel = createViewModel(fixtures)
            runCurrent()
            val callsAfterCreation = fixtures.coordinator.reconcileCalls

            viewModel.onScreenResumed()
            runCurrent()

            assertEquals(callsAfterCreation + 1, fixtures.coordinator.reconcileCalls)
        }

    @Test
    fun `purchased outcome records supporter state and logs start and completion`() =
        runTest {
            val product = SupportProduct(COFFEE_SMALL_PRODUCT_ID, "£1.99", "Small")
            val outcome =
                PurchaseOutcome.Purchased(
                    productId = product.id,
                    purchaseToken = "token-1",
                    purchasedAtMillis = 1_700_000_000_000L,
                )
            val fixtures = fixtures(products = listOf(product), purchaseOutcome = outcome)
            val viewModel = createViewModel(fixtures)
            runCurrent()

            viewModel.onEvent(SupportEvent.PurchaseClicked(product.id))
            runCurrent()

            assertEquals(SupportBillingState.Available, viewModel.state.value.billingState)
            assertFalse(viewModel.state.value.isPurchaseInProgress)
            assertEquals(listOf(outcome), fixtures.coordinator.recordedPurchases)
            assertEquals(
                SupporterState(
                    badgeEarned = true,
                    purchaseCount = 1,
                    smallCoffeeCount = 1,
                    largeCoffeeCount = 0,
                    hasSupportActivity = true,
                ),
                viewModel.state.value.supporterState,
            )
            assertTrue(viewModel.state.value.hasSupportActivity)
            assertEquals(
                listOf(
                    AnalyticsEvent.SupportOpened,
                    AnalyticsEvent.SupportPurchaseStarted(product.id),
                    AnalyticsEvent.SupportPurchaseCompleted(product.id, "purchased"),
                ),
                fixtures.analytics.events,
            )
        }

    @Test
    fun `cancelled purchase restores available state and logs a cancelled completion`() =
        runTest {
            val product = SupportProduct(COFFEE_SMALL_PRODUCT_ID, "£1.99", "Small")
            val fixtures = fixtures(products = listOf(product), purchaseOutcome = PurchaseOutcome.Cancelled)
            val viewModel = createViewModel(fixtures)
            runCurrent()

            viewModel.onEvent(SupportEvent.PurchaseClicked(product.id))
            runCurrent()

            assertEquals(SupportBillingState.Available, viewModel.state.value.billingState)
            assertEquals(
                AnalyticsEvent.SupportPurchaseCompleted(product.id, "cancelled"),
                fixtures.analytics.events.last(),
            )
        }

    @Test
    fun `terminal purchase completion permits the next purchase`() =
        runTest {
            val product = SupportProduct(COFFEE_SMALL_PRODUCT_ID, "Â£1.99", "Small")
            val fixtures = fixtures(products = listOf(product), purchaseOutcome = PurchaseOutcome.Cancelled)
            val viewModel = createViewModel(fixtures)
            runCurrent()

            viewModel.onEvent(SupportEvent.PurchaseClicked(product.id))
            runCurrent()
            viewModel.onEvent(SupportEvent.PurchaseClicked(product.id))
            runCurrent()

            assertFalse(viewModel.state.value.isPurchaseInProgress)
            assertEquals(
                listOf(
                    AnalyticsEvent.SupportOpened,
                    AnalyticsEvent.SupportPurchaseStarted(product.id),
                    AnalyticsEvent.SupportPurchaseCompleted(product.id, "cancelled"),
                    AnalyticsEvent.SupportPurchaseStarted(product.id),
                    AnalyticsEvent.SupportPurchaseCompleted(product.id, "cancelled"),
                ),
                fixtures.analytics.events,
            )
        }

    @Test
    fun `network purchase outcome shows retryable error without recording a supporter purchase`() =
        runTest {
            val product = SupportProduct(COFFEE_SMALL_PRODUCT_ID, "£1.99", "Small")
            val fixtures = fixtures(products = listOf(product), purchaseOutcome = PurchaseOutcome.NetworkError)
            val viewModel = createViewModel(fixtures)
            runCurrent()

            viewModel.onEvent(SupportEvent.PurchaseClicked(product.id))
            runCurrent()

            assertEquals(SupportBillingState.NetworkError, viewModel.state.value.billingState)
            assertTrue(fixtures.coordinator.recordedPurchases.isEmpty())
            assertEquals(
                AnalyticsEvent.SupportPurchaseCompleted(product.id, "network_error"),
                fixtures.analytics.events.last(),
            )
        }

    @Test
    fun `pending purchase never logs terminal completion and stays pending`() =
        runTest {
            val product = SupportProduct(COFFEE_SMALL_PRODUCT_ID, "£1.99", "Small")
            val fixtures = fixtures(products = listOf(product), purchaseOutcome = PurchaseOutcome.Pending)
            val viewModel = createViewModel(fixtures)
            runCurrent()

            viewModel.onEvent(SupportEvent.PurchaseClicked(product.id))
            runCurrent()

            assertEquals(SupportBillingState.Pending, viewModel.state.value.billingState)
            assertFalse(viewModel.state.value.hasSupportActivity)
            assertEquals(0, fixtures.supporter.recordSupportActivityCalls)
            assertEquals(
                listOf(
                    AnalyticsEvent.SupportOpened,
                    AnalyticsEvent.SupportPurchaseStarted(product.id),
                ),
                fixtures.analytics.events,
            )
        }

    @Test
    fun `unavailable purchase returns to available state and logs an unavailable completion`() =
        runTest {
            val product = SupportProduct(COFFEE_SMALL_PRODUCT_ID, "Â£1.99", "Small")
            val fixtures =
                fixtures(
                    products = listOf(product),
                    purchaseOutcome = PurchaseOutcome.Unavailable("billing_unavailable"),
                )
            val viewModel = createViewModel(fixtures)
            runCurrent()

            viewModel.onEvent(SupportEvent.PurchaseClicked(product.id))
            runCurrent()

            assertEquals(SupportBillingState.Available, viewModel.state.value.billingState)
            assertFalse(viewModel.state.value.isPurchaseInProgress)
            assertEquals(
                AnalyticsEvent.SupportPurchaseCompleted(product.id, "unavailable"),
                fixtures.analytics.events.last(),
            )
        }

    private fun createViewModel(
        fixtures: Fixtures,
        observeAdRewardState: Boolean = false,
    ): SupportViewModel {
        val viewModel =
            SupportViewModel(
                billingGateway = fixtures.billing,
                observeSupporterStateUseCase = ObserveSupporterStateUseCase(fixtures.supporter),
                observeAdRewardStateUseCase =
                    if (observeAdRewardState) {
                        ObserveAdRewardStateUseCase(fixtures.adRewards)
                    } else {
                        null
                    },
                observeEntitlementUseCase = ObserveEntitlementUseCase(fixtures.entitlements),
                recordSupportActivityUseCase = RecordSupportActivityUseCase(fixtures.supporter),
                supportPurchaseReconciliationCoordinator = fixtures.coordinator,
                analyticsGateway = fixtures.analytics,
                ioDispatcher = mainDispatcherRule.testDispatcher,
            )
        viewModelStore.put("support", viewModel)
        return viewModel
    }

    private fun fixtures(
        availability: BillingAvailability = BillingAvailability.Available,
        products: List<SupportProduct> = emptyList(),
        purchaseOutcome: PurchaseOutcome? = null,
        reconciliationState: SupportPurchaseReconciliationState = SupportPurchaseReconciliationState.Ready,
        supporterState: SupporterState = SupporterState(badgeEarned = false, purchaseCount = 0),
        rewardState: AdRewardState? = null,
        entitlement: UserEntitlement = UserEntitlement.Free,
    ): Fixtures {
        val billing =
            FakeBillingGateway().apply {
                seedAvailability(availability)
                seedProducts(*products.toTypedArray())
                if (purchaseOutcome != null) {
                    products.forEach { product -> seedPurchaseOutcome(product.id, purchaseOutcome) }
                }
            }
        val supporter = ActivityAwareSupporterRepository(initialState = supporterState)
        return Fixtures(
            billing = billing,
            supporter = supporter,
            adRewards = FakeAdRewardRepository(initialState = rewardState),
            entitlements = FakeEntitlementRepository(initialEntitlement = entitlement),
            coordinator =
                FakeSupportPurchaseReconciliationCoordinator(
                    initialState = reconciliationState,
                    supporterRepository = supporter,
                ),
            analytics = FakeAnalyticsGateway(),
        )
    }

    private data class Fixtures(
        val billing: FakeBillingGateway,
        val supporter: ActivityAwareSupporterRepository,
        val adRewards: FakeAdRewardRepository,
        val entitlements: FakeEntitlementRepository,
        val coordinator: FakeSupportPurchaseReconciliationCoordinator,
        val analytics: FakeAnalyticsGateway,
    )

    private fun adRewardState(totalWatched: Int) =
        AdRewardState(
            progress = 2,
            required = 5,
            frozen = false,
            frozenReason = null,
            plusActive = false,
            plusProvider = null,
            plusExpiresAt = null,
            totalWatched = totalWatched,
        )

    private fun plusEntitlement(
        source: EntitlementSource,
        state: EntitlementState,
    ) =
        UserEntitlement.Plus(
            source = source,
            state = state,
            startsAt = Instant.parse("2026-08-01T00:00:00Z"),
            expiresAt = Instant.parse("2026-09-01T00:00:00Z"),
            graceEndsAt = null,
        )

    private class FakeSupportPurchaseReconciliationCoordinator(
        initialState: SupportPurchaseReconciliationState,
        private val supporterRepository: SupporterRepository,
    ) : SupportPurchaseReconciliationCoordinator {
        private val stateFlow = MutableStateFlow(initialState)

        override val state: StateFlow<SupportPurchaseReconciliationState> = stateFlow.asStateFlow()
        var reconcileCalls = 0
        var recordResult: Result<Unit> = Result.success(Unit)
        val recordedPurchases = mutableListOf<PurchaseOutcome.Purchased>()

        override suspend fun reconcile() {
            reconcileCalls++
        }

        override suspend fun recordPurchase(outcome: PurchaseOutcome.Purchased): Result<Unit> {
            recordedPurchases += outcome
            if (recordResult.isFailure) return recordResult
            return supporterRepository.recordPurchase(outcome)
        }
    }

    private class ActivityAwareSupporterRepository(
        initialState: SupporterState,
    ) : SupporterRepository {
        private val delegate = FakeSupporterRepository(initialState)
        private val activityRecorded =
            MutableStateFlow(
                initialState.hasSupportActivity ||
                    initialState.badgeEarned ||
                    initialState.purchaseCount > 0 ||
                    initialState.smallCoffeeCount > 0 ||
                    initialState.largeCoffeeCount > 0,
            )

        var recordSupportActivityCalls = 0
            private set

        override fun state(): Flow<SupporterState> =
            combine(delegate.state(), activityRecorded) { state, recorded ->
                state.copy(hasSupportActivity = state.hasSupportActivity || recorded)
            }

        override suspend fun recordPurchase(outcome: PurchaseOutcome.Purchased): Result<Unit> {
            val result = delegate.recordPurchase(outcome)
            if (result.isSuccess) activityRecorded.value = true
            return result
        }

        override suspend fun recordSupportActivity(): Result<Unit> {
            recordSupportActivityCalls++
            activityRecorded.value = true
            return Result.success(Unit)
        }

        override suspend fun mergeRemote(
            remoteCount: Int,
            remoteBadge: Boolean,
        ): Result<Unit> {
            val result = delegate.mergeRemote(remoteCount, remoteBadge)
            if (result.isSuccess && (remoteCount > 0 || remoteBadge)) activityRecorded.value = true
            return result
        }
    }
}
