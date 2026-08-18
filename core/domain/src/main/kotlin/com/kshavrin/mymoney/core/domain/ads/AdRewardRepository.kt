package com.kshavrin.mymoney.core.domain.ads

import kotlinx.coroutines.flow.StateFlow

interface AdRewardRepository {
    val state: StateFlow<AdRewardState?>

    fun invalidateSession()

    suspend fun refresh(): Result<AdRewardState>

    suspend fun awaitConfirmation(previous: AdRewardState): ConfirmationOutcome
}

sealed interface ConfirmationOutcome {
    data class ProgressIncreased(
        val state: AdRewardState,
    ) : ConfirmationOutcome

    data class PlusGranted(
        val state: AdRewardState,
    ) : ConfirmationOutcome

    data class WatchCounted(
        val state: AdRewardState,
    ) : ConfirmationOutcome

    data object PendingConfirmation : ConfirmationOutcome
}
