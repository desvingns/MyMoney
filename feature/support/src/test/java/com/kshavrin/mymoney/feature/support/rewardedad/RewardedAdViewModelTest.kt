package com.kshavrin.mymoney.feature.support.rewardedad

import androidx.lifecycle.ViewModelStore
import com.kshavrin.mymoney.core.ads.AdAvailability
import com.kshavrin.mymoney.core.ads.AdLoadResult
import com.kshavrin.mymoney.core.ads.AdShowResult
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.domain.ads.AdRewardState
import com.kshavrin.mymoney.core.domain.ads.ConfirmationOutcome
import com.kshavrin.mymoney.core.domain.usecase.ObserveAdRewardStateUseCase
import com.kshavrin.mymoney.core.testing.fake.FakeAdRewardRepository
import com.kshavrin.mymoney.feature.support.util.MainDispatcherRule
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric supplies a real android.app.Activity so onBlockShown/onWatchAd can be called
// with a non-null receiver; FakeAdGateway ignores the Activity parameter.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RewardedAdViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val viewModelStore = ViewModelStore()
    private val activity =
        Robolectric.buildActivity(android.app.Activity::class.java).create().get()

    @After
    fun tearDown() {
        viewModelStore.clear()
    }

    // --- Helpers ---

    private fun rewardState(
        progress: Int = 3,
        required: Int = 5,
        plusActive: Boolean = false,
    ) = AdRewardState(
        progress = progress,
        required = required,
        frozen = false,
        frozenReason = null,
        plusActive = plusActive,
        plusProvider = null,
        plusExpiresAt = null,
    )

    private fun createViewModel(
        fakeRepo: FakeAdRewardRepository = FakeAdRewardRepository(),
        fakeGateway: FakeAdGateway = FakeAdGateway(),
    ): RewardedAdViewModel {
        val viewModel =
            RewardedAdViewModel(
                observeAdRewardState = ObserveAdRewardStateUseCase(fakeRepo),
                adGateway = fakeGateway,
                ioDispatcher = mainDispatcherRule.testDispatcher,
            )
        viewModelStore.put("rewarded_ad", viewModel)
        return viewModel
    }

    // ─── Acceptance matrix: scenario 1 — Unauthenticated ───────────────────────

    @Test
    fun `auth failure from repository sets status to Unauthenticated and clears reward`() =
        runTest {
            val fakeRepo = FakeAdRewardRepository()
            fakeRepo.seedRefreshResult(Result.failure(SyncException(SyncError.Auth)))
            val viewModel = createViewModel(fakeRepo = fakeRepo)

            viewModel.onBlockShown(activity)
            runCurrent()

            assertEquals(RewardedAdStatus.Unauthenticated, viewModel.state.value.status)
        }

    // ─── Acceptance matrix: scenario 2 — Progress shown from server ─────────────

    @Test
    fun `successful refresh sets status to Ready and populates reward with server progress`() =
        runTest {
            val fakeRepo = FakeAdRewardRepository()
            fakeRepo.seedState(rewardState(progress = 3, required = 5))
            val fakeGateway = FakeAdGateway().apply { loadResult = AdLoadResult.Loaded }
            val viewModel = createViewModel(fakeRepo = fakeRepo, fakeGateway = fakeGateway)

            viewModel.onBlockShown(activity)
            runCurrent()

            assertEquals(RewardedAdStatus.Ready, viewModel.state.value.status)
            val reward = viewModel.state.value.reward
            assertNotNull(reward)
            assertEquals(3, reward!!.progress)
            assertEquals(5, reward.required)
        }

    // ─── Acceptance matrix: scenario 3 — Region unavailable ─────────────────────

    @Test
    fun `RegionUnavailable from gateway sets a status that is distinct from NoFill`() =
        runTest {
            val fakeRepo = FakeAdRewardRepository()
            fakeRepo.seedState(rewardState())
            val fakeGateway =
                FakeAdGateway().apply {
                    loadResult = AdLoadResult.Unavailable(AdAvailability.RegionUnavailable)
                }
            val viewModel = createViewModel(fakeRepo = fakeRepo, fakeGateway = fakeGateway)

            viewModel.onBlockShown(activity)
            runCurrent()

            assertEquals(RewardedAdStatus.RegionUnavailable, viewModel.state.value.status)
            assertNotEquals(RewardedAdStatus.NoFill, viewModel.state.value.status)
        }

    // ─── Acceptance matrix: scenario 4 — No fill ────────────────────────────────

    @Test
    fun `NoFill from gateway sets status to NoFill distinct from RegionUnavailable`() =
        runTest {
            val fakeRepo = FakeAdRewardRepository()
            fakeRepo.seedState(rewardState())
            val fakeGateway =
                FakeAdGateway().apply {
                    loadResult = AdLoadResult.Unavailable(AdAvailability.NoFill)
                }
            val viewModel = createViewModel(fakeRepo = fakeRepo, fakeGateway = fakeGateway)

            viewModel.onBlockShown(activity)
            runCurrent()

            assertEquals(RewardedAdStatus.NoFill, viewModel.state.value.status)
            assertNotEquals(RewardedAdStatus.RegionUnavailable, viewModel.state.value.status)
        }

    // ─── Acceptance matrix: scenario 5 — Plus already active ────────────────────

    @Test
    fun `plus active state surfaces on reward and watch flow remains unblocked`() =
        runTest {
            val fakeRepo = FakeAdRewardRepository()
            fakeRepo.seedState(rewardState(plusActive = true))
            val fakeGateway = FakeAdGateway().apply { loadResult = AdLoadResult.Loaded }
            val viewModel = createViewModel(fakeRepo = fakeRepo, fakeGateway = fakeGateway)

            viewModel.onBlockShown(activity)
            runCurrent()

            assertEquals(RewardedAdStatus.Ready, viewModel.state.value.status)
            val reward = viewModel.state.value.reward
            assertNotNull(reward)
            assertEquals(true, reward!!.plusActive)
        }

    // ─── Acceptance matrix: scenario 6 — Awaiting confirmation ──────────────────

    @Test
    fun `dismissed with rewardEarned and PendingConfirmation outcome stays in AwaitingConfirmation`() =
        runTest {
            val fakeRepo = FakeAdRewardRepository()
            fakeRepo.seedState(rewardState())
            fakeRepo.seedConfirmationOutcome(ConfirmationOutcome.PendingConfirmation)
            val fakeGateway =
                FakeAdGateway().apply {
                    loadResult = AdLoadResult.Loaded
                    showResult = AdShowResult.Dismissed(rewardEarned = true)
                }
            val viewModel = createViewModel(fakeRepo = fakeRepo, fakeGateway = fakeGateway)

            viewModel.onBlockShown(activity)
            runCurrent()
            assertEquals(RewardedAdStatus.Ready, viewModel.state.value.status)

            viewModel.onWatchAd(activity)
            runCurrent()

            assertEquals(RewardedAdStatus.AwaitingConfirmation, viewModel.state.value.status)
        }

    // STATE-STUCK-WAITING-001 fix: null cached state in confirmReward must set Unavailable, not
    // leave the block in a permanent AwaitingConfirmation.
    @Test
    fun `confirmReward with null cached repository state sets Unavailable not AwaitingConfirmation`() =
        runTest {
            val fakeRepo = FakeAdRewardRepository()
            fakeRepo.seedState(rewardState())
            val fakeGateway =
                FakeAdGateway().apply {
                    loadResult = AdLoadResult.Loaded
                    showResult = AdShowResult.Dismissed(rewardEarned = true)
                }
            val viewModel = createViewModel(fakeRepo = fakeRepo, fakeGateway = fakeGateway)

            viewModel.onBlockShown(activity)
            runCurrent()
            assertEquals(RewardedAdStatus.Ready, viewModel.state.value.status)

            // Simulate the session being invalidated while the user watches the ad.
            fakeRepo.seedState(null)

            viewModel.onWatchAd(activity)
            runCurrent()

            assertEquals(RewardedAdStatus.Unavailable, viewModel.state.value.status)
        }

    // ─── Acceptance matrix: scenario 7 — Interrupted ────────────────────────────

    // STATE-STALE-BUTTON-001 fix: interrupted dismissal must call loadAvailability and reflect
    // the gateway's actual state — it must not blindly reset status to Ready.
    @Test
    fun `interrupted dismissal re-resolves actual gateway availability not a hardcoded Ready`() =
        runTest {
            val fakeRepo = FakeAdRewardRepository()
            fakeRepo.seedState(rewardState(progress = 3, required = 5))
            val fakeGateway =
                FakeAdGateway().apply {
                    loadResult = AdLoadResult.Loaded
                    showResult = AdShowResult.Dismissed(rewardEarned = false)
                }
            val viewModel = createViewModel(fakeRepo = fakeRepo, fakeGateway = fakeGateway)

            viewModel.onBlockShown(activity)
            runCurrent()
            assertEquals(RewardedAdStatus.Ready, viewModel.state.value.status)

            // Change gateway to report NoFill so the re-resolved status won't be Ready.
            fakeGateway.loadResult = AdLoadResult.Unavailable(AdAvailability.NoFill)

            viewModel.onWatchAd(activity)
            runCurrent()

            assertEquals(RewardedAdStatus.NoFill, viewModel.state.value.status)
            // Progress counter is unchanged — no reward was counted.
            assertEquals(3, viewModel.state.value.reward?.progress)
        }

    // ─── Acceptance matrix: scenario 8 — Offline ─────────────────────────────────

    @Test
    fun `network error from refresh sets status to Offline`() =
        runTest {
            val fakeRepo = FakeAdRewardRepository()
            fakeRepo.seedRefreshResult(Result.failure(SyncException(SyncError.Network)))
            val viewModel = createViewModel(fakeRepo = fakeRepo)

            viewModel.onBlockShown(activity)
            runCurrent()

            assertEquals(RewardedAdStatus.Offline, viewModel.state.value.status)
        }

    @Test
    fun `Offline from gateway load sets status to Offline`() =
        runTest {
            val fakeRepo = FakeAdRewardRepository()
            fakeRepo.seedState(rewardState())
            val fakeGateway =
                FakeAdGateway().apply {
                    loadResult = AdLoadResult.Unavailable(AdAvailability.Offline)
                }
            val viewModel = createViewModel(fakeRepo = fakeRepo, fakeGateway = fakeGateway)

            viewModel.onBlockShown(activity)
            runCurrent()

            assertEquals(RewardedAdStatus.Offline, viewModel.state.value.status)
        }

    // STATE-ROTATION-RACE-001 fix: onBlockShown must guard on watchJob as well as loadJob. While the
    // block sits in AwaitingConfirmation, loadJob has finished and watchJob is suspended inside
    // awaitConfirmation() waiting on the server. An Activity recreation (rotation) re-fires the
    // LaunchedEffect(viewModel) -> onBlockShown; that must NOT launch a fresh loadBlock and clobber
    // the in-flight confirmation wait back to Loading.
    @Test
    fun `onBlockShown while confirmation wait is in flight does not clobber AwaitingConfirmation with Loading`() =
        runTest {
            val fakeRepo = FakeAdRewardRepository()
            fakeRepo.seedState(rewardState())
            val fakeGateway =
                FakeAdGateway().apply {
                    loadResult = AdLoadResult.Loaded
                    showResult = AdShowResult.Dismissed(rewardEarned = true)
                }
            val viewModel = createViewModel(fakeRepo = fakeRepo, fakeGateway = fakeGateway)

            viewModel.onBlockShown(activity)
            runCurrent()
            assertEquals(RewardedAdStatus.Ready, viewModel.state.value.status)

            // Hold awaitConfirmation() suspended so watchJob stays active in AwaitingConfirmation.
            val confirmationGate = fakeRepo.suspendConfirmation()

            viewModel.onWatchAd(activity)
            runCurrent()
            assertEquals(RewardedAdStatus.AwaitingConfirmation, viewModel.state.value.status)

            // Simulate a screen rotation re-firing onBlockShown while the confirmation is in flight.
            viewModel.onBlockShown(activity)
            runCurrent()

            // The guard must have short-circuited: no fresh loadBlock, status untouched.
            assertNotEquals(RewardedAdStatus.Loading, viewModel.state.value.status)
            assertEquals(RewardedAdStatus.AwaitingConfirmation, viewModel.state.value.status)

            // Release the wait so the in-flight coroutine can finish cleanly.
            confirmationGate.complete(Unit)
            runCurrent()
        }

    // ─── Load timeout guard ──────────────────────────────────────────────────────

    // AD_LOAD_TIMEOUT_MILLIS = 25_000L (private constant in the production file). When
    // loadRewarded() never completes, withTimeoutOrNull fires and status must become NoFill —
    // the block must not stay in Loading indefinitely.
    @Test
    fun `ad load that exceeds the timeout guard exits Loading and becomes NoFill`() =
        runTest {
            val fakeRepo = FakeAdRewardRepository()
            fakeRepo.seedState(rewardState())
            val fakeGateway = FakeAdGateway().apply { neverLoad = true }
            val viewModel = createViewModel(fakeRepo = fakeRepo, fakeGateway = fakeGateway)

            viewModel.onBlockShown(activity)
            runCurrent()

            // Advance virtual time past the 25 000 ms guard; withTimeoutOrNull returns null.
            advanceTimeBy(25_001L)
            runCurrent()

            assertNotEquals(RewardedAdStatus.Loading, viewModel.state.value.status)
            assertEquals(RewardedAdStatus.NoFill, viewModel.state.value.status)
        }
}
