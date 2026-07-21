package com.kshavrin.mymoney.core.sync

interface CloudSyncBackend {
    val target: SyncTarget

    fun isConnected(): Boolean

    suspend fun accountLabel(): Result<String>

    fun connect(payload: String) = Unit

    fun disconnect()
}
