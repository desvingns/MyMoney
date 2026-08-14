package com.kshavrin.mymoney.core.network.shared

import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant
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
        ): Result<Unit> {
            val postResult =
                http
                    .post(
                        path = "rest/v1/supporter_purchases",
                        payload =
                            buildJsonObject {
                                put("user_id", userId)
                                put("product_id", outcome.productId)
                                put("purchase_token", outcome.purchaseToken)
                                put("purchased_at", Instant.ofEpochMilli(outcome.purchasedAtMillis).toString())
                            },
                        accessToken = accessToken,
                        preservePostgrestConflict = true,
                    ).map { Unit }
            val failure = postResult.exceptionOrNull() ?: return postResult
            if (!failure.isDuplicatePurchase()) {
                return postResult
            }
            return if (duplicateBelongsToUser(userId, outcome.purchaseToken, accessToken)) {
                Result.success(Unit)
            } else {
                Result.failure(failure)
            }
        }

        private suspend fun duplicateBelongsToUser(
            userId: String,
            purchaseToken: String,
            accessToken: String,
        ): Boolean =
            http
                .get(
                    path =
                        "rest/v1/supporter_purchases?select=id&user_id=eq.${userId.percentEncodeQueryValue()}&purchase_token=eq.${purchaseToken.percentEncodeQueryValue()}",
                    accessToken = accessToken,
                ).mapCatching { response ->
                    response.jsonArray.isNotEmpty()
                }.getOrDefault(false)

        suspend fun getState(
            userId: String,
            accessToken: String,
        ): Result<RemoteSupporterState> =
            http
                .getWithExactCount(
                    path = "rest/v1/supporter_purchases?select=id&user_id=eq.$userId",
                    accessToken = accessToken,
                ).mapCatching { response ->
                    val purchaseCount = response.contentRange.exactCount()
                    RemoteSupporterState(
                        purchaseCount = purchaseCount,
                        badgeEarned = purchaseCount > 0,
                    )
                }
    }

private fun Throwable.isDuplicatePurchase(): Boolean =
    (this as? SupabasePostgrestConflictException)?.isDuplicatePurchaseTokenConflict() == true

private fun SupabasePostgrestConflictException.isDuplicatePurchaseTokenConflict(): Boolean =
    runCatching {
        val error = Json.parseToJsonElement(responseBody).jsonObject
        val code = error["code"]?.jsonPrimitive?.content
        val message = error["message"]?.jsonPrimitive?.content.orEmpty()
        val details = error["details"]?.jsonPrimitive?.content.orEmpty()
        code == "23505" &&
            (
                message.contains("supporter_purchases_purchase_token_key") ||
                    details.contains("Key (purchase_token)=")
            )
    }.getOrDefault(false)

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
