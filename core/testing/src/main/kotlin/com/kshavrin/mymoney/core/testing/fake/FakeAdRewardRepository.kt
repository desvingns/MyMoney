package com.kshavrin.mymoney.core.testing.fake

import com.kshavrin.mymoney.core.domain.ads.AdRewardRepository
import com.kshavrin.mymoney.core.domain.ads.AdRewardState
import com.kshavrin.mymoney.core.domain.ads.ConfirmationOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAdRewardRepository(
    initialState: AdRewardState? = null,
) : AdRewardRepository {
    private val rewardState = MutableStateFlow(initialState)
    private var refreshResult: Result<AdRewardState>? = initialState?.let(Result.Companion::success)
    private var confirmationOutcome: ConfirmationOutcome = ConfirmationOutcome.PendingConfirmation

    override val state: StateFlow<AdRewardState?> = rewardState.asStateFlow()

    override suspend fun refresh(): Result<AdRewardState> {
        val result =
            refreshResult
                ?: rewardState.value?.let(Result.Companion::success)
                ?: Result.failure(IllegalStateException("No ad reward state has been seeded."))
        result.onSuccess { refreshedState -> rewardState.value = refreshedState }
        return result
    }

    override suspend fun awaitConfirmation(previous: AdRewardState): ConfirmationOutcome = confirmationOutcome

    fun seedState(state: AdRewardState?) {
        rewardState.value = state
        refreshResult = state?.let(Result.Companion::success)
    }

    fun seedRefreshResult(result: Result<AdRewardState>) {
        refreshResult = result
    }

    fun seedConfirmationOutcome(outcome: ConfirmationOutcome) {
        confirmationOutcome = outcome
    }
}
