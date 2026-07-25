package com.kshavrin.mymoney.core.sync.fake

import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.sync.JournalBackend
import com.kshavrin.mymoney.core.sync.RemoteJournalFile
import com.kshavrin.mymoney.core.sync.SyncTarget

class FakeJournalBackend(
    private val ownDeviceId: String? = null,
    override val target: SyncTarget = SyncTarget.Dropbox,
) : JournalBackend {
    data class Upload(val deviceId: String, val bytes: ByteArray)

    private val files = linkedMapOf<String, Pair<ByteArray, Long>>()
    private var version = 1L
    var uploadError: SyncError? = null
    var listError: SyncError? = null
    var downloadError: SyncError? = null
    val uploadCalls = mutableListOf<Upload>()
    val listCalls = mutableListOf<Unit>()

    override suspend fun uploadJournal(deviceId: String, bytes: ByteArray): Result<Unit> {
        uploadError?.let { return Result.failure(SyncException(it)) }
        uploadCalls += Upload(deviceId, bytes)
        files[deviceId] = bytes to version++
        return Result.success(Unit)
    }

    override suspend fun listPeerJournals(): Result<List<RemoteJournalFile>> {
        listError?.let { return Result.failure(SyncException(it)) }
        listCalls += Unit
        return Result.success(
            files.filterKeys { it != ownDeviceId }.map { (device, pair) ->
                RemoteJournalFile("file-$device", device, pair.second)
            },
        )
    }

    override suspend fun downloadJournal(fileId: String): Result<ByteArray> {
        downloadError?.let { return Result.failure(SyncException(it)) }
        val device = fileId.removePrefix("file-")
        return files[device]?.first?.let(Result.Companion::success)
            ?: Result.failure(SyncException(SyncError.Unknown))
    }
}
