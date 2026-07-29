package com.kshavrin.mymoney.core.network.shared

import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseSharedAuth
    @Inject
    constructor(
        private val config: SupabaseConfig,
        private val http: SupabaseHttpTransport,
    ) : SharedAuth {
        @Volatile private var session: SharedSession? = null

        override fun currentSession(): SharedSession? = session

        override suspend fun signInWithGoogle(
            googleIdToken: String,
            nonce: String,
        ): Result<SharedSession> {
            if (!config.isGoogleSignInConfigured || googleIdToken.isBlank() || nonce.isBlank()) {
                return Result.failure(SyncException(SyncError.Auth))
            }
            return http
                .post(
                    path = "auth/v1/token?grant_type=id_token",
                    payload =
                        buildJsonObject {
                            put("provider", "google")
                            put("id_token", googleIdToken)
                            put("nonce", nonce)
                        },
                ).mapCatching { response ->
                    val root = response.jsonObject
                    SharedSession(
                        user =
                            SharedUser(
                                id = root.requiredObject("user").requiredString("id"),
                                email = root.requiredObject("user").requiredString("email"),
                            ),
                        accessToken = root.requiredString("access_token"),
                    )
                }.onSuccess { session = it }
        }

        override suspend fun signOut(): Result<Unit> {
            val accessToken = session?.accessToken ?: return Result.success(Unit)
            return http
                .post(
                    path = "auth/v1/logout",
                    payload = buildJsonObject { },
                    accessToken = accessToken,
                ).map { Unit }
                .also { session = null }
        }
    }

internal fun SharedAuth.requireAccessToken(): String =
    currentSession()?.accessToken ?: throw SyncException(SyncError.Auth)

internal fun kotlinx.serialization.json.JsonObject.requiredObject(name: String): kotlinx.serialization.json.JsonObject =
    get(name)?.jsonObject ?: throw SyncException(SyncError.Server)

internal fun kotlinx.serialization.json.JsonObject.requiredString(name: String): String =
    get(name)?.jsonPrimitive?.content?.takeIf(String::isNotBlank) ?: throw SyncException(SyncError.Server)
