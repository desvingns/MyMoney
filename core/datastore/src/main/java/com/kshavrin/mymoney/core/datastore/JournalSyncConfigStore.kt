package com.kshavrin.mymoney.core.datastore

/**
 * Configuration + bookkeeping for the append-only journal sync (operations-journal-sync epic).
 *
 * - [folderId] is the shared Drive folder where every device drops its `ops-<deviceId>.jsonl`
 *   journal (D10). When blank the journal sync is a no-op.
 * - [peerHighWaterMs] tracks, per peer journal file, the last `modifiedAt` we already pulled so we
 *   only re-download changed files.
 * - [bootstrapDone] is the one-shot flag guarding [com.kshavrin.mymoney] journal bootstrap (D11).
 */
interface JournalSyncConfigStore {
    suspend fun folderId(): String

    suspend fun setFolderId(folderId: String)

    suspend fun peerHighWaterMs(fileId: String): Long

    suspend fun setPeerHighWaterMs(
        fileId: String,
        modifiedAtMs: Long,
    )

    suspend fun isBootstrapDone(): Boolean

    suspend fun markBootstrapDone()
}
