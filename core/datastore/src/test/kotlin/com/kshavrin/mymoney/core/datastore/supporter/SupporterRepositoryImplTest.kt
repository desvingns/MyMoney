package com.kshavrin.mymoney.core.datastore.supporter

import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.domain.billing.COFFEE_LARGE_PRODUCT_ID
import com.kshavrin.mymoney.core.domain.billing.COFFEE_SMALL_PRODUCT_ID
import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import com.kshavrin.mymoney.core.domain.supporter.SupporterState
import com.kshavrin.mymoney.core.testing.fake.FakeAppSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SupporterRepositoryImplTest {
    @Test
    fun `state backfills a legacy count once without overwriting later split purchases`() =
        runTest {
            val appSettingsRepository =
                FakeAppSettingsRepository(
                    AppSettings(
                        supporterBadgeEarned = true,
                        supportPurchaseCount = 2,
                    ),
                )
            val repository = SupporterRepositoryImpl(appSettingsRepository)

            assertEquals(
                SupporterState(
                    badgeEarned = true,
                    purchaseCount = 2,
                    smallCoffeeCount = 2,
                    largeCoffeeCount = 0,
                    hasSupportActivity = true,
                ),
                repository.state().first(),
            )
            assertEquals(true, appSettingsRepository.current().supportPurchaseSplitBackfilled)
            assertEquals(true, appSettingsRepository.current().supporterActivityRecorded)

            repository.recordPurchase(purchasedOutcome(COFFEE_LARGE_PRODUCT_ID, "large-token")).getOrThrow()

            assertEquals(
                SupporterState(
                    badgeEarned = true,
                    purchaseCount = 3,
                    smallCoffeeCount = 2,
                    largeCoffeeCount = 1,
                    hasSupportActivity = true,
                ),
                repository.state().first(),
            )
        }

    @Test
    fun `fresh install keeps split counters at zero`() =
        runTest {
            val appSettingsRepository = FakeAppSettingsRepository()
            val repository = SupporterRepositoryImpl(appSettingsRepository)

            assertEquals(
                SupporterState(badgeEarned = false, purchaseCount = 0),
                repository.state().first(),
            )
            assertEquals(0, appSettingsRepository.current().supportPurchaseCountSmall)
            assertEquals(0, appSettingsRepository.current().supportPurchaseCountLarge)
            assertEquals(true, appSettingsRepository.current().supportPurchaseSplitBackfilled)
        }

    @Test
    fun `small and large purchases increment their respective counters`() =
        runTest {
            val appSettingsRepository = FakeAppSettingsRepository()
            val repository = SupporterRepositoryImpl(appSettingsRepository)

            repository.recordPurchase(purchasedOutcome(COFFEE_SMALL_PRODUCT_ID, "small-token")).getOrThrow()

            assertEquals(
                SupporterState(
                    badgeEarned = true,
                    purchaseCount = 1,
                    smallCoffeeCount = 1,
                    largeCoffeeCount = 0,
                    hasSupportActivity = true,
                ),
                repository.state().first(),
            )

            repository.recordPurchase(purchasedOutcome(COFFEE_LARGE_PRODUCT_ID, "large-token")).getOrThrow()

            assertEquals(
                SupporterState(
                    badgeEarned = true,
                    purchaseCount = 2,
                    smallCoffeeCount = 1,
                    largeCoffeeCount = 1,
                    hasSupportActivity = true,
                ),
                repository.state().first(),
            )
        }

    @Test
    fun `unknown product increments only the total count`() =
        runTest {
            val appSettingsRepository = FakeAppSettingsRepository()
            val repository = SupporterRepositoryImpl(appSettingsRepository)

            repository.recordPurchase(purchasedOutcome(productId = "support_tip", token = "unknown-token")).getOrThrow()

            assertEquals(
                SupporterState(
                    badgeEarned = true,
                    purchaseCount = 1,
                    smallCoffeeCount = 0,
                    largeCoffeeCount = 0,
                    hasSupportActivity = true,
                ),
                repository.state().first(),
            )
        }

    @Test
    fun `replaying the same purchase token does not increment any counter`() =
        runTest {
            val appSettingsRepository = FakeAppSettingsRepository()
            val repository = SupporterRepositoryImpl(appSettingsRepository)
            val outcome = purchasedOutcome(COFFEE_SMALL_PRODUCT_ID, "token")

            repository.recordPurchase(outcome).getOrThrow()
            repository.recordPurchase(outcome).getOrThrow()

            assertEquals(
                SupporterState(
                    badgeEarned = true,
                    purchaseCount = 1,
                    smallCoffeeCount = 1,
                    largeCoffeeCount = 0,
                    hasSupportActivity = true,
                ),
                repository.state().first(),
            )
            assertEquals(setOf("token"), appSettingsRepository.current().supporterPurchaseTokens)
        }

    @Test
    fun `merge remote keeps the larger local count and earned badge`() =
        runTest {
            val appSettingsRepository =
                FakeAppSettingsRepository(
                    AppSettings(
                        supporterBadgeEarned = true,
                        supportPurchaseCount = 3,
                    ),
                )
            val repository = SupporterRepositoryImpl(appSettingsRepository)

            repository.mergeRemote(remoteCount = 1, remoteBadge = false).getOrThrow()

            assertEquals(
                SupporterState(
                    badgeEarned = true,
                    purchaseCount = 3,
                    smallCoffeeCount = 3,
                    largeCoffeeCount = 0,
                    hasSupportActivity = true,
                ),
                repository.state().first(),
            )
        }

    @Test
    fun `merge remote raises local state when the remote has newer supporter data`() =
        runTest {
            val appSettingsRepository = FakeAppSettingsRepository()
            val repository = SupporterRepositoryImpl(appSettingsRepository)

            repository.mergeRemote(remoteCount = 4, remoteBadge = true).getOrThrow()

            assertEquals(
                SupporterState(badgeEarned = true, purchaseCount = 4, hasSupportActivity = true),
                repository.state().first(),
            )
        }

    @Test
    fun `recordSupportActivity is monotonic and survives a remote merge with no activity`() =
        runTest {
            val appSettingsRepository = FakeAppSettingsRepository()
            val repository = SupporterRepositoryImpl(appSettingsRepository)

            assertEquals(false, repository.state().first().hasSupportActivity)

            repository.recordSupportActivity().getOrThrow()
            repository.mergeRemote(remoteCount = 0, remoteBadge = false).getOrThrow()

            assertEquals(true, repository.state().first().hasSupportActivity)
            assertEquals(true, appSettingsRepository.current().supporterActivityRecorded)
        }

    @Test
    fun `remote badge or count backfills support activity`() =
        runTest {
            listOf(
                0 to true,
                2 to false,
            ).forEach { (remoteCount, remoteBadge) ->
                val appSettingsRepository = FakeAppSettingsRepository()
                val repository = SupporterRepositoryImpl(appSettingsRepository)

                repository.mergeRemote(remoteCount, remoteBadge).getOrThrow()

                assertEquals(true, repository.state().first().hasSupportActivity)
                assertEquals(true, appSettingsRepository.current().supporterActivityRecorded)
            }
        }

    private fun purchasedOutcome(
        productId: String,
        token: String,
    ) =
        PurchaseOutcome.Purchased(
            productId = productId,
            purchaseToken = token,
            purchasedAtMillis = 1L,
        )
}
