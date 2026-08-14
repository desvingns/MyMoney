package com.kshavrin.mymoney.core.ads.token

import com.kshavrin.mymoney.core.common.di.IoDispatcher
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
)

interface RewardTokenSource {
    suspend fun requestToken(): Result<RewardToken>
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
                http
                    .post(
                        path = "functions/v1/create-ad-reward-token",
                        payload = JsonObject(emptyMap()),
                        accessToken = accessToken,
                    ).mapCatching { response ->
                        response.jsonObject.toRewardToken()
                    }
            }
    }

private fun JsonObject.toRewardToken(): RewardToken {
    val customData = this["custom_data"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
        ?: throw IllegalStateException("Missing rewarded ad custom_data")
    val expiresAt = this["expires_at"]?.jsonPrimitive?.contentOrNull?.let(Instant::parse)
        ?: throw IllegalStateException("Missing rewarded ad expires_at")
    return RewardToken(customData = customData, expiresAt = expiresAt)
}
