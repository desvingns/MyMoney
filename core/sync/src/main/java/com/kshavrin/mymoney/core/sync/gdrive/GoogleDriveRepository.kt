package com.kshavrin.mymoney.core.sync.gdrive

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.FileContent
import com.google.api.client.http.HttpResponseException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.datastore.SecureStorage
import com.kshavrin.mymoney.core.sync.CloudSyncBackend
import com.kshavrin.mymoney.core.sync.RemoteSnapshot
import com.kshavrin.mymoney.core.sync.SyncTarget
import com.kshavrin.mymoney.core.sync.dropbox.snapshotsToDelete
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import com.google.api.services.drive.model.File as DriveFile

@Singleton
class GoogleDriveRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val secureStorage: SecureStorage,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : CloudSyncBackend {
        override val target: SyncTarget = SyncTarget.GoogleDrive

        private fun storedEmail(): String? =
            secureStorage.read().gdriveAccountEmail?.takeIf { it.isNotBlank() }

        private fun client(): Drive {
            val email = storedEmail() ?: throw SyncException(SyncError.Auth)
            val credential =
                GoogleAccountCredential
                    .usingOAuth2(context, listOf(DriveScopes.DRIVE_APPDATA))
                    .setSelectedAccountName(email)
            return Drive
                .Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
                .setApplicationName(APPLICATION_NAME)
                .build()
        }

        fun persistAccountEmail(email: String) {
            secureStorage.writeGdriveAccountEmail(email)
        }

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

        override suspend fun upload(
            localFile: File,
            remoteName: String,
        ): Result<Unit> =
            runOnIo {
                val metadata =
                    DriveFile().apply {
                        name = remoteName
                        parents = listOf(APP_DATA_FOLDER)
                    }
                val content = FileContent(SNAPSHOT_MIME_TYPE, localFile)
                client()
                    .files()
                    .create(metadata, content)
                    .setFields("id, modifiedTime")
                    .execute()
                Unit
            }

        override suspend fun listSnapshots(): Result<List<RemoteSnapshot>> =
            runOnIo {
                val files = client().files()
                val snapshots = mutableListOf<RemoteSnapshot>()
                var pageToken: String? = null
                do {
                    val page =
                        files
                            .list()
                            .setSpaces(APP_DATA_FOLDER)
                            .setFields("nextPageToken, files(id, name, modifiedTime)")
                            .setPageToken(pageToken)
                            .execute()
                    page.files?.forEach {
                        snapshots +=
                            RemoteSnapshot(
                                id = it.id,
                                name = it.name,
                                modifiedAtEpochMs = it.modifiedTime.value,
                            )
                    }
                    pageToken = page.nextPageToken
                } while (pageToken != null)
                snapshots.sortedByDescending { it.modifiedAtEpochMs }
            }

        override suspend fun downloadNewest(destFile: File): Result<RemoteSnapshot?> {
            val snapshots = listSnapshots().getOrElse { return Result.failure(it) }
            val newest = snapshots.firstOrNull() ?: return Result.success(null)
            return runOnIo {
                try {
                    destFile.outputStream().use { output ->
                        client().files().get(newest.id).executeMediaAndDownloadTo(output)
                    }
                    newest
                } catch (t: Throwable) {
                    if (httpStatusOf(t) == HTTP_NOT_FOUND) return@runOnIo null
                    throw t
                }
            }
        }

        override suspend fun prune(keep: Int): Result<Unit> {
            val snapshots = listSnapshots().getOrElse { return Result.failure(it) }
            val toDelete = snapshotsToDelete(snapshots, keep)
            if (toDelete.isEmpty()) return Result.success(Unit)
            return runOnIo {
                val files = client().files()
                toDelete.forEach { files.delete(it.id).execute() }
                Unit
            }
        }

        private suspend fun <T> runOnIo(block: () -> T): Result<T> =
            withContext(ioDispatcher) {
                var lastError: Throwable
                var attempt = 0
                do {
                    try {
                        return@withContext Result.success(block())
                    } catch (t: Throwable) {
                        lastError = t
                        val backoff = transientBackoffMillis(t, attempt) ?: break
                        delay(backoff)
                        attempt++
                    }
                } while (true)
                Result.failure(SyncException(mapGdriveError(toMappable(lastError))))
            }

        private fun transientBackoffMillis(
            t: Throwable,
            attempt: Int,
        ): Long? {
            if (attempt >= MAX_RETRIES - 1) return null
            val status = httpStatusOf(t)
            val transient = t is IOException || (status != null && status >= HTTP_SERVER_ERROR)
            return if (transient) retryDelayMillis(attempt) else null
        }

        private fun retryDelayMillis(attempt: Int): Long = BASE_BACKOFF_MILLIS shl attempt

        private fun toMappable(t: Throwable): Throwable {
            val status = httpStatusOf(t) ?: return t
            return GdriveHttpException(status, gdriveReasonOf(t), t)
        }

        private companion object {
            const val APPLICATION_NAME = "MyMoney"
            const val APP_DATA_FOLDER = "appDataFolder"
            const val SNAPSHOT_MIME_TYPE = "application/x-sqlite3"
            const val MAX_RETRIES = 3
            const val BASE_BACKOFF_MILLIS = 1_000L
        }
    }

internal const val HTTP_NOT_FOUND = 404
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
