package com.kshavrin.mymoney.core.ads.admob

import android.app.Activity
import android.content.Context
import android.os.Bundle
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import com.kshavrin.mymoney.core.ads.AdAvailability
import com.kshavrin.mymoney.core.ads.AdGateway
import com.kshavrin.mymoney.core.ads.AdLoadResult
import com.kshavrin.mymoney.core.ads.AdShowResult
import com.kshavrin.mymoney.core.ads.BuildConfig
import com.kshavrin.mymoney.core.ads.ConsentResult
import com.kshavrin.mymoney.core.ads.canRequestAds
import com.kshavrin.mymoney.core.ads.consent.UmpConsentGateway
import com.kshavrin.mymoney.core.ads.requiresNonPersonalizedAds
import com.kshavrin.mymoney.core.ads.token.RewardToken
import com.kshavrin.mymoney.core.ads.token.RewardTokenSource
import com.kshavrin.mymoney.core.common.di.MainDispatcher
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class AdMobAdGateway
    @Inject
    constructor(
        private val consentGateway: UmpConsentGateway,
        private val rewardTokenSource: RewardTokenSource,
        private val rewardedAdClient: RewardedAdClient,
        private val adErrorMapper: AdErrorMapper,
        private val noFillStreak: NoFillStreak,
        private val clock: Clock,
        @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    ) : AdGateway {
        private val operationMutex = Mutex()
        private val consentMutex = Mutex()
        private val mutableAvailability =
            MutableStateFlow(
                if (BuildConfig.ADS_ENABLED) AdAvailability.ConsentRequired else AdAvailability.Disabled,
            )
        private var consentResult: ConsentResult? = null
        private var rewardedToken: RewardToken? = null

        override suspend fun ensureConsent(activity: Activity): ConsentResult {
            if (!BuildConfig.ADS_ENABLED) {
                mutableAvailability.value = AdAvailability.Disabled
                return ConsentResult.Unavailable
            }

            consentMutex.withLock { consentResult }?.let { return it }
            val result = withContext(mainDispatcher) { consentGateway.ensureConsent(activity) }
            if (result.canRequestAds()) {
                consentMutex.withLock { consentResult = result }
                mutableAvailability.value = AdAvailability.Available
            } else {
                mutableAvailability.value = AdAvailability.ConsentRequired
            }
            return result
        }

        override suspend fun loadRewarded(): AdLoadResult =
            operationMutex.withLock {
                if (!BuildConfig.ADS_ENABLED) {
                    return@withLock unavailable(AdAvailability.Disabled)
                }

                val consent = consentMutex.withLock { consentResult }
                if (consent == null || !consent.canRequestAds()) {
                    return@withLock unavailable(AdAvailability.ConsentRequired)
                }

                mutableAvailability.value = AdAvailability.Loading
                rewardedToken = null
                withContext(mainDispatcher) { rewardedAdClient.clear() }
                val token =
                    rewardTokenSource.requestToken().getOrElse { error ->
                        return@withLock tokenFailure(error)
                    }
                if (!token.expiresAt.isAfter(clock.instant())) {
                    IllegalStateException("Expired rewarded ad token").reportToSentry()
                    return@withLock unavailable(AdAvailability.NoFill)
                }
                val result = loadAd(token, consent.requiresNonPersonalizedAds())
                when (result) {
                    RewardedAdLoadResult.Loaded -> {
                        noFillStreak.reset()
                        rewardedToken = token
                        mutableAvailability.value = AdAvailability.Available
                        AdLoadResult.Loaded
                    }

                    is RewardedAdLoadResult.Failed -> loadFailure(result.errorCode, result.errorMessage)
                    RewardedAdLoadResult.TimedOut -> {
                        noFillStreak.reset()
                        withContext(mainDispatcher) { rewardedAdClient.clear() }
                        unavailable(AdAvailability.Offline)
                    }
                }
            }

        override suspend fun showRewarded(activity: Activity): AdShowResult =
            operationMutex.withLock {
                if (!BuildConfig.ADS_ENABLED) {
                    return@withLock showUnavailable(AdAvailability.Disabled)
                }
                val token = rewardedToken
                if (token == null || !token.expiresAt.isAfter(clock.instant())) {
                    rewardedToken = null
                    withContext(mainDispatcher) { rewardedAdClient.clear() }
                    return@withLock showUnavailable(AdAvailability.NoFill)
                }

                rewardedToken = null
                when (val result = showAd(activity)) {
                    is RewardedAdShowResult.Dismissed -> AdShowResult.Dismissed(result.rewardEarned)
                    is RewardedAdShowResult.Failed -> {
                        noFillStreak.reset()
                        showUnavailable(adErrorMapper.map(result.errorCode, result.errorMessage))
                    }

                    RewardedAdShowResult.NotLoaded -> showUnavailable(AdAvailability.NoFill)
                }
            }

        override fun availability(): StateFlow<AdAvailability> = mutableAvailability.asStateFlow()

        private fun tokenFailure(error: Throwable): AdLoadResult {
            val availability =
                when ((error as? SyncException)?.syncError) {
                    SyncError.Auth -> {
                        mutableAvailability.value = AdAvailability.ConsentRequired
                        return AdLoadResult.Unauthenticated
                    }

                    SyncError.Network -> AdAvailability.Offline
                    else -> {
                        error.reportToSentry()
                        AdAvailability.NoFill
                    }
                }
            noFillStreak.reset()
            return unavailable(availability)
        }

        private fun loadFailure(
            errorCode: Int,
            errorMessage: String?,
        ): AdLoadResult.Unavailable {
            val mappedAvailability = adErrorMapper.map(errorCode, errorMessage)
            val availability =
                if (adErrorMapper.isNoFill(errorCode) && noFillStreak.recordNoFill()) {
                    AdAvailability.RegionUnavailable
                } else {
                    if (!adErrorMapper.isNoFill(errorCode)) {
                        noFillStreak.reset()
                    }
                    mappedAvailability
                }
            rewardedToken = null
            return unavailable(availability)
        }

        private fun unavailable(availability: AdAvailability): AdLoadResult.Unavailable {
            mutableAvailability.value = availability
            return AdLoadResult.Unavailable(availability)
        }

        private fun showUnavailable(availability: AdAvailability): AdShowResult.Unavailable {
            mutableAvailability.value = availability
            return AdShowResult.Unavailable(availability)
        }

        private suspend fun loadAd(
            token: RewardToken,
            nonPersonalized: Boolean,
        ): RewardedAdLoadResult =
            try {
                withContext(mainDispatcher) {
                    withTimeoutOrNull(REWARDED_LOAD_TIMEOUT_MILLIS) {
                        rewardedAdClient.load(
                            adUnitId = BuildConfig.ADMOB_REWARDED_UNIT_ID,
                            customData = token.customData,
                            nonPersonalized = nonPersonalized,
                        )
                    } ?: RewardedAdLoadResult.TimedOut
                }
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    throw error
                }
                error.reportToSentry()
                RewardedAdLoadResult.Failed(UNKNOWN_SDK_ERROR_CODE, error.message)
            }

        private suspend fun showAd(activity: Activity): RewardedAdShowResult =
            try {
                withContext(mainDispatcher) { rewardedAdClient.show(activity) }
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    throw error
                }
                error.reportToSentry()
                RewardedAdShowResult.Failed(UNKNOWN_SDK_ERROR_CODE, error.message)
            }
    }

