package com.kshavrin.mymoney.core.ads.data

import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.domain.ads.AdRewardRepository
import com.kshavrin.mymoney.core.domain.ads.AdRewardState
import com.kshavrin.mymoney.core.domain.ads.ConfirmationOutcome
import com.kshavrin.mymoney.core.domain.ads.FrozenReason
import com.kshavrin.mymoney.core.network.shared.SharedAuth
import com.kshavrin.mymoney.core.network.shared.AuthSessionLifecycle
import com.kshavrin.mymoney.core.network.shared.NoOpAuthSessionLifecycle
import com.kshavrin.mymoney.core.network.shared.SupabaseHttpTransport
import javax.inject.Inject
import javax.inject.Singleton
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

@Singleton
class SupabaseAdRewardRepository
    @Inject
    constructor(
        private val auth: SharedAuth,
        private val http: SupabaseHttpTransport,
        private val backoff: AdRewardBackoff,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        private val authSessionLifecycle: AuthSessionLifecycle = NoOpAuthSessionLifecycle,
    ) : AdRewardRepository {
        private val stateLock = Any()
        private val refreshMutex = Mutex()
        private val mutableState = MutableStateFlow<AdRewardState?>(null)
        private var cachedSession: RewardSession? = null
        private var sessionGeneration = 0L

        init {
            authSessionLifecycle.addInvalidationListener(::invalidateSession)
        }

        override val state: StateFlow<AdRewardState?> = mutableState.asStateFlow()

        override fun invalidateSession() {
            synchronized(stateLock) {
                sessionGeneration += 1
                cachedSession = null
                mutableState.value = null
            }
        }

        override suspend fun refresh(): Result<AdRewardState> =
            refreshFor(expectedSession = null)

        override suspend fun awaitConfirmation(previous: AdRewardState): ConfirmationOutcome {
            val session = confirmationSessionFor(previous) ?: return ConfirmationOutcome.PendingConfirmation
            return withTimeoutOrNull(backoff.maximumWaitMillis) {
                awaitServerConfirmation(previous, session)
            } ?: ConfirmationOutcome.PendingConfirmation
        }

        private suspend fun refreshFor(expectedSession: RewardSession?): Result<AdRewardState> =
            refreshMutex.withLock {
                withContext(ioDispatcher) {
                    clearStateForInactiveSession()
                    val sessionBeforeAccessToken = expectedSession ?: currentSession()
                    if (sessionBeforeAccessToken == null) {
                        return@withContext Result.failure(SyncException(SyncError.Auth))
                    }
                    if (!isCurrentSession(sessionBeforeAccessToken)) {
                        clearStateFor(sessionBeforeAccessToken)
                        return@withContext Result.failure(SyncException(SyncError.Auth))
                    }
                    val accessToken =
                        auth
                            .accessToken()
                            .getOrElse { error ->
                                if (error is CancellationException) throw error
                                if (error.isAuthFailure()) sessionBeforeAccessToken?.let(::clearStateFor)
                                clearStateForInactiveSession()
                                return@withContext Result.failure(error)
                            }
                    val session =
                        currentSession(accessToken)
                            ?: run {
                                clearStateFor(sessionBeforeAccessToken)
                                clearStateForInactiveSession()
                                return@withContext Result.failure(SyncException(SyncError.Auth))
                            }
                    if (session != sessionBeforeAccessToken) {
                        clearStateFor(sessionBeforeAccessToken)
                        return@withContext Result.failure(SyncException(SyncError.Auth))
                    }
                    val result =
                        http
                            .post(
                                path = "rest/v1/rpc/get_ad_reward_state",
                                payload = JsonObject(emptyMap()),
                                accessToken = accessToken,
                                callTimeoutMillis = AD_REWARD_STATE_CALL_TIMEOUT_MILLIS,
                            ).toAdRewardStateResult()
                    result.fold(
                        onSuccess = { refreshedState ->
                            if (publishStateFor(session, refreshedState)) {
                                Result.success(refreshedState)
                            } else {
                                clearStateFor(session)
                                Result.failure(SyncException(SyncError.Auth))
                            }
                        },
                        onFailure = { error ->
                            if (error is CancellationException) throw error
                            if (error.isAuthFailure()) clearStateFor(session)
                            clearStateForInactiveSession()
                            Result.failure(error)
                        },
                    )
                }
            }

        private suspend fun awaitServerConfirmation(
            previous: AdRewardState,
            session: RewardSession,
        ): ConfirmationOutcome {
            confirmationOutcome(previous, session)?.let { return it }
            for (delayMillis in backoff.delaysMillis) {
                delay(delayMillis)
                confirmationOutcome(previous, session)?.let { return it }
            }
            return ConfirmationOutcome.PendingConfirmation
        }

        private suspend fun confirmationOutcome(
            previous: AdRewardState,
            session: RewardSession,
        ): ConfirmationOutcome? {
            if (!isCurrentSession(session)) {
                clearStateFor(session)
                return ConfirmationOutcome.PendingConfirmation
            }
            val refreshed = refreshFor(session)
            val current = refreshed.getOrNull()
            if (current != null && isCurrentSession(session)) {
                return when {
                    !previous.plusActive && current.plusActive -> ConfirmationOutcome.PlusGranted(current)
                    current.progress > previous.progress -> ConfirmationOutcome.ProgressIncreased(current)
                    current.totalWatched > previous.totalWatched -> ConfirmationOutcome.WatchCounted(current)
                    else -> null
                }
            }
            if (current != null) clearStateFor(session)
            return if (refreshed.isAuthFailure()) ConfirmationOutcome.PendingConfirmation else null
        }

        private fun currentSession(accessToken: String? = null): RewardSession? =
            auth
                .currentSession()
                ?.takeIf { session -> session.user.id.isNotBlank() && (accessToken == null || session.accessToken == accessToken) }
                ?.let { session ->
                    RewardSession(
                        userId = session.user.id,
                        generation = synchronized(stateLock) { sessionGeneration },
                    )
                }

        private fun isCurrentSession(session: RewardSession): Boolean = currentSession() == session

        private fun confirmationSessionFor(previous: AdRewardState): RewardSession? =
            synchronized(stateLock) {
                val session = cachedSession ?: return@synchronized null
                if (mutableState.value != previous) {
                    return@synchronized null
                }
                if (!isCurrentSession(session)) {
                    clearStateForLocked(session)
                    return@synchronized null
                }
                session
            }

        private fun clearStateForInactiveSession() {
            synchronized(stateLock) {
                cachedSession
                    ?.takeIf { !isCurrentSession(it) }
                    ?.let(::clearStateForLocked)
            }
        }

        private fun clearStateFor(session: RewardSession) {
            synchronized(stateLock) {
                if (cachedSession == session) clearStateForLocked(session)
            }
        }

        private fun publishStateFor(
            session: RewardSession,
            state: AdRewardState,
        ): Boolean =
            synchronized(stateLock) {
                if (currentSession() != session) {
                    if (cachedSession == session) clearStateForLocked(session)
                    false
                } else {
                    cachedSession = session
                    mutableState.value = state
                    true
                }
            }

        private fun clearStateForLocked(session: RewardSession) {
            if (cachedSession == session) {
                cachedSession = null
                mutableState.value = null
            }
        }
    }

