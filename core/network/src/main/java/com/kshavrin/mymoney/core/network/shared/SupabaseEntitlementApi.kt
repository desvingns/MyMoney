package com.kshavrin.mymoney.core.network.shared

import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.domain.model.EntitlementSnapshot
import com.kshavrin.mymoney.core.domain.model.EntitlementSource
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseEntitlementApi
    @Inject
    constructor(
        private val auth: SharedAuth,
        private val http: SupabaseHttpTransport,
    ) {
        suspend fun bindGooglePlayPurchase(purchaseToken: String): Result<Unit> {
            val accessToken = auth.accessToken().getOrElse { return Result.failure(it) }
            return http
                .post(
                    path = "functions/v1/bind-google-play-purchase",
                    payload = buildJsonObject { put("purchase_token", purchaseToken) },
                    accessToken = accessToken,
                ).map { Unit }
        }

        suspend fun getMyEntitlement(): Result<EntitlementSnapshot?> {
            val accessToken = auth.accessToken().getOrElse { return Result.failure(it) }
            return http
                .post(
                    path = "rest/v1/rpc/get_my_entitlement",
                    payload = JsonObject(emptyMap()),
                    accessToken = accessToken,
                ).mapCatching { response -> response.jsonObject.toEntitlementSnapshotOrNull() }
        }
    }

private fun JsonObject.toEntitlementSnapshotOrNull(): EntitlementSnapshot? {
    val source = this["source"]?.jsonPrimitive?.contentOrNull ?: return null
    val startsAt =
        this["starts_at"]?.jsonPrimitive?.contentOrNull?.let(Instant::parse)
            ?: throw SyncException(SyncError.Server)
    return EntitlementSnapshot(
        source = source.toEntitlementSource(),
        startsAt = startsAt,
        expiresAt = this["expires_at"]?.jsonPrimitive?.contentOrNull?.let(Instant::parse),
        inTrial = this["in_trial"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
        revokedAt = null,
    )
}

private fun String.toEntitlementSource(): EntitlementSource =
    when (this) {
        "plus_monthly", "subscription_monthly" -> EntitlementSource.SUBSCRIPTION_MONTHLY
        "plus_yearly", "subscription_yearly", "google_play" -> EntitlementSource.SUBSCRIPTION_YEARLY
        "admob_reward" -> EntitlementSource.AD_REWARD
        "whitelist", "activation_code" -> EntitlementSource.WHITELIST
        else -> throw SyncException(SyncError.Server)
    }
