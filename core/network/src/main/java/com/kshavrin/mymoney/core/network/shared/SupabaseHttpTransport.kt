package com.kshavrin.mymoney.core.network.shared

import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

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
            )

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
            )

        private fun execute(
            request: Request,
            mapBadRequestToAuth: Boolean = false,
        ): Result<JsonElement> {
            if (!config.isConfigured) return Result.failure(SyncException(SyncError.Server))
            return runCatching {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw SupabaseHttpException(response.code, mapBadRequestToAuth)
                    responseBody.takeIf(String::isNotBlank)?.let(json::parseToJsonElement) ?: JsonNull
                }
            }.mapFailure()
        }

        private companion object {
            const val API_KEY_HEADER = "apikey"
            const val AUTHORIZATION_HEADER = "Authorization"
            val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        }
    }

private class SupabaseHttpException(
    val statusCode: Int,
    val mapBadRequestToAuth: Boolean,
) : Exception()

private fun <T> Result<T>.mapFailure(): Result<T> =
    exceptionOrNull()?.let { error -> Result.failure(error.toSyncException()) } ?: this

private fun Throwable.toSyncException(): Throwable =
    when (this) {
        is SyncException -> this
        is IOException -> SyncException(SyncError.Network)
        is SupabaseHttpException ->
            SyncException(
                when (statusCode) {
                    401, 403 -> SyncError.Auth
                    400 -> if (mapBadRequestToAuth) SyncError.Auth else SyncError.Unknown
                    409 -> SyncError.Conflict
                    429 -> SyncError.Quota
                    in 500..599 -> SyncError.Server
                    else -> SyncError.Unknown
                },
            )
        else -> SyncException(SyncError.Unknown)
    }

private fun SupabaseConfig.urlFor(path: String): String =
    "${url.trimEnd('/')}/${path.trimStart('/')}"
