package com.kshavrin.mymoney.core.sync.fake

import com.kshavrin.mymoney.core.sync.CloudSyncBackend
import com.kshavrin.mymoney.core.sync.SyncTarget

/**
 * In-memory [CloudSyncBackend] for SnapshotSyncRepository orchestration tests.
 *
 * Records connect/disconnect calls; accountLabel can be flipped to a failure via
 * simulateAccountLabelFailure so the bookkeeping failure path is exercisable
 * without any real SDK.
 */
class FakeCloudSyncBackend(
    override val target: SyncTarget,
    private var connected: Boolean = true,
) : CloudSyncBackend {
    val connectCalls: MutableList<String> = mutableListOf()
    var disconnectCalls: Int = 0

    private var accountLabelError: Throwable? = null

    fun setConnected(value: Boolean) {
        connected = value
    }

    fun simulateAccountLabelFailure(error: Throwable) {
        accountLabelError = error
    }

    override fun isConnected(): Boolean = connected

    override suspend fun accountLabel(): Result<String> =
        accountLabelError?.let { Result.failure(it) } ?: Result.success(target.name)

    override fun connect(payload: String) {
        connectCalls += payload
        connected = true
    }

    override fun disconnect() {
        disconnectCalls++
        connected = false
    }
}
