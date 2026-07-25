package com.kshavrin.mymoney.core.sync.gdrive

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.HttpResponseException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.datastore.SecureStorage
import com.kshavrin.mymoney.core.sync.CloudAccountIdentity
import com.kshavrin.mymoney.core.sync.CloudSyncBackend
import com.kshavrin.mymoney.core.sync.SyncTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveRepository
    @Inject
    constructor(
        private val secureStorage: SecureStorage,
        private val authorizer: GoogleDriveAuthorizer,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : CloudSyncBackend {
        override val target: SyncTarget = SyncTarget.GoogleDrive

        private fun storedEmail(): String? =
            secureStorage.read().gdriveAccountEmail?.takeIf { it.isNotBlank() }

        override fun connect(payload: String) {
            secureStorage.writeGdriveAccountEmail(payload)
        }

        override fun isConnected(): Boolean = storedEmail() != null

        override fun disconnect() {
            secureStorage.writeGdriveAccountEmail(null)
        }

        override suspend fun accountLabel(): Result<String> =
            runCatching {
                storedEmail() ?: throw SyncException(SyncError.Auth)
            }

        override suspend fun accountIdentity(): Result<CloudAccountIdentity> =
            try {
                val email = storedEmail() ?: throw SyncException(SyncError.Auth)
                val token = authorizer.accessToken(email).getOrThrow()
                withContext(ioDispatcher) {
                    val user =
                        Drive
                            .Builder(
                                NetHttpTransport(),
                                GsonFactory.getDefaultInstance(),
                                HttpRequestInitializer { request -> request.headers.authorization = "Bearer $token" },
                            ).setApplicationName(APPLICATION_NAME)
                            .build()
                            .about()
                            .get()
                            .setFields("user(permissionId,emailAddress)")
                            .execute()
                            .user
                    CloudAccountIdentity(
                        stableId = user.permissionId?.takeIf { it.isNotBlank() } ?: throw SyncException(SyncError.Auth),
                        label = user.emailAddress?.takeIf { it.isNotBlank() } ?: email,
                    )
                }.let(Result.Companion::success)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                Result.failure(SyncException(mapGdriveError(t)))
            }

        private companion object {
            const val APPLICATION_NAME = "MyMoney"
        }
    }

internal const val HTTP_SERVER_ERROR = 500
internal const val GDRIVE_QUOTA_REASON = "storageQuotaExceeded"

internal class GdriveHttpException(
    val statusCode: Int,
    val reason: String?,
    cause: Throwable,
) : Exception(cause)

private fun httpStatusOf(t: Throwable): Int? =
    when (t) {
        is GdriveHttpException -> t.statusCode
        is HttpResponseException -> t.statusCode
        else -> null
    }

private fun gdriveReasonOf(t: Throwable): String? =
    when (t) {
        is GdriveHttpException -> t.reason
        is GoogleJsonResponseException ->
            t.details
                ?.errors
                ?.firstOrNull()
                ?.reason
        else -> null
    }

internal fun mapGdriveError(t: Throwable): SyncError =
    when {
        t is SyncException -> t.syncError
        t is IOException -> SyncError.Network
        httpStatusOf(t) != null ->
            when {
                httpStatusOf(t) == 401 -> SyncError.Auth
                httpStatusOf(t) == 403 && gdriveReasonOf(t) == GDRIVE_QUOTA_REASON -> SyncError.Quota
                httpStatusOf(t) == 403 -> SyncError.Auth
                httpStatusOf(t)!! >= HTTP_SERVER_ERROR -> SyncError.Server
                else -> SyncError.Unknown
            }
        else -> SyncError.Unknown
    }
