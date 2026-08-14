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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
        private val adRuntimeConfig: AdRuntimeConfig,
        @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    ) : AdGateway {
        private val operationMutex = Mutex()
        private val consentMutex = Mutex()
        private val mutableAvailability =
            MutableStateFlow(
                if (adRuntimeConfig.adsEnabled) AdAvailability.ConsentRequired else AdAvailability.Disabled,
            )
        private var consentResult: ConsentResult? = null
        private var consentRefresh: CompletableDeferred<ConsentResult>? = null
        private var rewardedToken: RewardToken? = null
        private var rewardedConsent: ConsentResult? = null

        override suspend fun ensureConsent(activity: Activity): ConsentResult {
            if (!adRuntimeConfig.adsEnabled) {
                mutableAvailability.value = AdAvailability.Disabled
                return ConsentResult.Unavailable
            }

            val refresh =
                consentMutex.withLock {
                    val existing = consentRefresh
                    if (existing != null) {
                        existing to false
                    } else {
                        CompletableDeferred<ConsentResult>().also { consentRefresh = it } to true
                    }
                }
            if (!refresh.second) {
                return refresh.first.await()
            }

            try {
                val result = withContext(mainDispatcher) { consentGateway.ensureConsent(activity) }
                consentResult = result
                mutableAvailability.value =
                    if (result.canRequestAds()) {
                        AdAvailability.Available
                    } else {
                        AdAvailability.ConsentRequired
                    }
                refresh.first.complete(result)
                return result
            } catch (error: Throwable) {
                refresh.first.completeExceptionally(error)
                throw error
            } finally {
                withContext(NonCancellable) {
                    consentMutex.withLock {
                        if (consentRefresh === refresh.first) {
                            consentRefresh = null
                        }
                    }
                }
            }
        }

        override suspend fun loadRewarded(): AdLoadResult =
            operationMutex.withLock {
                if (!adRuntimeConfig.adsEnabled) {
                    noFillStreak.reset()
                    return@withLock unavailable(AdAvailability.Disabled)
                }

                if (
                    mutableAvailability.value == AdAvailability.RegionUnavailable ||
                    noFillStreak.isRegionUnavailable()
                ) {
                    return@withLock unavailable(AdAvailability.RegionUnavailable)
                }

                awaitConsentRefresh()
                val consent = consentMutex.withLock { consentResult }
                if (consent == null || !consent.canRequestAds()) {
                    noFillStreak.reset()
                    return@withLock unavailable(AdAvailability.ConsentRequired)
                }

                try {
                    clearPendingRewarded()
                    mutableAvailability.value = AdAvailability.Loading
                    withTimeout(REWARDED_LOAD_TIMEOUT_MILLIS) {
                        val token =
                            rewardTokenSource.requestToken().getOrElse { error ->
                                return@withTimeout tokenFailure(error)
                            }
                        if (!token.expiresAt.isAfter(clock.instant())) {
                            noFillStreak.reset()
                            IllegalStateException("Expired rewarded ad token").reportToSentry()
                            return@withTimeout unavailable(AdAvailability.NoFill)
                        }
                        when (val result = loadAd(token, consent.requiresNonPersonalizedAds())) {
                            RewardedAdLoadResult.Loaded -> {
                                noFillStreak.reset()
                                rewardedToken = token
                                rewardedConsent = consent
                                mutableAvailability.value = AdAvailability.Available
                                AdLoadResult.Loaded
                            }

                            is RewardedAdLoadResult.Failed -> loadFailure(result.errorCode, result.errorMessage)
                            RewardedAdLoadResult.TimedOut -> {
                                noFillStreak.reset()
                                clearPendingRewarded()
                                unavailable(AdAvailability.Offline)
                            }
                        }
                    }
                } catch (error: TimeoutCancellationException) {
                    if (!currentCoroutineContext().isActive) {
                        throw error
                    }
                    noFillStreak.reset()
                    clearPendingRewarded()
                    unavailable(AdAvailability.Offline)
                } finally {
                    clearCancelledOperation()
                }
            }

        override suspend fun showRewarded(activity: Activity): AdShowResult =
            operationMutex.withLock {
                if (!adRuntimeConfig.adsEnabled) {
                    noFillStreak.reset()
                    return@withLock showUnavailable(AdAvailability.Disabled)
                }
                try {
                    val token = rewardedToken
                    if (token == null || !token.expiresAt.isAfter(clock.instant())) {
                        noFillStreak.reset()
                        clearPendingRewarded()
                        return@withLock showUnavailable(AdAvailability.NoFill)
                    }

                    val refreshedConsent = ensureConsent(activity)
                    if (!refreshedConsent.canRequestAds()) {
                        noFillStreak.reset()
                        clearPendingRewarded()
                        return@withLock showUnavailable(AdAvailability.ConsentRequired)
                    }
                    if (
                        refreshedConsent.requiresNonPersonalizedAds() &&
                        rewardedConsent?.requiresNonPersonalizedAds() != true
                    ) {
                        noFillStreak.reset()
                        clearPendingRewarded()
                        return@withLock showUnavailable(AdAvailability.ConsentRequired)
                    }

                    val currentSessionUserId =
                        rewardTokenSource.currentSessionUserId().getOrElse { error ->
                            noFillStreak.reset()
                            clearPendingRewarded()
                            return@withLock showAuthenticationFailure(error)
                        }
                    if (token.sessionUserId != null && currentSessionUserId != token.sessionUserId) {
                        noFillStreak.reset()
                        clearPendingRewarded()
                        return@withLock showUnauthenticated()
                    }
                    if (!token.expiresAt.isAfter(clock.instant())) {
                        noFillStreak.reset()
                        clearPendingRewarded()
                        return@withLock showUnavailable(AdAvailability.NoFill)
                    }
                    rewardedToken = null
                    when (val result = showAd(activity)) {
                        is RewardedAdShowResult.Dismissed ->
                            AdShowResult.Dismissed(rewardEarned = result.rewardEarned)
                        is RewardedAdShowResult.Failed -> {
                            noFillStreak.reset()
                            showUnavailable(adErrorMapper.map(result.errorCode, result.errorMessage))
                        }

                        RewardedAdShowResult.NotLoaded -> {
                            noFillStreak.reset()
                            showUnavailable(AdAvailability.NoFill)
                        }
                    }
                } finally {
                    clearCancelledOperation()
                }
            }

        override fun availability(): StateFlow<AdAvailability> = mutableAvailability.asStateFlow()

        private fun tokenFailure(error: Throwable): AdLoadResult {
            noFillStreak.reset()
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

        private fun showUnauthenticated(): AdShowResult.Unauthenticated {
            mutableAvailability.value = AdAvailability.ConsentRequired
            return AdShowResult.Unauthenticated
        }

        private fun showAuthenticationFailure(error: Throwable): AdShowResult =
            when ((error as? SyncException)?.syncError) {
                SyncError.Auth -> showUnauthenticated()
                SyncError.Network -> showUnavailable(AdAvailability.Offline)
                else -> {
                    error.reportToSentry()
                    showUnavailable(AdAvailability.NoFill)
                }
            }

        private suspend fun clearPendingRewarded() {
            rewardedToken = null
            rewardedConsent = null
            withContext(mainDispatcher) { rewardedAdClient.clear() }
        }

        private suspend fun clearCancelledOperation() {
            if (currentCoroutineContext().isActive) return
            withContext(NonCancellable + mainDispatcher) {
                rewardedToken = null
                rewardedConsent = null
                runCatching { rewardedAdClient.clear() }.onFailure(Throwable::reportToSentry)
                noFillStreak.reset()
                mutableAvailability.value = AdAvailability.NoFill
            }
        }

        private suspend fun awaitConsentRefresh() {
            val refresh = consentMutex.withLock { consentRefresh }
            refresh?.await()
        }

        private suspend fun loadAd(
            token: RewardToken,
            nonPersonalized: Boolean,
        ): RewardedAdLoadResult =
            try {
                withContext(mainDispatcher) {
                    rewardedAdClient.load(
                        adUnitId = BuildConfig.ADMOB_REWARDED_UNIT_ID,
                        customData = token.customData,
                        nonPersonalized = nonPersonalized,
                    )
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
                            if (!continuation.isActive) {
                                return
                            }
                            try {
                                ad.setServerSideVerificationOptions(
                                    ServerSideVerificationOptions
                                        .Builder()
                                        .setCustomData(customData)
                                        .build(),
                                )
                                rewardedAd = ad
                                if (continuation.isActive) {
                                    runCatching { continuation.resume(RewardedAdLoadResult.Loaded) }
                                }
                            } catch (error: Throwable) {
                                rewardedAd = null
                                error.reportToSentry()
                                if (continuation.isActive) {
                                    runCatching {
                                        continuation.resume(
                                            RewardedAdLoadResult.Failed(
                                                errorCode = UNKNOWN_SDK_ERROR_CODE,
                                                errorMessage = error.message,
                                            ),
                                        )
                                    }
                                }
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
                var watchedToCompletion = false
                ad.fullScreenContentCallback =
                    object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            if (continuation.isActive) {
                                continuation.resume(
                                    RewardedAdShowResult.Dismissed(
                                        rewardEarned = watchedToCompletion,
                                    ),
                                )
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
                    ad.show(activity) { watchedToCompletion = true }
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
                if (initialized) {
                    return
                }
                withTimeout(MOBILE_ADS_INITIALIZATION_TIMEOUT_MILLIS) {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        try {
                            MobileAds.initialize(context) {
                                if (continuation.isActive) {
                                    runCatching { continuation.resume(Unit) }
                                }
                            }
                        } catch (error: Throwable) {
                            if (continuation.isActive) {
                                runCatching { continuation.resumeWithException(error) }
                            }
                        }
                    }
                }
                initialized = true
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
private const val MOBILE_ADS_INITIALIZATION_TIMEOUT_MILLIS = 10_000L
private const val NON_PERSONALIZED_ADS_KEY = "npa"
private const val UNKNOWN_SDK_ERROR_CODE = -1
private const val UNKNOWN_SHOW_ERROR_CODE = -1

interface AdRuntimeConfig {
    val adsEnabled: Boolean
}

@Singleton
class BuildConfigAdRuntimeConfig
    @Inject
    constructor() : AdRuntimeConfig {
        override val adsEnabled: Boolean = BuildConfig.ADS_ENABLED
    }
