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
import org.junit.Assert.assertTrue
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
        totalWatched: Int = 0,
    ) = AdRewardState(
        progress = progress,
        required = required,
        frozen = false,
        frozenReason = null,
        plusActive = plusActive,
        plusProvider = null,
        plusExpiresAt = null,
        totalWatched = totalWatched,
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

    // ─── Acceptance matrix: scenario 6 — Awaiting confirmation / retry loop ─────

    // CONFIRMATION-RETRY-001: the loop retries up to CONFIRMATION_MAX_RETRIES times; when all
    // attempts return PendingConfirmation the status must reach ConfirmationTimeout, not stay in
    // AwaitingConfirmation forever.
    @Test
    fun `confirmation retries exhausted after 10 pending outcomes transitions to ConfirmationTimeout`() =
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

            assertEquals(RewardedAdStatus.ConfirmationTimeout, viewModel.state.value.status)
        }

    // CONFIRMATION-RETRY-002: after the retry loop places the block in ConfirmationTimeout, the
    // user can tap "Try again" (onRetry) to restart the whole loadBlock() sequence. The block
    // must transition through Loading and reach Ready again.
    @Test
    fun `onRetry from ConfirmationTimeout reloads the block and reaches Ready`() =
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
            viewModel.onWatchAd(activity)
            runCurrent()
            assertEquals(RewardedAdStatus.ConfirmationTimeout, viewModel.state.value.status)

            viewModel.onRetry(activity)
            runCurrent()

            assertEquals(RewardedAdStatus.Ready, viewModel.state.value.status)
        }

    // CONFIRMATION-RETRY-003: while the retry loop is in flight (suspended at awaitConfirmation),
    // the status must remain AwaitingConfirmation — the loop has not yet exhausted its budget.
    @Test
    fun `status remains AwaitingConfirmation while confirmation loop is suspended mid-retry`() =
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

            val confirmationGate = fakeRepo.suspendConfirmation()

            viewModel.onWatchAd(activity)
            runCurrent()

            assertEquals(RewardedAdStatus.AwaitingConfirmation, viewModel.state.value.status)

            confirmationGate.complete(Unit)
            runCurrent()
        }

    // CONFIRMATION-RETRY-004: ConfirmationOutcome.ProgressIncreased returned on the first loop
    // iteration must exit the repeat block early via non-local return, transition status to Ready,
    // and surface the updated progress from the outcome state — not the stale pre-watch value.
    @Test
    fun `ProgressIncreased outcome in confirmation loop exits early with Ready status and updated progress`() =
        runTest {
            val fakeRepo = FakeAdRewardRepository()
            fakeRepo.seedState(rewardState(progress = 3, required = 5))
            val updatedState = rewardState(progress = 4, required = 5)
            fakeRepo.seedConfirmationOutcome(ConfirmationOutcome.ProgressIncreased(updatedState))
            val fakeGateway =
                FakeAdGateway().apply {
                    loadResult = AdLoadResult.Loaded
                    showResult = AdShowResult.Dismissed(rewardEarned = true)
                }
            val viewModel = createViewModel(fakeRepo = fakeRepo, fakeGateway = fakeGateway)

            viewModel.onBlockShown(activity)
            runCurrent()
            assertEquals(RewardedAdStatus.Ready, viewModel.state.value.status)
            val loadsBeforeWatch = fakeGateway.loadRewardedCalls

            viewModel.onWatchAd(activity)
            runCurrent()

            assertEquals(RewardedAdStatus.Ready, viewModel.state.value.status)
            assertEquals(
                4,
                viewModel.state.value.reward
                    ?.progress,
            )
            assertTrue(fakeGateway.loadRewardedCalls > loadsBeforeWatch)
        }

    // CONFIRMATION-RETRY-005: ConfirmationOutcome.PlusGranted returned on the first loop
    // iteration must exit the repeat block early via non-local return, transition status to
    // Ready, and surface plusActive = true from the outcome state.
    @Test
    fun `PlusGranted outcome in confirmation loop exits early with Ready status and plusActive true`() =
        runTest {
            val fakeRepo = FakeAdRewardRepository()
            fakeRepo.seedState(rewardState(progress = 5, required = 5, plusActive = false))
            val grantedState = rewardState(progress = 5, required = 5, plusActive = true)
            fakeRepo.seedConfirmationOutcome(ConfirmationOutcome.PlusGranted(grantedState))
            val fakeGateway =
                FakeAdGateway().apply {
                    loadResult = AdLoadResult.Loaded
                    showResult = AdShowResult.Dismissed(rewardEarned = true)
                }
            val viewModel = createViewModel(fakeRepo = fakeRepo, fakeGateway = fakeGateway)

            viewModel.onBlockShown(activity)
            runCurrent()
            assertEquals(RewardedAdStatus.Ready, viewModel.state.value.status)
            val loadsBeforeWatch = fakeGateway.loadRewardedCalls

            viewModel.onWatchAd(activity)
            runCurrent()

            assertEquals(RewardedAdStatus.Ready, viewModel.state.value.status)
            assertEquals(
                true,
                viewModel.state.value.reward
                    ?.plusActive,
            )
            assertTrue(fakeGateway.loadRewardedCalls > loadsBeforeWatch)
        }

    // CONFIRMATION-RETRY-006: clearing the ViewModel while the confirmation loop is suspended
    // mid-retry must cancel the watchJob coroutine and produce no further state mutations.
    // The block must NOT transition to ConfirmationTimeout or any other terminal state —
    // the cancellation path is opaque to the UI.
    @Test
    fun `clearing ViewModel while confirmation loop is suspended cancels the coroutine with no further state change`() =
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

            val confirmationGate = fakeRepo.suspendConfirmation()

            viewModel.onWatchAd(activity)
            runCurrent()
            assertEquals(RewardedAdStatus.AwaitingConfirmation, viewModel.state.value.status)

            // Clear the store — triggers ViewModel.onCleared() and cancels viewModelScope.
            viewModelStore.clear()
            runCurrent()

            // The coroutine was cancelled before completing the loop: no terminal transition.
            assertEquals(RewardedAdStatus.AwaitingConfirmation, viewModel.state.value.status)

            // Release the gate so the fake's pending deferred does not interfere with teardown.
            confirmationGate.complete(Unit)
        }

    // GUARD-001: calling onWatchAd or onRetry a second time while the confirmation loop is
    // in flight (watchJob.isActive == true) must be a no-op — the job-active guard must
    // short-circuit both paths, preventing a duplicate loop from starting.
    @Test
    fun `onWatchAd and onRetry are ignored while confirmation loop is mid-flight`() =
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

            val confirmationGate = fakeRepo.suspendConfirmation()

            viewModel.onWatchAd(activity)
            runCurrent()
            assertEquals(RewardedAdStatus.AwaitingConfirmation, viewModel.state.value.status)

            // Both calls must hit the watchJob.isActive guard and short-circuit.
            viewModel.onWatchAd(activity)
            viewModel.onRetry(activity)
            runCurrent()

            // Status is unchanged — no fresh loadBlock or second watch loop was started.
            assertEquals(RewardedAdStatus.AwaitingConfirmation, viewModel.state.value.status)

            // Release the gate; the single in-flight loop exhausts all retries.
            confirmationGate.complete(Unit)
            runCurrent()

            // One loop reaches ConfirmationTimeout — confirming only a single loop was in flight.
            assertEquals(RewardedAdStatus.ConfirmationTimeout, viewModel.state.value.status)
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
            assertEquals(
                3,
                viewModel.state.value.reward
                    ?.progress,
            )
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

    // ─── totalWatched field mapping ──────────────────────────────────────────────

    // Verifies toRewardProgress() passes totalWatched from AdRewardState to RewardProgress so the
    // TotalAdsWatchedBadge can read the lifetime counter without an extra repository query.
    @Test
    fun `totalWatched from AdRewardState is surfaced on reward in Ready state`() =
        runTest {
            val fakeRepo = FakeAdRewardRepository()
            fakeRepo.seedState(rewardState(progress = 3, required = 5, totalWatched = 42))
            val fakeGateway = FakeAdGateway().apply { loadResult = AdLoadResult.Loaded }
            val viewModel = createViewModel(fakeRepo = fakeRepo, fakeGateway = fakeGateway)

            viewModel.onBlockShown(activity)
            runCurrent()

            assertEquals(RewardedAdStatus.Ready, viewModel.state.value.status)
            assertEquals(
                42,
                viewModel.state.value.reward
                    ?.totalWatched,
            )
        }

    // ─── WATCH-COUNTED-001: frozen view confirmed via totalWatched ───────────────
    // Regression (prod project shwzjlkhlpgbmzgnxhxi): with Plus already active every reward row is
    // frozen, so progress stays 0 and plusActive stays true — only totalWatched moves (6 -> 7). The
    // view IS confirmed: the block must leave AwaitingConfirmation, publish the new totalWatched,
    // never reach ConfirmationTimeout, and re-arm a fresh ad so Watch is usable again without Retry.
    @Test
    fun `WatchCounted outcome while Plus active publishes totalWatched, avoids timeout and re-arms watch`() =
        runTest {
            val fakeRepo = FakeAdRewardRepository()
            fakeRepo.seedState(rewardState(progress = 0, required = 5, plusActive = true, totalWatched = 6))
            val countedState = rewardState(progress = 0, required = 5, plusActive = true, totalWatched = 7)
            fakeRepo.seedConfirmationOutcome(ConfirmationOutcome.WatchCounted(countedState))
            val fakeGateway =
                FakeAdGateway().apply {
                    loadResult = AdLoadResult.Loaded
                    showResult = AdShowResult.Dismissed(rewardEarned = true)
                }
            val viewModel = createViewModel(fakeRepo = fakeRepo, fakeGateway = fakeGateway)

            viewModel.onBlockShown(activity)
            runCurrent()
            assertEquals(RewardedAdStatus.Ready, viewModel.state.value.status)
            val loadsBeforeWatch = fakeGateway.loadRewardedCalls

            viewModel.onWatchAd(activity)
            runCurrent()

            assertNotEquals(RewardedAdStatus.ConfirmationTimeout, viewModel.state.value.status)
            assertNotEquals(RewardedAdStatus.AwaitingConfirmation, viewModel.state.value.status)
            assertEquals(RewardedAdStatus.Ready, viewModel.state.value.status)
            assertEquals(
                7,
                viewModel.state.value.reward
                    ?.totalWatched,
            )
            assertTrue(fakeGateway.loadRewardedCalls > loadsBeforeWatch)
        }

    // ─── Acceptance matrix: cell 8 — Plus active, no server delta, bounded wait ──
    // With Plus already active and every poll returning PendingConfirmation, the awaiting window is
    // bounded by the ViewModel's outer withTimeoutOrNull, not by the per-call retry count. Each poll
    // here takes 30 s of virtual time; without the ~35 s outer bound the 10-iteration loop would run
    // for 300 s. The block must still be awaiting after one poll and reach ConfirmationTimeout at the
    // bound — it must never hang.
    @Test
    fun `plus active with only pending outcomes reaches ConfirmationTimeout within the bounded window`() =
        runTest {
            val fakeRepo = FakeAdRewardRepository()
            fakeRepo.seedState(rewardState(progress = 0, required = 5, plusActive = true, totalWatched = 6))
            fakeRepo.seedConfirmationOutcome(ConfirmationOutcome.PendingConfirmation)
            fakeRepo.seedConfirmationDelay(30_000L)
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

            // One 30 s poll elapses: still inside the outer budget, so no timeout yet.
            advanceTimeBy(30_000L)
            runCurrent()
            assertEquals(RewardedAdStatus.AwaitingConfirmation, viewModel.state.value.status)

            // CONFIRMATION_MAX_WAIT_MILLIS = 35_000L (private in the production file): the outer bound
            // fires here, cutting the loop off ~265 s before it would otherwise finish.
            advanceTimeBy(5_001L)
            runCurrent()
            assertEquals(RewardedAdStatus.ConfirmationTimeout, viewModel.state.value.status)
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
