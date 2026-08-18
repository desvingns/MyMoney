package com.kshavrin.mymoney.feature.support.rewardedad

import android.app.Activity
import com.kshavrin.mymoney.core.ads.AdAvailability
import com.kshavrin.mymoney.core.ads.AdGateway
import com.kshavrin.mymoney.core.ads.AdLoadResult
import com.kshavrin.mymoney.core.ads.AdShowResult
import com.kshavrin.mymoney.core.ads.ConsentResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

class FakeAdGateway : AdGateway {
    private val availabilityState = MutableStateFlow<AdAvailability>(AdAvailability.Available)

    var consentResult: ConsentResult = ConsentResult.Granted
    var loadResult: AdLoadResult = AdLoadResult.Loaded
    var showResult: AdShowResult = AdShowResult.Dismissed(rewardEarned = true)

    // When true, loadRewarded() suspends indefinitely — used to trigger withTimeoutOrNull.
    var neverLoad: Boolean = false

    var loadRewardedCalls: Int = 0
        private set

    override suspend fun ensureConsent(activity: Activity): ConsentResult = consentResult

    override suspend fun loadRewarded(): AdLoadResult {
        loadRewardedCalls++
        if (neverLoad) {
            suspendCancellableCoroutine<Nothing> { }
        }
        return loadResult
    }

    override suspend fun showRewarded(activity: Activity): AdShowResult = showResult

    override fun availability(): StateFlow<AdAvailability> = availabilityState.asStateFlow()

    fun seedAvailability(availability: AdAvailability) {
        availabilityState.value = availability
    }
}
