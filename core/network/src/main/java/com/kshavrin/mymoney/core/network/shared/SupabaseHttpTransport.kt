package com.kshavrin.mymoney.core.network.shared

import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class SupabaseHttpTransport
    @Inject
    constructor(
        private val config: SupabaseConfig,
        @SharedSupabaseClient private val client: OkHttpClient,
        private val json: Json,
    ) {
        suspend fun post(
            path: String,
            payload: JsonObject,
            accessToken: String? = null,
            mapBadRequestToAuth: Boolean = false,
            mapMembershipDeniedToAuth: Boolean = false,
            mapAccountDeletionWorkspaceConflict: Boolean = false,
            preservePostgrestConflict: Boolean = false,
            callTimeoutMillis: Long? = null,
        ): Result<JsonElement> =
            execute(
                Request
                    .Builder()
                    .url(config.urlFor(path))
                    .header(API_KEY_HEADER, config.anonKey)
                    .apply { accessToken?.let { header(AUTHORIZATION_HEADER, "Bearer $it") } }
                    .post(json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE))
                    .build(),
                mapBadRequestToAuth,
                mapMembershipDeniedToAuth,
                mapAccountDeletionWorkspaceConflict,
                preservePostgrestConflict = preservePostgrestConflict,
                callTimeoutMillis = callTimeoutMillis,
            ).map(SupabaseHttpResponse::body)

        suspend fun get(
            path: String,
            accessToken: String,
        ): Result<JsonElement> =
            execute(
                Request
                    .Builder()
                    .url(config.urlFor(path))
                    .header(API_KEY_HEADER, config.anonKey)
                    .header(AUTHORIZATION_HEADER, "Bearer $accessToken")
                    .get()
                    .build(),
            ).map(SupabaseHttpResponse::body)

        internal suspend fun getWithExactCount(
            path: String,
            accessToken: String,
        ): Result<SupabaseHttpResponse> =
            execute(
                Request
                    .Builder()
                    .url(config.urlFor(path))
                    .header(API_KEY_HEADER, config.anonKey)
                    .header(AUTHORIZATION_HEADER, "Bearer $accessToken")
                    .header(PREFER_HEADER, "count=exact")
                    .get()
                    .build(),
            )

        private suspend fun execute(
            request: Request,
            mapBadRequestToAuth: Boolean = false,
            mapMembershipDeniedToAuth: Boolean = false,
            mapAccountDeletionWorkspaceConflict: Boolean = false,
            preservePostgrestConflict: Boolean = false,
            callTimeoutMillis: Long? = null,
        ): Result<SupabaseHttpResponse> {
            if (!config.isConfigured) return Result.failure(SyncException(SyncError.Server))
            return suspendCancellableCoroutine { continuation ->
                val call = client.newCall(request)
                callTimeoutMillis?.let { timeoutMillis ->
                    call.timeout().timeout(timeoutMillis, TimeUnit.MILLISECONDS)
                }
                continuation.invokeOnCancellation { call.cancel() }
                runCatching {
                    call.enqueue(
                        object : Callback {
                            override fun onFailure(
                                call: Call,
                                error: IOException,
                            ) {
                                if (continuation.isActive) {
                                    continuation.resume(Result.failure<SupabaseHttpResponse>(error).mapFailure())
                                }
                            }

                            override fun onResponse(
                                call: Call,
                                response: Response,
                            ) {
                                val result =
                                    runCatching {
                                        response.use {
                                            val responseBody = response.body?.string().orEmpty()
                                            if (!response.isSuccessful) {
                                                throw SupabaseHttpException(
                                                    statusCode = response.code,
                                                    responseBody = responseBody,
                                                    mapBadRequestToAuth = mapBadRequestToAuth,
                                                    mapMembershipDeniedToAuth = mapMembershipDeniedToAuth,
                                                    mapAccountDeletionWorkspaceConflict =
                                                        mapAccountDeletionWorkspaceConflict,
                                                    preservePostgrestConflict = preservePostgrestConflict,
                                                )
                                            }
                                            SupabaseHttpResponse(
                                                body =
                                                    responseBody
                                                        .takeIf(String::isNotBlank)
                                                        ?.let(json::parseToJsonElement)
                                                        ?: JsonNull,
                                                contentRange = response.header(CONTENT_RANGE_HEADER),
                                            )
                                        }
                                    }.mapFailure()
                                if (continuation.isActive) {
                                    continuation.resume(result)
                                }
                            }
                        },
                    )
                }.onFailure { error ->
                    if (continuation.isActive) {
                        continuation.resume(Result.failure<SupabaseHttpResponse>(error).mapFailure())
                    }
                }
            }
        }

        private companion object {
            const val API_KEY_HEADER = "apikey"
            const val AUTHORIZATION_HEADER = "Authorization"
            const val CONTENT_RANGE_HEADER = "Content-Range"
            const val PREFER_HEADER = "Prefer"
            val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        }
    }

