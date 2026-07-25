package com.kshavrin.mymoney.core.sync

data class RemoteJournalFile(
    val fileId: String,
    val deviceId: String,
    val modifiedAtEpochMs: Long,
)

interface JournalBackend {
    val target: SyncTarget

    suspend fun uploadJournal(
        deviceId: String,
        bytes: ByteArray,
    ): Result<Unit>

    suspend fun listPeerJournals(): Result<List<RemoteJournalFile>>

    suspend fun downloadJournal(fileId: String): Result<ByteArray>
}
