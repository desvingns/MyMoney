package com.kshavrin.mymoney.core.ads

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

interface AdGateway {
    suspend fun ensureConsent(activity: Activity): ConsentResult

    suspend fun loadRewarded(): AdLoadResult

    suspend fun showRewarded(activity: Activity): AdShowResult

    fun availability(): StateFlow<AdAvailability>
}

sealed interface ConsentResult {
    data object Granted : ConsentResult

    data object Denied : ConsentResult

    data object NotRequired : ConsentResult

    data object Unavailable : ConsentResult
}

sealed interface AdLoadResult {
    data object Loaded : AdLoadResult

    data class Unavailable(
        val availability: AdAvailability,
    ) : AdLoadResult

    data object Unauthenticated : AdLoadResult
}

sealed interface AdShowResult {
    data object Unauthenticated : AdShowResult

    data class Dismissed(
        val rewardEarned: Boolean,
    ) : AdShowResult

    data class Unavailable(
        val availability: AdAvailability,
    ) : AdShowResult
}

internal fun ConsentResult.canRequestAds(): Boolean =
    this == ConsentResult.Granted || this == ConsentResult.Denied || this == ConsentResult.NotRequired

internal fun ConsentResult.requiresNonPersonalizedAds(): Boolean = this == ConsentResult.Denied