internal data class SupabaseHttpResponse(
    val body: JsonElement,
    val contentRange: String?,
)

private class SupabaseHttpException(
    val statusCode: Int,
    val responseBody: String,
    val mapBadRequestToAuth: Boolean,
    val mapMembershipDeniedToAuth: Boolean,
    val mapAccountDeletionWorkspaceConflict: Boolean,
    val preservePostgrestConflict: Boolean,
) : Exception()

internal class SupabasePostgrestConflictException(
    val responseBody: String,
) : SyncException(SyncError.Conflict)

private fun <T> Result<T>.mapFailure(): Result<T> =
    exceptionOrNull()?.let { error -> Result.failure(error.toSyncException()) } ?: this

private fun Throwable.toSyncException(): Throwable =
    when (this) {
        is CancellationException -> this
        is SyncException -> this
        is IOException -> SyncException(SyncError.Network)
        is SerializationException -> SyncException(SyncError.Server)
        is SupabaseHttpException -> toSyncException()
        else -> SyncException(SyncError.Unknown)
    }

private fun SupabaseHttpException.toSyncException(): Throwable =
    if (responseBody.hasEntitlementRequired()) {
        SyncException(SyncError.EntitlementRequired)
    } else if (statusCode == 409 && preservePostgrestConflict) {
        SupabasePostgrestConflictException(responseBody)
    } else {
        SyncException(
            when (statusCode) {
                401, 403 -> SyncError.Auth
                400 ->
                    if (mapMembershipDeniedToAuth && responseBody.hasSqlState42501()) {
                        SyncError.Auth
                    } else if (
                        mapAccountDeletionWorkspaceConflict &&
                        responseBody.hasAccountDeletionWorkspaceConflict()
                    ) {
                        SyncError.Conflict
                    } else if (mapBadRequestToAuth) {
                        SyncError.Auth
                    } else {
                        SyncError.Unknown
                    }
                409 -> SyncError.Conflict
                429 -> SyncError.Quota
                in 500..599 -> SyncError.Server
                else -> SyncError.Unknown
            },
        )
    }

private fun String.hasSqlState42501(): Boolean =
    runCatching {
        Json
            .parseToJsonElement(this)
            .jsonObject["code"]
            ?.jsonPrimitive
            ?.content == "42501"
    }.getOrDefault(false)

private fun String.hasEntitlementRequired(): Boolean =
    runCatching {
        Json
            .parseToJsonElement(this)
            .jsonObject["message"]
            ?.jsonPrimitive
            ?.content == "entitlement_required"
    }.getOrDefault(false)

private fun String.hasAccountDeletionWorkspaceConflict(): Boolean =
    runCatching {
        Json
            .parseToJsonElement(this)
            .jsonObject["message"]
            ?.jsonPrimitive
            ?.content
            ?.startsWith("account deletion requires leaving or deleting the active workspace") == true
    }.getOrDefault(false)

private fun SupabaseConfig.urlFor(path: String): String =
    "${url.trimEnd('/')}/${path.trimStart('/')}"
