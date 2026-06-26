package com.kshavrin.mymoney.core.sync.gdrive

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.HttpResponseException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.datastore.SecureStorage
import com.kshavrin.mymoney.core.sync.JournalBackend
import com.kshavrin.mymoney.core.sync.RemoteJournalFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import com.google.api.services.drive.model.File as DriveFile

@Singleton
class GoogleDriveJournalBackend
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val secureStorage: SecureStorage,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : JournalBackend {
        private fun storedEmail(): String? =
            secureStorage.read().gdriveAccountEmail?.takeIf { it.isNotBlank() }

        private fun client(): Drive {
            val email = storedEmail() ?: throw SyncException(SyncError.Auth)
            val credential =
                GoogleAccountCredential
                    .usingOAuth2(context, listOf(DriveScopes.DRIVE_FILE))
                    .setSelectedAccountName(email)
            return Drive
                .Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
                .setApplicationName(APPLICATION_NAME)
                .build()
        }

        override suspend fun uploadJournal(
            folderId: String,
            deviceId: String,
            bytes: ByteArray,
        ): Result<Unit> =
            runOnIo {
                val fileName = journalFileName(deviceId)
                val content = ByteArrayContent(JOURNAL_MIME_TYPE, bytes)
                val files = client().files()
                val existingId = findOwnFileId(files, folderId, fileName)
                if (existingId != null) {
                    files
                        .update(existingId, DriveFile(), content)
                        .setFields("id")
                        .execute()
                } else {
                    val metadata =
                        DriveFile().apply {
                            name = fileName
                            parents = listOf(folderId)
                        }
                    files
                        .create(metadata, content)
                        .setFields("id")
                        .execute()
                }
                Unit
            }

        override suspend fun listPeerJournals(folderId: String): Result<List<RemoteJournalFile>> =
            runOnIo {
                val files = client().files()
                val result = mutableListOf<RemoteJournalFile>()
                var pageToken: String? = null
                do {
                    val page =
                        files
                            .list()
                            .setQ("'$folderId' in parents and trashed = false and name contains '$JOURNAL_PREFIX'")
                            .setFields("nextPageToken, files(id, name, modifiedTime)")
                            .setPageToken(pageToken)
                            .execute()
                    page.files?.forEach { file ->
                        val deviceId = deviceIdFromName(file.name) ?: return@forEach
                        result +=
                            RemoteJournalFile(
                                fileId = file.id,
                                deviceId = deviceId,
                                modifiedAtEpochMs = file.modifiedTime.value,
                            )
                    }
                    pageToken = page.nextPageToken
                } while (pageToken != null)
                result.sortedByDescending { it.modifiedAtEpochMs }
            }

        override suspend fun downloadJournal(fileId: String): Result<ByteArray> =
            runOnIo {
                ByteArrayOutputStream().use { output ->
                    client().files().get(fileId).executeMediaAndDownloadTo(output)
                    output.toByteArray()
                }
            }

        private fun findOwnFileId(
            files: Drive.Files,
            folderId: String,
            fileName: String,
        ): String? {
            val page =
                files
                    .list()
                    .setQ("'$folderId' in parents and trashed = false and name = '$fileName'")
                    .setFields("files(id)")
                    .execute()
            return page.files?.firstOrNull()?.id
        }

        private fun deviceIdFromName(name: String?): String? =
            name
                ?.takeIf { it.startsWith(JOURNAL_PREFIX) && it.endsWith(JOURNAL_SUFFIX) }
                ?.removePrefix(JOURNAL_PREFIX)
                ?.removeSuffix(JOURNAL_SUFFIX)
                ?.takeIf { it.isNotBlank() }

        private fun journalFileName(deviceId: String): String = "$JOURNAL_PREFIX$deviceId$JOURNAL_SUFFIX"

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
            return GdriveHttpException(status, null, t)
        }

        private fun httpStatusOf(t: Throwable): Int? =
            when (t) {
                is GdriveHttpException -> t.statusCode
                is HttpResponseException -> t.statusCode
                else -> null
            }

        private companion object {
            const val APPLICATION_NAME = "MyMoney"
            const val JOURNAL_MIME_TYPE = "application/x-ndjson"
            const val JOURNAL_PREFIX = "ops-"
            const val JOURNAL_SUFFIX = ".jsonl"
            const val MAX_RETRIES = 3
            const val BASE_BACKOFF_MILLIS = 1_000L
        }
    }
