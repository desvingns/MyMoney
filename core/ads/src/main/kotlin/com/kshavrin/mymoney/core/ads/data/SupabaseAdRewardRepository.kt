package com.kshavrin.mymoney.core.ads.data

import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.domain.ads.AdRewardRepository
import com.kshavrin.mymoney.core.domain.ads.AdRewardState
import com.kshavrin.mymoney.core.domain.ads.ConfirmationOutcome
import com.kshavrin.mymoney.core.domain.ads.FrozenReason
import com.kshavrin.mymoney.core.network.shared.SharedAuth
import com.kshavrin.mymoney.core.network.shared.SupabaseHttpTransport
import javax.inject.Inject
import javax.inject.Singleton
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

@Singleton
class SupabaseAdRewardRepository
    @Inject
    constructor(
        private val auth: SharedAuth,
        private val http: SupabaseHttpTransport,
        private val backoff: AdRewardBackoff,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : AdRewardRepository {
        private val mutableState = MutableStateFlow<AdRewardState?>(null)

        override val state: StateFlow<AdRewardState?> = mutableState.asStateFlow()

        override suspend fun refresh(): Result<AdRewardState> =
            withContext(ioDispatcher) {
                val accessToken = auth.accessToken().getOrElse { return@withContext Result.failure(it) }
                http
                    .post(
                        path = "rest/v1/rpc/get_ad_reward_state",
                        payload = JsonObject(emptyMap()),
                        accessToken = accessToken,
                        callTimeoutMillis = AD_REWARD_STATE_CALL_TIMEOUT_MILLIS,
                    ).mapCatching(JsonElement::toAdRewardState)
                    .onSuccess { refreshedState -> mutableState.value = refreshedState }
            }

        override suspend fun awaitConfirmation(previous: AdRewardState): ConfirmationOutcome =
            withTimeoutOrNull(backoff.maximumWaitMillis) {
                awaitServerConfirmation(previous)
            } ?: ConfirmationOutcome.PendingConfirmation

        private suspend fun awaitServerConfirmation(previous: AdRewardState): ConfirmationOutcome {
            confirmationOutcome(previous)?.let { return it }
            for (delayMillis in backoff.delaysMillis) {
                delay(delayMillis)
                confirmationOutcome(previous)?.let { return it }
            }
            return ConfirmationOutcome.PendingConfirmation
        }

        private suspend fun confirmationOutcome(previous: AdRewardState): ConfirmationOutcome? {
            val refreshed = refresh()
            val current = refreshed.getOrNull()
            if (current != null) {
                return when {
                    current.plusActive -> ConfirmationOutcome.PlusGranted(current)
                    current.progress > previous.progress -> ConfirmationOutcome.ProgressIncreased(current)
                    else -> null
                }
            }
            return if (refreshed.isAuthFailure()) ConfirmationOutcome.PendingConfirmation else null
        }
    }

private fun JsonElement.toAdRewardState(): AdRewardState =
    jsonObject.let { response ->
        AdRewardState(
            progress = response.requiredInt("progress"),
            required = response.requiredInt("required"),
            frozen = response.requiredBoolean("frozen"),
            frozenReason = response.nullableString("frozenReason")?.toFrozenReason(),
            plusActive = response.requiredBoolean("plusActive"),
            plusProvider = response.nullableString("plusProvider"),
            plusExpiresAt = response.nullableString("plusExpiresAt")?.toInstant(),
        )
    }

private fun JsonObject.requiredInt(name: String): Int =
    (this[name] as? JsonPrimitive)
        ?.takeUnless(JsonPrimitive::isString)
        ?.intOrNull
        ?: invalidAdRewardState()

private fun JsonObject.requiredBoolean(name: String): Boolean =
    (this[name] as? JsonPrimitive)
        ?.takeUnless(JsonPrimitive::isString)
        ?.booleanOrNull
        ?: invalidAdRewardState()

private fun JsonObject.nullableString(name: String): String? =
    when (val value = this[name]) {
        JsonNull -> null
        is JsonPrimitive ->
            value
                .takeIf(JsonPrimitive::isString)
                ?.contentOrNull
                ?.takeIf(String::isNotBlank)
                ?: invalidAdRewardState()
        else -> invalidAdRewardState()
    }

private fun String.toFrozenReason(): FrozenReason =
    removePrefix(PLUS_ACTIVE_PREFIX)
        .takeIf { provider -> provider != this && provider.isNotBlank() }
        ?.let(FrozenReason::PlusActive)
        ?: FrozenReason.Unknown

private fun String.toInstant(): Instant =
    runCatching(Instant::parse).getOrElse { invalidAdRewardState() }

private fun invalidAdRewardState(): Nothing = throw SyncException(SyncError.Server)

private fun Result<AdRewardState>.isAuthFailure(): Boolean =
    (exceptionOrNull() as? SyncException)?.syncError == SyncError.Auth

private const val PLUS_ACTIVE_PREFIX = "plus_active:"
private const val AD_REWARD_STATE_CALL_TIMEOUT_MILLIS = 5_000L
