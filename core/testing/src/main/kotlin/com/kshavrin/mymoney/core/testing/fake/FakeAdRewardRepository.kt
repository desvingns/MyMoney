package com.kshavrin.mymoney.core.testing.fake

import com.kshavrin.mymoney.core.domain.ads.AdRewardRepository
import com.kshavrin.mymoney.core.domain.ads.AdRewardState
import com.kshavrin.mymoney.core.domain.ads.ConfirmationOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAdRewardRepository(
    initialState: AdRewardState? = null,
) : AdRewardRepository {
    private val rewardState = MutableStateFlow(initialState)
    private var refreshResult: Result<AdRewardState>? = initialState?.let(Result.Companion::success)
    private var confirmationOutcome: ConfirmationOutcome = ConfirmationOutcome.PendingConfirmation
    private var sessionGeneration = 0L
    private var stateSessionGeneration = 0L
    private var confirmationGate: CompletableDeferred<Unit>? = null
    private var confirmationDelayMillis = 0L

    override val state: StateFlow<AdRewardState?> = rewardState.asStateFlow()

    override suspend fun refresh(): Result<AdRewardState> {
        val result =
            refreshResult
                ?: rewardState.value?.let(Result.Companion::success)
                ?: Result.failure(IllegalStateException("No ad reward state has been seeded."))
        result.onSuccess { refreshedState ->
            rewardState.value = refreshedState
            stateSessionGeneration = sessionGeneration
        }
        return result
    }

    override fun invalidateSession() {
        sessionGeneration += 1
        rewardState.value = null
        refreshResult = null
    }

    override suspend fun awaitConfirmation(previous: AdRewardState): ConfirmationOutcome {
        confirmationGate?.await()
        if (confirmationDelayMillis > 0L) delay(confirmationDelayMillis)
        return if (rewardState.value == previous && stateSessionGeneration == sessionGeneration) {
            confirmationOutcome
        } else {
            ConfirmationOutcome.PendingConfirmation
        }
    }

    fun seedState(state: AdRewardState?) {
        rewardState.value = state
        refreshResult = state?.let(Result.Companion::success)
        stateSessionGeneration = sessionGeneration
    }

    fun seedRefreshResult(result: Result<AdRewardState>) {
        refreshResult = result
    }

    fun seedConfirmationOutcome(outcome: ConfirmationOutcome) {
        confirmationOutcome = outcome
    }

    // Makes each awaitConfirmation() call consume virtual time, so a test can prove the caller's
    // outer timeout bounds a slow poll loop instead of waiting for every retry to finish.
    fun seedConfirmationDelay(delayMillis: Long) {
        confirmationDelayMillis = delayMillis
    }

    // Holds awaitConfirmation() suspended until the returned gate is completed, so a test can keep
    // the caller in AwaitingConfirmation with its watch coroutine genuinely in flight.
    fun suspendConfirmation(): CompletableDeferred<Unit> {
        val gate = CompletableDeferred<Unit>()
        confirmationGate = gate
        return gate
    }
}
