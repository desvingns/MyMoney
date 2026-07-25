package com.kshavrin.mymoney.core.sync

data class CloudAccountIdentity(
    val stableId: String,
    val label: String,
)

interface CloudSyncBackend {
    val target: SyncTarget

    fun isConnected(): Boolean

    suspend fun accountLabel(): Result<String>

    suspend fun accountIdentity(): Result<CloudAccountIdentity> =
        accountLabel().map { label -> CloudAccountIdentity(stableId = label, label = label) }

    fun connect(payload: String) = Unit

    fun disconnect()
}
