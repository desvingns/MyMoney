package com.kshavrin.mymoney.core.ads.admob

import android.app.Activity
import com.google.android.gms.ads.AdRequest
import com.kshavrin.mymoney.core.ads.AdAvailability
import com.kshavrin.mymoney.core.ads.AdLoadResult
import com.kshavrin.mymoney.core.ads.AdShowResult
import com.kshavrin.mymoney.core.ads.ConsentResult
import com.kshavrin.mymoney.core.ads.consent.UmpConsentGateway
import com.kshavrin.mymoney.core.ads.token.RewardToken
import com.kshavrin.mymoney.core.ads.token.RewardTokenSource
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AdMobAdGatewayTest {
    @Test
    fun `does not request consent or initialize the ad client before explicit use`() {
        val consent = FakeConsentGateway()
        val tokenSource = FakeRewardTokenSource()
        val client = FakeRewardedAdClient()
        val gateway = gateway(consent, tokenSource, client)

        assertEquals(AdAvailability.ConsentRequired, gateway.availability().value)
        assertEquals(0, consent.calls)
        assertEquals(0, tokenSource.requestCalls)
        assertTrue(client.loadCalls.isEmpty())
    }

    @Test
    fun `serializes explicit consent requests and caches the first result`() =
        runTest {
            val consent = FakeConsentGateway().apply { block = CompletableDeferred() }
            val gateway = gateway(consent, FakeRewardTokenSource(), FakeRewardedAdClient())

            val first = async { gateway.ensureConsent(activity()) }
            consent.started.await()
            val second = async { gateway.ensureConsent(activity()) }

            assertEquals(1, consent.calls)
            consent.block?.complete(Unit)

            assertEquals(ConsentResult.Granted, first.await())
            assertEquals(ConsentResult.Granted, second.await())
            assertEquals(1, consent.calls)
        }

    @Test
    fun `returns consent required and does not request a token before consent`() =
        runTest {
            val tokenSource = FakeRewardTokenSource()
            val client = FakeRewardedAdClient()
            val gateway = gateway(FakeConsentGateway(), tokenSource, client)

            val result = gateway.loadRewarded()

            assertEquals(
                AdLoadResult.Unavailable(AdAvailability.ConsentRequired),
                result,
            )
            assertEquals(0, tokenSource.requestCalls)
            assertTrue(client.loadCalls.isEmpty())
        }

    @Test
    fun `consent denial still loads an explicitly non-personalized rewarded ad`() =
        runTest {
            val consent = FakeConsentGateway().apply { result = ConsentResult.Denied }
            val tokenSource = FakeRewardTokenSource()
            val client = FakeRewardedAdClient()
            val gateway = gateway(consent, tokenSource, client)

            assertEquals(ConsentResult.Denied, gateway.ensureConsent(activity()))
            assertEquals(AdLoadResult.Loaded, gateway.loadRewarded())

            assertEquals(1, client.loadCalls.size)
            assertTrue(client.loadCalls.single().nonPersonalized)
        }

    @Test
    fun `fails closed when the server token request is unauthenticated`() =
        runTest {
            val consent = FakeConsentGateway()
            val tokenSource =
                FakeRewardTokenSource().apply {
                    tokenResult = Result.failure(SyncException(SyncError.Auth))
                }
            val client = FakeRewardedAdClient()
            val gateway = gateway(consent, tokenSource, client)
            gateway.ensureConsent(activity())

            val result = gateway.loadRewarded()

            assertEquals(AdLoadResult.Unauthenticated, result)
            assertEquals(AdAvailability.ConsentRequired, gateway.availability().value)
            assertTrue(client.loadCalls.isEmpty())
        }

    @Test
    fun `requests a fresh server token before every rewarded load`() =
        runTest {
            val tokenSource =
                FakeRewardTokenSource().apply {
                    tokenResults =
                        listOf(
                            Result.success(token(customData = "first-token")),
                            Result.success(token(customData = "second-token")),
                        )
                }
            val client = FakeRewardedAdClient()
            val gateway = gateway(FakeConsentGateway(), tokenSource, client)
            gateway.ensureConsent(activity())

            assertEquals(AdLoadResult.Loaded, gateway.loadRewarded())
            assertEquals(AdLoadResult.Loaded, gateway.loadRewarded())

            assertEquals(2, tokenSource.requestCalls)
            assertEquals(
                listOf("first-token", "second-token"),
                client.loadCalls.map { it.customData },
            )
        }

    @Test
    fun `does not send an expired token to the ad SDK`() =
        runTest {
            val tokenSource =
                FakeRewardTokenSource().apply {
                    tokenResult = Result.success(token(expiresAt = Instant.parse("2026-08-13T23:59:59Z")))
                }
            val client = FakeRewardedAdClient()
            val gateway = gateway(FakeConsentGateway(), tokenSource, client)
            gateway.ensureConsent(activity())

            val result = gateway.loadRewarded()

            assertEquals(AdLoadResult.Unavailable(AdAvailability.NoFill), result)
            assertTrue(client.loadCalls.isEmpty())
        }

    @Test
    fun `maps the configured consecutive no fill threshold to region unavailable`() =
        runTest {
            val tokenSource = FakeRewardTokenSource()
            val client =
                FakeRewardedAdClient().apply {
                    loadResult =
                        RewardedAdLoadResult.Failed(
                            errorCode = AdRequest.ERROR_CODE_NO_FILL,
                            errorMessage = "no fill",
                        )
                }
            val gateway =
                gateway(
                    FakeConsentGateway(),
                    tokenSource,
                    client,
                    noFillStreak = NoFillStreak(threshold = 3),
                )
            gateway.ensureConsent(activity())

            assertEquals(AdAvailability.NoFill, (gateway.loadRewarded() as AdLoadResult.Unavailable).availability)
            assertEquals(AdAvailability.NoFill, (gateway.loadRewarded() as AdLoadResult.Unavailable).availability)
            assertEquals(AdAvailability.RegionUnavailable, (gateway.loadRewarded() as AdLoadResult.Unavailable).availability)
            assertEquals(AdAvailability.RegionUnavailable, gateway.availability().value)
        }

    @Test
    fun `a non-no-fill failure resets the in-memory no fill streak`() =
        runTest {
            val client =
                FakeRewardedAdClient().apply {
                    loadResult = RewardedAdLoadResult.Failed(AdRequest.ERROR_CODE_NO_FILL, "no fill")
                }
            val gateway =
                gateway(
                    FakeConsentGateway(),
                    FakeRewardTokenSource(),
                    client,
                    noFillStreak = NoFillStreak(threshold = 2),
                )
            gateway.ensureConsent(activity())

            val first = gateway.loadRewarded()
            client.loadResult = RewardedAdLoadResult.Failed(AdRequest.ERROR_CODE_NETWORK_ERROR, "offline")
            val second = gateway.loadRewarded()
            client.loadResult = RewardedAdLoadResult.Failed(AdRequest.ERROR_CODE_NO_FILL, "no fill")
            val third = gateway.loadRewarded()

            assertEquals(AdAvailability.NoFill, (first as AdLoadResult.Unavailable).availability)
            assertEquals(AdAvailability.Offline, (second as AdLoadResult.Unavailable).availability)
            assertEquals(AdAvailability.NoFill, (third as AdLoadResult.Unavailable).availability)
        }

    @Test
    fun `maps a token timeout to offline without invoking the ad SDK`() =
        runTest {
            val tokenSource =
                FakeRewardTokenSource().apply {
                    requestBlock = CompletableDeferred()
                }
            val client = FakeRewardedAdClient()
            val gateway = gateway(FakeConsentGateway(), tokenSource, client)
            gateway.ensureConsent(activity())

            val result = gateway.loadRewarded()

            assertEquals(AdLoadResult.Unavailable(AdAvailability.Offline), result)
            assertTrue(client.loadCalls.isEmpty())
            assertEquals(AdAvailability.Offline, gateway.availability().value)
        }

    @Test
    fun `maps an SDK load timeout to offline and clears pending SDK state`() =
        runTest {
            val client =
                FakeRewardedAdClient().apply {
                    loadBlock = CompletableDeferred()
                }
            val gateway = gateway(FakeConsentGateway(), FakeRewardTokenSource(), client)
            gateway.ensureConsent(activity())

            val result = gateway.loadRewarded()

            assertEquals(AdLoadResult.Unavailable(AdAvailability.Offline), result)
            assertTrue(client.clearCalls > 0)
            assertEquals(AdAvailability.Offline, gateway.availability().value)
        }

    @Test
    fun `cancellation clears SDK state and resets availability`() =
        runTest {
            val client =
                FakeRewardedAdClient().apply {
                    loadBlock = CompletableDeferred()
                }
            val gateway = gateway(FakeConsentGateway(), FakeRewardTokenSource(), client)
            gateway.ensureConsent(activity())

            val load = launch { gateway.loadRewarded() }
            client.loadStarted.await()
            load.cancelAndJoin()

            assertTrue(client.clearCalls > 0)
            assertEquals(AdAvailability.NoFill, gateway.availability().value)
        }

    @Test
    fun `show reports completion and early dismissal without claiming entitlement`() =
        runTest {
            val completedClient =
                FakeRewardedAdClient().apply {
                    showResult = RewardedAdShowResult.Dismissed(rewardEarned = true)
                }
            val completedGateway = gateway(FakeConsentGateway(), FakeRewardTokenSource(), completedClient)
            completedGateway.ensureConsent(activity())
            completedGateway.loadRewarded()

            assertEquals(
                AdShowResult.Dismissed(rewardEarned = true),
                completedGateway.showRewarded(activity()),
            )

            val earlyDismissalClient =
                FakeRewardedAdClient().apply {
                    showResult = RewardedAdShowResult.Dismissed(rewardEarned = false)
                }
            val earlyDismissalGateway = gateway(FakeConsentGateway(), FakeRewardTokenSource(), earlyDismissalClient)
            earlyDismissalGateway.ensureConsent(activity())
            earlyDismissalGateway.loadRewarded()

            assertEquals(
                AdShowResult.Dismissed(rewardEarned = false),
                earlyDismissalGateway.showRewarded(activity()),
            )
        }

    @Test
    fun `show fails closed when the current session cannot be authenticated`() =
        runTest {
            val tokenSource =
                FakeRewardTokenSource().apply {
                    currentSessionUserIdResult = Result.failure(SyncException(SyncError.Auth))
                }
            val client = FakeRewardedAdClient()
            val gateway = gateway(FakeConsentGateway(), tokenSource, client)
            gateway.ensureConsent(activity())
            gateway.loadRewarded()

            assertEquals(AdShowResult.Unauthenticated, gateway.showRewarded(activity()))
            assertEquals(0, client.showCalls)
        }

    @Test
    fun `disabled runtime never asks for consent or loads an ad`() =
        runTest {
            val consent = FakeConsentGateway()
            val tokenSource = FakeRewardTokenSource()
            val client = FakeRewardedAdClient()
            val gateway =
                gateway(
                    consent,
                    tokenSource,
                    client,
                    runtimeConfig = FakeAdRuntimeConfig(adsEnabled = false),
                )

            assertEquals(AdAvailability.Disabled, gateway.availability().value)
            assertEquals(ConsentResult.Unavailable, gateway.ensureConsent(activity()))
            assertEquals(AdLoadResult.Unavailable(AdAvailability.Disabled), gateway.loadRewarded())
            assertEquals(0, consent.calls)
            assertEquals(0, tokenSource.requestCalls)
            assertTrue(client.loadCalls.isEmpty())
        }

    private fun gateway(
        consentGateway: FakeConsentGateway,
        tokenSource: FakeRewardTokenSource,
        client: FakeRewardedAdClient,
        noFillStreak: NoFillStreak = NoFillStreak(),
        runtimeConfig: AdRuntimeConfig = FakeAdRuntimeConfig(adsEnabled = true),
        mainDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ): AdMobAdGateway =
        AdMobAdGateway(
            consentGateway = consentGateway,
            rewardTokenSource = tokenSource,
            rewardedAdClient = client,
            adErrorMapper = AdErrorMapper(),
            noFillStreak = noFillStreak,
            clock = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC),
            adRuntimeConfig = runtimeConfig,
            mainDispatcher = mainDispatcher,
        )

    private fun token(
        customData: String = "custom-data",
        expiresAt: Instant = Instant.parse("2026-08-14T01:00:00Z"),
    ): RewardToken =
        RewardToken(
            customData = customData,
            expiresAt = expiresAt,
            sessionUserId = "user-1",
        )

    private fun activity(): Activity = Activity()

    private class FakeAdRuntimeConfig(
        override val adsEnabled: Boolean,
    ) : AdRuntimeConfig

    private class FakeConsentGateway : UmpConsentGateway {
        var result: ConsentResult = ConsentResult.Granted
        var calls = 0
        var block: CompletableDeferred<Unit>? = null
        val started = CompletableDeferred<Unit>()

        override suspend fun ensureConsent(activity: Activity): ConsentResult {
            calls += 1
            started.complete(Unit)
            block?.await()
            return result
        }
    }

    private class FakeRewardTokenSource : RewardTokenSource {
        var tokenResult: Result<RewardToken> =
            Result.success(
                RewardToken(
                    customData = "custom-data",
                    expiresAt = Instant.parse("2026-08-14T01:00:00Z"),
                    sessionUserId = "user-1",
                ),
            )
        var tokenResults: List<Result<RewardToken>> = emptyList()
        var currentSessionUserIdResult: Result<String?> = Result.success("user-1")
        var requestCalls = 0
        var requestBlock: CompletableDeferred<Unit>? = null
        val requestStarted = CompletableDeferred<Unit>()

        override suspend fun requestToken(): Result<RewardToken> {
            requestCalls += 1
            requestStarted.complete(Unit)
            blockAndReturn()
            return tokenResults.getOrNull(requestCalls - 1) ?: tokenResult
        }

        override suspend fun currentSessionUserId(): Result<String?> = currentSessionUserIdResult

        private suspend fun blockAndReturn() {
            requestBlock?.await()
        }
    }

    private class FakeRewardedAdClient : RewardedAdClient {
        data class LoadCall(
            val adUnitId: String,
            val customData: String,
            val nonPersonalized: Boolean,
        )

        val loadCalls = mutableListOf<LoadCall>()
        var loadResult: RewardedAdLoadResult = RewardedAdLoadResult.Loaded
        var loadBlock: CompletableDeferred<Unit>? = null
        val loadStarted = CompletableDeferred<Unit>()
        var clearCalls = 0
        var showCalls = 0
        var showResult: RewardedAdShowResult = RewardedAdShowResult.Dismissed(rewardEarned = false)

        override suspend fun load(
            adUnitId: String,
            customData: String,
            nonPersonalized: Boolean,
        ): RewardedAdLoadResult {
            loadCalls += LoadCall(adUnitId, customData, nonPersonalized)
            loadStarted.complete(Unit)
            loadBlock?.await()
            return loadResult
        }

        override suspend fun show(activity: Activity): RewardedAdShowResult {
            showCalls += 1
            return showResult
        }

        override fun clear() {
            clearCalls += 1
        }
    }
}
