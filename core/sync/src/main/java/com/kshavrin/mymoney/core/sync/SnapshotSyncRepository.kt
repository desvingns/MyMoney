package com.kshavrin.mymoney.core.sync

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SnapshotSyncRepository
    @Inject
    constructor(
        private val backends: Set<@JvmSuppressWildcards CloudSyncBackend>,
    ) : SnapshotSync {
        private fun backend(target: SyncTarget): CloudSyncBackend =
            backends.first { it.target == target }

        override fun isConnected(target: SyncTarget): Boolean = backend(target).isConnected()

        override fun connectedTargets(): List<SyncTarget> =
            backends.filter { it.isConnected() }.map { it.target }

        override fun connect(
            target: SyncTarget,
            payload: String,
        ) = backend(target).connect(payload)

        override fun disconnect(target: SyncTarget) = backend(target).disconnect()

        override suspend fun accountLabel(target: SyncTarget): Result<String> =
            backend(target).accountLabel()

        override suspend fun accountIdentity(target: SyncTarget): Result<CloudAccountIdentity> =
            backend(target).accountIdentity()
    }
