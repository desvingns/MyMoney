package com.kshavrin.mymoney.core.datastore.supporter

import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import com.kshavrin.mymoney.core.domain.supporter.SupporterState
import com.kshavrin.mymoney.core.testing.fake.FakeAppSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SupporterRepositoryImplTest {
    @Test
    fun `state exposes persisted supporter badge and purchase count`() =
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
                SupporterState(badgeEarned = true, purchaseCount = 2),
                repository.state().first(),
            )
        }

    @Test
    fun `purchased outcome earns badge and increments purchase count`() =
        runTest {
            val appSettingsRepository = FakeAppSettingsRepository()
            val repository = SupporterRepositoryImpl(appSettingsRepository)

            repository.recordPurchase(purchasedOutcome()).getOrThrow()

            assertEquals(
                SupporterState(badgeEarned = true, purchaseCount = 1),
                repository.state().first(),
            )
            assertEquals(setOf("token"), appSettingsRepository.current().supporterPurchaseTokens)
        }

    @Test
    fun `subsequent purchased outcome increments an existing count`() =
        runTest {
            val appSettingsRepository =
                FakeAppSettingsRepository(
                    AppSettings(
                        supporterBadgeEarned = true,
                        supportPurchaseCount = 2,
                        supporterPurchaseTokens = setOf("previous-token"),
                    ),
                )
            val repository = SupporterRepositoryImpl(appSettingsRepository)

            repository.recordPurchase(purchasedOutcome()).getOrThrow()

            assertEquals(
                SupporterState(badgeEarned = true, purchaseCount = 3),
                repository.state().first(),
            )
            assertEquals(
                setOf("previous-token", "token"),
                appSettingsRepository.current().supporterPurchaseTokens,
            )
        }

    @Test
    fun `replaying the same purchase token does not increment the local count`() =
        runTest {
            val appSettingsRepository =
                FakeAppSettingsRepository(
                    AppSettings(
                        supporterBadgeEarned = true,
                        supportPurchaseCount = 2,
                        supporterPurchaseTokens = setOf("token"),
                    ),
                )
            val repository = SupporterRepositoryImpl(appSettingsRepository)

            repository.recordPurchase(purchasedOutcome()).getOrThrow()

            assertEquals(
                SupporterState(badgeEarned = true, purchaseCount = 2),
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
                SupporterState(badgeEarned = true, purchaseCount = 3),
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
                SupporterState(badgeEarned = true, purchaseCount = 4),
                repository.state().first(),
            )
        }

    private fun purchasedOutcome() =
        PurchaseOutcome.Purchased(
            productId = "support_tip",
            purchaseToken = "token",
            purchasedAtMillis = 1L,
        )
}
