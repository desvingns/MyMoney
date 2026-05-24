package com.kshavrin.mymoney.core.sync.dropbox

import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.NetworkIOException
import com.dropbox.core.RetryException
import com.dropbox.core.ServerException
import com.dropbox.core.oauth.DbxCredential
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.UploadErrorException
import com.dropbox.core.v2.files.WriteMode
import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.datastore.SecureStorage
import com.kshavrin.mymoney.core.sync.CloudSyncBackend
import com.kshavrin.mymoney.core.sync.RemoteSnapshot
import com.kshavrin.mymoney.core.sync.SyncTarget
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DropboxRepository @Inject constructor(
    private val secureStorage: SecureStorage,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CloudSyncBackend {

    override val target: SyncTarget = SyncTarget.Dropbox

    // The serialized DbxCredential (refresh-token flow) is persisted in the Dropbox
    // refresh-token slot of SecureStorage; a blank slot means "not connected".
    private fun storedCredential(): String? =
        secureStorage.read().dropboxRefreshToken?.takeIf { it.isNotBlank() }

    private fun client(): DbxClientV2 {
        val serialized = storedCredential() ?: throw SyncException(SyncError.Auth)
        val credential = DbxCredential.Reader.readFully(serialized)
        val config = DbxRequestConfig.newBuilder(CLIENT_IDENTIFIER).build()
        return DbxClientV2(config, credential)
    }

    fun persistCredential(serialized: String) {
        secureStorage.writeDropboxRefreshToken(serialized)
    }

    override fun connect(payload: String) {
        secureStorage.writeDropboxRefreshToken(payload)
    }

    override fun isConnected(): Boolean = storedCredential() != null

    override fun disconnect() {
        secureStorage.writeDropboxRefreshToken(null)
    }

    override suspend fun accountLabel(): Result<String> = runOnIo {
        client().users().currentAccount.name.displayName
    }

    override suspend fun upload(localFile: File, remoteName: String): Result<Unit> = runOnIo {
        try {
            localFile.inputStream().use { input ->
                client().files()
                    .uploadBuilder(STORAGE_PATH + remoteName)
                    .withMode(WriteMode.OVERWRITE)
                    .uploadAndFinish(input)
            }
            Unit
        } catch (e: UploadErrorException) {
            if (e.isInsufficientSpace()) throw DropboxQuotaException(e) else throw e
        }
    }

    override suspend fun listSnapshots(): Result<List<RemoteSnapshot>> = runOnIo {
        val files = client().files()
        val entries = mutableListOf<FileMetadata>()
        var result = files.listFolder(STORAGE_PATH)
        while (true) {
            result.entries.filterIsInstanceTo(entries, FileMetadata::class.java)
            if (!result.hasMore) break
            result = files.listFolderContinue(result.cursor)
        }
        entries
            .map {
                RemoteSnapshot(
                    id = it.pathLower ?: (STORAGE_PATH + it.name),
                    name = it.name,
                    modifiedAtEpochMs = it.serverModified.time,
                )
            }
            .sortedByDescending { it.modifiedAtEpochMs }
    }

    override suspend fun downloadNewest(destFile: File): Result<RemoteSnapshot?> {
        val snapshots = listSnapshots().getOrElse { return Result.failure(it) }
        val newest = snapshots.firstOrNull() ?: return Result.success(null)
        return runOnIo {
            destFile.outputStream().use { output ->
                client().files().download(newest.id).download(output)
            }
            newest
        }
    }

    override suspend fun prune(keep: Int): Result<Unit> {
        val snapshots = listSnapshots().getOrElse { return Result.failure(it) }
        val toDelete = snapshotsToDelete(snapshots, keep)
        if (toDelete.isEmpty()) return Result.success(Unit)
        return runOnIo {
            val files = client().files()
            toDelete.forEach { files.deleteV2(it.id) }
            Unit
        }
    }

    private suspend fun <T> runOnIo(block: () -> T): Result<T> = withContext(ioDispatcher) {
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
        Result.failure(SyncException(mapDropboxError(lastError)))
    }

    private fun transientBackoffMillis(t: Throwable, attempt: Int): Long? {
        if (attempt >= MAX_RETRIES - 1) return null
        return when (t) {
            is RetryException -> t.backoffMillis.coerceAtLeast(retryDelayMillis(attempt))
            is NetworkIOException, is IOException -> retryDelayMillis(attempt)
            is ServerException -> retryDelayMillis(attempt)
            else -> null
        }
    }

    private fun retryDelayMillis(attempt: Int): Long = BASE_BACKOFF_MILLIS shl attempt

    private companion object {
        const val CLIENT_IDENTIFIER = "MyMoney/1.0"
        const val STORAGE_PATH = "/Apps/MyMoney/"
        const val MAX_RETRIES = 3
        const val BASE_BACKOFF_MILLIS = 1_000L
    }
}

private fun UploadErrorException.isInsufficientSpace(): Boolean =
    errorValue?.takeIf { it.isPath }?.pathValue?.reason?.isInsufficientSpace == true

internal class DropboxQuotaException(cause: Throwable) : Exception(cause)

internal fun mapDropboxError(t: Throwable): SyncError = when {
    t is SyncException -> t.syncError
    t is IOException -> SyncError.Network
    else -> when (t::class.simpleName) {
        "InvalidAccessTokenException" -> SyncError.Auth
        "DropboxQuotaException", "SpaceError" -> SyncError.Quota
        "NetworkIOException" -> SyncError.Network
        "RateLimitException", "RetryException", "ServerException", "DbxException" ->
            SyncError.Server
        else -> SyncError.Unknown
    }
}

internal fun snapshotsToDelete(all: List<RemoteSnapshot>, keep: Int): List<RemoteSnapshot> {
    if (keep < 0) return all
    return all.sortedByDescending { it.modifiedAtEpochMs }.drop(keep)
}

internal fun snapshotFileName(now: Instant): String =
    "monefy_backup_" + SNAPSHOT_FORMATTER.format(now) + ".db"

private val SNAPSHOT_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMddHHmm").withZone(ZoneOffset.UTC)
