package com.kshavrin.mymoney.core.sync.fake

import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.sync.JournalBackend
import com.kshavrin.mymoney.core.sync.RemoteJournalFile

/**
 * In-memory [JournalBackend] for journal-transport and orchestrator tests.
 *
 * Models a single shared folder as folderId -> (deviceId -> bytes). uploadJournal
 * is upsert-by-deviceId (Drive re-uploads the whole per-device file, no second
 * copy). listPeerJournals returns every device's file except [ownDeviceId],
 * mirroring the engine's "neighbours only" contract. The live Google path is a
 * deferred OQ; this fake is the unit-test seam (D2).
 */
class FakeJournalBackend(
    private val ownDeviceId: String? = null,
) : JournalBackend {
    private val store = mutableMapOf<String, MutableMap<String, ByteArray>>()
    private val fileIndex = mutableMapOf<String, ByteArray>()
    private val modifiedAt = mutableMapOf<String, Long>()
    private var clock = 1L

    val uploadCalls: MutableList<Upload> = mutableListOf()

    private var uploadError: SyncError? = null
    private var listError: SyncError? = null
    private var downloadError: SyncError? = null

    data class Upload(
        val folderId: String,
        val deviceId: String,
        val bytes: ByteArray,
    )

    fun simulateUploadFailure(error: SyncError) {
        uploadError = error
    }

    fun simulateListFailure(error: SyncError) {
        listError = error
    }

    fun simulateDownloadFailure(error: SyncError) {
        downloadError = error
    }

    private fun fileId(
        folderId: String,
        deviceId: String,
    ): String = "$folderId/$deviceId"

    override suspend fun uploadJournal(
        folderId: String,
        deviceId: String,
        bytes: ByteArray,
    ): Result<Unit> {
        uploadError?.let { return Result.failure(SyncException(it)) }
        uploadCalls += Upload(folderId, deviceId, bytes)
        store.getOrPut(folderId) { mutableMapOf() }[deviceId] = bytes
        val id = fileId(folderId, deviceId)
        fileIndex[id] = bytes
        modifiedAt[id] = clock++
        return Result.success(Unit)
    }

    override suspend fun listPeerJournals(folderId: String): Result<List<RemoteJournalFile>> {
        listError?.let { return Result.failure(SyncException(it)) }
        val files =
            store[folderId]
                .orEmpty()
                .filterKeys { it != ownDeviceId }
                .map { (deviceId, _) ->
                    val id = fileId(folderId, deviceId)
                    RemoteJournalFile(
                        fileId = id,
                        deviceId = deviceId,
                        modifiedAtEpochMs = modifiedAt[id] ?: 0L,
                    )
                }.sortedByDescending { it.modifiedAtEpochMs }
        return Result.success(files)
    }

    override suspend fun downloadJournal(fileId: String): Result<ByteArray> {
        downloadError?.let { return Result.failure(SyncException(it)) }
        val bytes = fileIndex[fileId] ?: return Result.failure(SyncException(SyncError.Unknown))
        return Result.success(bytes)
    }
}
