package com.kshavrin.mymoney.core.sync.gdrive

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpResponseException
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.datastore.SecureStorage
import com.kshavrin.mymoney.core.sync.CloudSyncBackend
import com.kshavrin.mymoney.core.sync.SyncTarget
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveRepository
    @Inject
    constructor(
        private val secureStorage: SecureStorage,
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
        t is GdriveHttpException ->
            when {
                t.statusCode == 401 -> SyncError.Auth
                t.statusCode == 403 && t.reason == GDRIVE_QUOTA_REASON -> SyncError.Quota
                t.statusCode == 403 -> SyncError.Auth
                t.statusCode >= HTTP_SERVER_ERROR -> SyncError.Server
                else -> SyncError.Unknown
            }
        else -> SyncError.Unknown
    }
