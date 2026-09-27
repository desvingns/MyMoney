package com.kshavrin.mymoney.core.network.shared

import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

data class RemoteSupporterState(
    val purchaseCount: Int,
    val badgeEarned: Boolean,
)

@Singleton
class SupabaseSupporterApi
    @Inject
    constructor(
        private val http: SupabaseHttpTransport,
    ) {
        suspend fun postPurchase(
            userId: String,
            outcome: PurchaseOutcome.Purchased,
            accessToken: String,
        ): Result<Unit> =
            http
                .post(
                    path = "functions/v1/verify-supporter-purchase",
                    payload =
                        buildJsonObject {
                            put("purchase_token", outcome.purchaseToken)
                            put("expected_user_id", userId)
                        },
                    accessToken = accessToken,
                ).map { Unit }

        suspend fun getState(
            userId: String,
            accessToken: String,
        ): Result<RemoteSupporterState> =
            http
                .post(
                    path = "functions/v1/verify-supporter-purchase",
                    payload =
                        buildJsonObject {
                            put("refresh", true)
                            put("expected_user_id", userId)
                        },
                    accessToken = accessToken,
                ).mapCatching {
                    http
                        .getWithExactCount(
                            path = "rest/v1/supporter_purchases?select=id&user_id=eq.${userId.percentEncodeQueryValue()}&verified_at=not.is.null",
                            accessToken = accessToken,
                        ).getOrThrow()
                        .let { response ->
                            val purchaseCount = response.contentRange.exactCount()
                            RemoteSupporterState(
                                purchaseCount = purchaseCount,
                                badgeEarned = purchaseCount > 0,
                            )
                        }
                }
    }

private fun String?.exactCount(): Int =
    this
        ?.substringAfter('/', missingDelimiterValue = "")
        ?.toIntOrNull()
        ?: error("Missing exact Content-Range count")

private fun String.percentEncodeQueryValue(): String =
    buildString(length) {
        toByteArray(Charsets.UTF_8).forEach { byte ->
            val value = byte.toInt() and 0xFF
            if (
                value in 'A'.code..'Z'.code ||
                value in 'a'.code..'z'.code ||
                value in '0'.code..'9'.code ||
                value == '-'.code ||
                value == '.'.code ||
                value == '_'.code ||
                value == '~'.code
            ) {
                append(value.toChar())
            } else {
                append('%')
                append(HEX_DIGITS[value ushr 4])
                append(HEX_DIGITS[value and 0x0F])
            }
        }
    }

private const val HEX_DIGITS = "0123456789ABCDEF"
