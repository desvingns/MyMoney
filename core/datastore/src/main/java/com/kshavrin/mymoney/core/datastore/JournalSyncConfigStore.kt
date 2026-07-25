package com.kshavrin.mymoney.core.datastore

interface JournalSyncConfigStore {
    suspend fun binding(): CloudBinding? = null

    suspend fun setBinding(binding: CloudBinding) = Unit

    suspend fun clearBinding() = Unit

    suspend fun peerHighWaterMs(fileId: String): Long

    suspend fun setPeerHighWaterMs(
        fileId: String,
        modifiedAtMs: Long,
    )

    suspend fun isBootstrapDone(): Boolean

    suspend fun markBootstrapDone()

    suspend fun clear()
}
