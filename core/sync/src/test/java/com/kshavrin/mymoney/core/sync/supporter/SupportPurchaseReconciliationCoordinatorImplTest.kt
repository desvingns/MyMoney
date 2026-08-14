package com.kshavrin.mymoney.core.sync.supporter

import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import com.kshavrin.mymoney.core.domain.supporter.SupportPurchaseReconciliationState
import com.kshavrin.mymoney.core.testing.fake.FakeBillingGateway
import com.kshavrin.mymoney.core.testing.fake.FakeSupporterRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportPurchaseReconciliationCoordinatorImplTest {
    @Test
    fun `repeated restoration records the same purchase only once`() =
        runTest {
            val outcome = purchasedOutcome()
            val billing = FakeBillingGateway().apply { seedPendingPurchases(outcome) }
            val supporter = FakeSupporterRepository()
            val coordinator =
                SupportPurchaseReconciliationCoordinatorImpl(
                    billingGateway = billing,
                    supporterRepository = supporter,
                    dispatcher = UnconfinedTestDispatcher(testScheduler),
                )

            coordinator.reconcile()
            coordinator.reconcile()

            assertEquals(1, supporter.state().first().purchaseCount)
            assertTrue(supporter.state().first().badgeEarned)
            assertEquals(SupportPurchaseReconciliationState.Ready, coordinator.state.value)
        }

    @Test
    fun `pending restoration keeps reconciliation pending`() =
        runTest {
            val billing = FakeBillingGateway().apply { seedPendingPurchases(PurchaseOutcome.Pending) }
            val coordinator = coordinator(billing)

            coordinator.reconcile()

            assertEquals(SupportPurchaseReconciliationState.Pending, coordinator.state.value)
        }

    @Test
    fun `billing resolution failure exposes a network error`() =
        runTest {
            val billing = FakeBillingGateway().apply { seedPendingPurchasesFailure(IllegalStateException("offline")) }
            val coordinator = coordinator(billing)

            coordinator.reconcile()

            assertEquals(SupportPurchaseReconciliationState.NetworkError, coordinator.state.value)
        }

    @Test
    fun `network outcome from billing exposes a network error`() =
        runTest {
            val billing = FakeBillingGateway().apply { seedPendingPurchases(PurchaseOutcome.NetworkError) }
            val coordinator = coordinator(billing)

            coordinator.reconcile()

            assertEquals(SupportPurchaseReconciliationState.NetworkError, coordinator.state.value)
        }

    private fun coordinator(billing: FakeBillingGateway) =
        SupportPurchaseReconciliationCoordinatorImpl(
            billingGateway = billing,
            supporterRepository = FakeSupporterRepository(),
            dispatcher = UnconfinedTestDispatcher(),
        )

    private fun purchasedOutcome() =
        PurchaseOutcome.Purchased(
            productId = "coffee_small",
            purchaseToken = "token-1",
            purchasedAtMillis = 1_700_000_000_000L,
        )
}
