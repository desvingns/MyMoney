package com.kshavrin.mymoney.core.sync.dropbox

import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.NetworkIOException
import com.dropbox.core.RetryException
import com.dropbox.core.ServerException
import com.dropbox.core.oauth.DbxCredential
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.WriteMode
import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.datastore.SecureStorage
import com.kshavrin.mymoney.core.sync.JournalBackend
import com.kshavrin.mymoney.core.sync.RemoteJournalFile
import com.kshavrin.mymoney.core.sync.SyncTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DropboxJournalBackend
    @Inject
    constructor(
        private val secureStorage: SecureStorage,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : JournalBackend {
        override val target: SyncTarget = SyncTarget.Dropbox

        private fun storedCredential(): String? =
            secureStorage.read().dropboxRefreshToken?.takeIf { it.isNotBlank() }

        private fun client(): DbxClientV2 {
            val serialized = storedCredential() ?: throw SyncException(SyncError.Auth)
            val credential = DbxCredential.Reader.readFully(serialized)
            val config = DbxRequestConfig.newBuilder(CLIENT_IDENTIFIER).build()
            return DbxClientV2(config, credential)
        }

        override suspend fun uploadJournal(
            deviceId: String,
            bytes: ByteArray,
        ): Result<Unit> =
            runOnIo {
                ByteArrayInputStream(bytes).use { input ->
                    client()
                        .files()
                        .uploadBuilder("/$JOURNAL_PREFIX$deviceId$JOURNAL_SUFFIX")
                        .withMode(WriteMode.OVERWRITE)
                        .uploadAndFinish(input)
                }
                Unit
            }

        override suspend fun listPeerJournals(): Result<List<RemoteJournalFile>> =
            runOnIo {
                val files = mutableListOf<RemoteJournalFile>()
                val requests = client().files()
                var page = requests.listFolder(APP_FOLDER_ROOT)
                while (true) {
                    page.entries.forEach { entry ->
                        val file = entry as? FileMetadata ?: return@forEach
                        val deviceId = deviceIdFromName(file.name) ?: return@forEach
                        files +=
                            RemoteJournalFile(
                                fileId = "$FILE_ID_PREFIX${file.id}",
                                deviceId = deviceId,
                                modifiedAtEpochMs = file.serverModified.time,
                            )
                    }
                    if (!page.hasMore) break
                    page = requests.listFolderContinue(page.cursor)
                }
                files.sortedByDescending { it.modifiedAtEpochMs }
            }

        override suspend fun downloadJournal(fileId: String): Result<ByteArray> =
            runOnIo {
                val remoteFileId = fileId.removePrefix(FILE_ID_PREFIX)
                ByteArrayOutputStream().use { output ->
                    client().files().download(remoteFileId).download(output)
                    output.toByteArray()
                }
            }

        private fun deviceIdFromName(name: String): String? =
            name
                .takeIf { it.startsWith(JOURNAL_PREFIX) && it.endsWith(JOURNAL_SUFFIX) }
                ?.removePrefix(JOURNAL_PREFIX)
                ?.removeSuffix(JOURNAL_SUFFIX)
                ?.takeIf { it.isNotBlank() }

        private suspend fun <T> runOnIo(block: () -> T): Result<T> =
            withContext(ioDispatcher) {
                var lastError: Throwable
                var attempt = 0
                do {
                    try {
                        return@withContext Result.success(block())
                    } catch (t: CancellationException) {
                        throw t
                    } catch (t: Throwable) {
                        lastError = t
                        val backoff = transientBackoffMillis(t, attempt) ?: break
                        delay(backoff)
                        attempt++
                    }
                } while (true)
                Result.failure(SyncException(mapDropboxError(lastError)))
            }

        private fun transientBackoffMillis(
            t: Throwable,
            attempt: Int,
        ): Long? {
            if (attempt >= MAX_RETRIES - 1) return null
            return when (t) {
                is RetryException -> t.backoffMillis.coerceAtLeast(retryDelayMillis(attempt))
                is NetworkIOException, is IOException, is ServerException -> retryDelayMillis(attempt)
                else -> null
            }
        }

        private fun retryDelayMillis(attempt: Int): Long = BASE_BACKOFF_MILLIS shl attempt

        private companion object {
            const val APP_FOLDER_ROOT = ""
            const val CLIENT_IDENTIFIER = "MyMoney/1.0"
            const val FILE_ID_PREFIX = "dropbox:"
            const val JOURNAL_PREFIX = "ops-"
            const val JOURNAL_SUFFIX = ".jsonl"
            const val MAX_RETRIES = 3
            const val BASE_BACKOFF_MILLIS = 1_000L
        }
    }
