package com.kshavrin.mymoney.feature.cloudsync.fake

import com.kshavrin.mymoney.core.sync.SnapshotSync
import com.kshavrin.mymoney.core.sync.SyncTarget

/**
 * Hand-written fake for [SnapshotSync]. Per-target connection / label state is
 * configured up front; the fake records connect / disconnect calls so tests can
 * assert the ViewModel drove the right seam.
 */
class FakeSnapshotSync : SnapshotSync {
    private val connected = mutableMapOf<SyncTarget, Boolean>()
    private val labels = mutableMapOf<SyncTarget, Result<String>>()

    val connectCalls = mutableListOf<Pair<SyncTarget, String>>()
    val disconnectCalls = mutableListOf<SyncTarget>()

    fun setConnected(
        target: SyncTarget,
        value: Boolean,
    ) {
        connected[target] = value
    }

    fun setAccountLabel(
        target: SyncTarget,
        result: Result<String>,
    ) {
        labels[target] = result
    }

    override fun isConnected(target: SyncTarget): Boolean = connected[target] ?: false

    override fun connectedTargets(): List<SyncTarget> =
        connected.filterValues { it }.keys.toList()

    override fun connect(
        target: SyncTarget,
        payload: String,
    ) {
        connectCalls += target to payload
        connected[target] = true
    }

    override fun disconnect(target: SyncTarget) {
        disconnectCalls += target
        connected[target] = false
    }

    override suspend fun accountLabel(target: SyncTarget): Result<String> =
        labels[target] ?: Result.success("account@example.com")
}
