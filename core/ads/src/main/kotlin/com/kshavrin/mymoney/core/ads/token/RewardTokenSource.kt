package com.kshavrin.mymoney.core.ads.token

import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.network.shared.SharedAuth
import com.kshavrin.mymoney.core.network.shared.SupabaseHttpTransport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class RewardToken(
    val customData: String,
    val expiresAt: Instant,
    val sessionUserId: String? = null,
)

interface RewardTokenSource {
    suspend fun requestToken(): Result<RewardToken>

    suspend fun currentSessionUserId(): Result<String?> = Result.success(null)
}

@Singleton
class SupabaseRewardTokenSource
    @Inject
    constructor(
        private val auth: SharedAuth,
        private val http: SupabaseHttpTransport,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : RewardTokenSource {
        override suspend fun requestToken(): Result<RewardToken> =
            withContext(ioDispatcher) {
                val accessToken = auth.accessToken().getOrElse { return@withContext Result.failure(it) }
                val session =
                    auth
                        .currentSession()
                        ?.takeIf { it.user.id.isNotBlank() && it.accessToken == accessToken }
                        ?: return@withContext Result.failure(SyncException(SyncError.Auth))
                http
                    .post(
                        path = "functions/v1/create-ad-reward-token",
                        payload = JsonObject(emptyMap()),
                        accessToken = accessToken,
                        callTimeoutMillis = REWARD_TOKEN_CALL_TIMEOUT_MILLIS,
                    ).mapCatching { response ->
                        response.jsonObject.toRewardToken().copy(sessionUserId = session.user.id)
                    }
            }

        override suspend fun currentSessionUserId(): Result<String?> =
            withContext(ioDispatcher) {
                val accessToken = auth.accessToken().getOrElse { return@withContext Result.failure(it) }
                auth
                    .currentSession()
                    ?.takeIf { it.user.id.isNotBlank() && it.accessToken == accessToken }
                    ?.user
                    ?.id
                    ?.let(Result.Companion::success)
                    ?: Result.failure(SyncException(SyncError.Auth))
            }
    }

private fun JsonObject.toRewardToken(): RewardToken {
    val customData = this["custom_data"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
        ?: throw IllegalStateException("Missing rewarded ad custom_data")
    val expiresAt =
        this["expires_at"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.toRewardTokenExpiry()
            ?: throw IllegalStateException("Missing rewarded ad expires_at")
    return RewardToken(customData = customData, expiresAt = expiresAt)
}

private fun String.toRewardTokenExpiry(): Instant =
    toLongOrNull()?.let(Instant::ofEpochSecond) ?: Instant.parse(this)

private const val REWARD_TOKEN_CALL_TIMEOUT_MILLIS = 10_000L