interface RewardedAdClient {
    suspend fun load(
        adUnitId: String,
        customData: String,
        nonPersonalized: Boolean,
    ): RewardedAdLoadResult

    suspend fun show(activity: Activity): RewardedAdShowResult

    fun clear()
}

sealed interface RewardedAdLoadResult {
    data object Loaded : RewardedAdLoadResult

    data class Failed(
        val errorCode: Int,
        val errorMessage: String?,
    ) : RewardedAdLoadResult

    data object TimedOut : RewardedAdLoadResult
}

sealed interface RewardedAdShowResult {
    data class Dismissed(
        val rewardEarned: Boolean,
    ) : RewardedAdShowResult

    data class Failed(
        val errorCode: Int,
        val errorMessage: String?,
    ) : RewardedAdShowResult

    data object NotLoaded : RewardedAdShowResult
}

@Singleton
class GoogleMobileAdsRewardedClient
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : RewardedAdClient {
        private val initializationMutex = Mutex()
        private var initialized = false
        private var rewardedAd: RewardedAd? = null

        override suspend fun load(
            adUnitId: String,
            customData: String,
            nonPersonalized: Boolean,
        ): RewardedAdLoadResult {
            initializeIfNeeded()
            return suspendCancellableCoroutine { continuation ->
                RewardedAd.load(
                    context,
                    adUnitId,
                    adRequest(nonPersonalized),
                    object : RewardedAdLoadCallback() {
                        override fun onAdLoaded(ad: RewardedAd) {
                            if (continuation.isActive) {
                                ad.setServerSideVerificationOptions(
                                    ServerSideVerificationOptions
                                        .Builder()
                                        .setCustomData(customData)
                                        .build(),
                                )
                                rewardedAd = ad
                                continuation.resume(RewardedAdLoadResult.Loaded)
                            }
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            if (continuation.isActive) {
                                continuation.resume(
                                    RewardedAdLoadResult.Failed(
                                        errorCode = error.code,
                                        errorMessage = error.message,
                                    ),
                                )
                            }
                        }
                    },
                )
            }
        }

        override suspend fun show(activity: Activity): RewardedAdShowResult {
            val ad = rewardedAd ?: return RewardedAdShowResult.NotLoaded
            rewardedAd = null
            return suspendCancellableCoroutine { continuation ->
                var rewardEarned = false
                ad.fullScreenContentCallback =
                    object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            if (continuation.isActive) {
                                continuation.resume(RewardedAdShowResult.Dismissed(rewardEarned))
                            }
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            if (continuation.isActive) {
                                continuation.resume(
                                    RewardedAdShowResult.Failed(
                                        errorCode = error.code,
                                        errorMessage = error.message,
                                    ),
                                )
                            }
                        }
                    }
                runCatching {
                    ad.show(activity) {
                        rewardEarned = true
                    }
                }.onFailure { error ->
                    error.reportToSentry()
                    if (continuation.isActive) {
                        continuation.resume(
                            RewardedAdShowResult.Failed(
                                errorCode = UNKNOWN_SHOW_ERROR_CODE,
                                errorMessage = error.message,
                            ),
                        )
                    }
                }
            }
        }

        override fun clear() {
            rewardedAd = null
        }

        private suspend fun initializeIfNeeded() {
            initializationMutex.withLock {
                if (!initialized) {
                    MobileAds.initialize(context) {}
                    initialized = true
                }
            }
        }

        private fun adRequest(nonPersonalized: Boolean): AdRequest {
            if (!nonPersonalized) {
                return AdRequest.Builder().build()
            }
            return AdRequest
                .Builder()
                .addNetworkExtrasBundle(
                    AdMobAdapter::class.java,
                    Bundle().apply { putInt(NON_PERSONALIZED_ADS_KEY, 1) },
                ).build()
        }
    }

private const val REWARDED_LOAD_TIMEOUT_MILLIS = 20_000L
private const val NON_PERSONALIZED_ADS_KEY = "npa"
private const val UNKNOWN_SDK_ERROR_CODE = -1
private const val UNKNOWN_SHOW_ERROR_CODE = -1
