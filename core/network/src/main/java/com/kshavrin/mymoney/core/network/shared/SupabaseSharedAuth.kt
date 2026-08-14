package com.kshavrin.mymoney.core.network.shared

import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseSharedAuth
    @Inject
    constructor(
        private val config: SupabaseConfig,
        private val http: SupabaseHttpTransport,
        private val sessionStore: SharedSessionStore,
        private val clock: Clock,
        private val authSessionLifecycle: AuthSessionLifecycle = NoOpAuthSessionLifecycle,
    ) : SharedAuth {
        @Volatile private var session: ActiveSession? = null
        private val sessionMutex = Mutex()
        private val sessionCacheLock = Any()

        @Volatile private var sessionGeneration = 0L

        @Volatile private var resetInProgress = false

        override fun currentSession(): SharedSession? =
            synchronized(sessionCacheLock) {
                cachedOrStoredSessionLocked()?.session
            }

        override suspend fun accessToken(): Result<String> =
            sessionMutex.withLock {
                val active = cachedOrStoredSession() ?: return@withLock Result.failure(SyncException(SyncError.Auth))
                if (active.expiresAt.isAfter(clock.instant().plusSeconds(REFRESH_AHEAD_SECONDS))) {
                    Result.success(active.session.accessToken)
                } else {
                    refresh(active.refreshToken).map { it.session.accessToken }
                }
            }

        override suspend fun signInWithGoogle(
            googleIdToken: String,
            nonce: String,
        ): Result<SharedSession> {
            if (!config.isGoogleSignInConfigured || googleIdToken.isBlank() || nonce.isBlank()) {
                return Result.failure(SyncException(SyncError.Auth))
            }
            val operationGeneration = currentSessionGeneration()
            val resetWasInProgress = resetInProgress
            return sessionMutex.withLock {
                if (
                    resetWasInProgress ||
                    resetInProgress ||
                    !isCurrentSessionGeneration(operationGeneration)
                ) {
                    return@withLock Result.failure(SyncException(SyncError.Auth))
                }
                http
                    .post(
                        path = "auth/v1/token?grant_type=id_token",
                        payload =
                            buildJsonObject {
                                put("provider", "google")
                                put("id_token", googleIdToken)
                                put("nonce", nonce)
                            },
                        mapBadRequestToAuth = true,
                    ).mapCatching { response ->
                        saveSession(response.jsonObject).session
                    }.also { result ->
                        if (result.isSuccess) authSessionLifecycle.invalidate()
                    }
            }
        }

        override suspend fun signOut(): Result<Unit> {
            return sessionMutex.withLock {
                val active = cachedOrStoredSession() ?: run {
                    authSessionLifecycle.invalidate()
                    return@withLock Result.success(Unit)
                }
                try {
                    try {
                        http
                            .post(
                                path = "auth/v1/logout",
                                payload = buildJsonObject { },
                                accessToken = active.session.accessToken,
                            ).map { Unit }
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        Result.failure(t)
                    }
                } finally {
                    clearSession()
                }
            }
        }

        override suspend fun clearLocalSession() {
            sessionMutex.withLock { clearSession() }
        }

        override suspend fun resetLocalSession(clearSecrets: () -> Unit) {
            sessionMutex.withLock {
                try {
                    synchronized(sessionCacheLock) {
                        resetInProgress = true
                        sessionGeneration += 1
                        try {
                            clearSessionLocked()
                            clearSecrets()
                        } finally {
                            resetInProgress = false
                        }
                    }
                } finally {
                    authSessionLifecycle.invalidate()
                }
            }
        }

        private suspend fun refresh(refreshToken: String): Result<ActiveSession> =
            http
                .post(
                    path = "auth/v1/token?grant_type=refresh_token",
                    payload = buildJsonObject { put("refresh_token", refreshToken) },
                    mapBadRequestToAuth = true,
                ).mapCatching { response ->
                    saveSession(response.jsonObject)
                }.also { result ->
                    if (result.isAuthFailure()) clearSession()
                }

        private fun cachedOrStoredSession(): ActiveSession? =
            synchronized(sessionCacheLock) {
                cachedOrStoredSessionLocked()
            }

        private fun cachedOrStoredSessionLocked(): ActiveSession? =
            session ?: sessionStore.readSharedSession()?.toActiveSession()?.also { session = it }

        private fun saveSession(response: kotlinx.serialization.json.JsonObject): ActiveSession =
            response.toActiveSession().also { active ->
                synchronized(sessionCacheLock) {
                    sessionStore.writeSharedSession(active.toStoredSession())
                    session = active
                }
            }

        private fun currentSessionGeneration(): Long = sessionGeneration

        private fun isCurrentSessionGeneration(generation: Long): Boolean = generation == sessionGeneration

        private fun clearSession() {
            synchronized(sessionCacheLock) { clearSessionLocked() }
            authSessionLifecycle.invalidate()
        }

        private fun clearSessionLocked() {
            session = null
            sessionStore.clearSharedSession()
        }

        private fun kotlinx.serialization.json.JsonObject.toActiveSession(): ActiveSession {
            val expiresAt =
                get("expires_at")?.jsonPrimitive?.longOrNull
                    ?: get("expires_in")?.jsonPrimitive?.longOrNull?.let { seconds ->
                        clock.instant().plusSeconds(seconds).epochSecond
                    }
                    ?: throw SyncException(SyncError.Server)
            return ActiveSession(
                session =
                    SharedSession(
                        user =
                            SharedUser(
                                id = requiredObject("user").requiredString("id"),
                                email = requiredObject("user").requiredString("email"),
                            ),
                        accessToken = requiredString("access_token"),
                    ),
                refreshToken = requiredString("refresh_token"),
                expiresAt = Instant.ofEpochSecond(expiresAt),
            )
        }

        private fun StoredSharedSession.toActiveSession(): ActiveSession? =
            takeIf {
                it.userId.isNotBlank() &&
                    it.userEmail.isNotBlank() &&
                    it.accessToken.isNotBlank() &&
                    it.refreshToken.isNotBlank() &&
                    it.accessTokenExpiresAtEpochSeconds > 0L
            }?.let {
                ActiveSession(
                    session =
                        SharedSession(
                            user = SharedUser(id = userId, email = userEmail),
                            accessToken = accessToken,
                        ),
                    refreshToken = refreshToken,
                    expiresAt = Instant.ofEpochSecond(accessTokenExpiresAtEpochSeconds),
                )
            }

        private fun ActiveSession.toStoredSession(): StoredSharedSession =
            StoredSharedSession(
                userId = session.user.id,
                userEmail = session.user.email,
                accessToken = session.accessToken,
                refreshToken = refreshToken,
                accessTokenExpiresAtEpochSeconds = expiresAt.epochSecond,
            )

        private data class ActiveSession(
            val session: SharedSession,
            val refreshToken: String,
            val expiresAt: Instant,
        )

        private companion object {
            const val REFRESH_AHEAD_SECONDS = 60L
        }
    }

private fun Result<*>.isAuthFailure(): Boolean =
    (exceptionOrNull() as? SyncException)?.syncError == SyncError.Auth

internal suspend fun SharedAuth.requireAccessToken(): String = accessToken().getOrThrow()

internal fun kotlinx.serialization.json.JsonObject.requiredObject(name: String): kotlinx.serialization.json.JsonObject =
    get(name)?.jsonObject ?: throw SyncException(SyncError.Server)

internal fun kotlinx.serialization.json.JsonObject.requiredString(name: String): String =
    get(name)?.jsonPrimitive?.content?.takeIf(String::isNotBlank) ?: throw SyncException(SyncError.Server)
