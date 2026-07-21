package com.kshavrin.mymoney.core.sync

data class RemoteJournalFile(
    val fileId: String,
    val deviceId: String,
    val modifiedAtEpochMs: Long,
)

interface JournalBackend {
    suspend fun isFolder(
        accountEmail: String,
        folderId: String,
    ): Result<Boolean> = Result.success(true)

    suspend fun uploadJournal(
        folderId: String,
        deviceId: String,
        bytes: ByteArray,
    ): Result<Unit>

    suspend fun listPeerJournals(folderId: String): Result<List<RemoteJournalFile>>

    suspend fun downloadJournal(fileId: String): Result<ByteArray>
}