private data class RewardSession(
    val userId: String,
    val generation: Long,
)

private fun Result<JsonElement>.toAdRewardStateResult(): Result<AdRewardState> =
    fold(
        onSuccess = { response ->
            try {
                Result.success(response.toAdRewardState())
            } catch (error: CancellationException) {
                throw error
            } catch (error: SyncException) {
                Result.failure(error)
            } catch (_: Throwable) {
                Result.failure(SyncException(SyncError.Server))
            }
        },
        onFailure = { error ->
            if (error is CancellationException) throw error
            Result.failure(error)
        },
    )

private fun JsonElement.toAdRewardState(): AdRewardState =
    (this as? JsonObject ?: invalidAdRewardState()).let { response ->
        AdRewardState(
            progress = response.requiredInt("progress"),
            required = response.requiredInt("required"),
            frozen = response.requiredBoolean("frozen"),
            frozenReason = response.nullableString("frozenReason")?.toFrozenReason(),
            plusActive = response.requiredBoolean("plusActive"),
            plusProvider = response.nullableString("plusProvider"),
            plusExpiresAt = response.nullableString("plusExpiresAt")?.toInstant(),
            totalWatched = response.requiredInt("totalWatched"),
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

private fun Result<*>.isAuthFailure(): Boolean = exceptionOrNull().isAuthFailure()

private fun Throwable?.isAuthFailure(): Boolean =
    (this as? SyncException)?.syncError == SyncError.Auth

private const val PLUS_ACTIVE_PREFIX = "plus_active:"
private const val AD_REWARD_STATE_CALL_TIMEOUT_MILLIS = 5_000L
