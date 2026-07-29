package com.kshavrin.mymoney.core.sync

import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.database.dao.OperationDao
import com.kshavrin.mymoney.core.database.journal.JournalApplier
import com.kshavrin.mymoney.core.database.journal.JournalBootstrap
import com.kshavrin.mymoney.core.database.journal.toDomain
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.CloudProvider
import com.kshavrin.mymoney.core.datastore.JournalSyncConfigStore
import com.kshavrin.mymoney.core.domain.sync.DeviceIdProvider
import com.kshavrin.mymoney.core.domain.sync.Operation
import com.kshavrin.mymoney.core.sync.shared.SharedSyncCoordinator
import dagger.Lazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JournalSyncImpl
    @Inject
    constructor(
        private val operationDao: OperationDao,
        private val serializer: JournalSerializer,
        private val backends: Set<@JvmSuppressWildcards JournalBackend>,
        private val applier: JournalApplier,
        private val bootstrap: JournalBootstrap,
        private val configStore: JournalSyncConfigStore,
        private val deviceIdProvider: DeviceIdProvider,
        private val appSettings: AppSettingsRepository,
        private val clock: Clock,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        private val sharedCoordinator: Lazy<SharedSyncCoordinator>?,
    ) : JournalSync {
        constructor(
            operationDao: OperationDao,
            serializer: JournalSerializer,
            backend: JournalBackend,
            applier: JournalApplier,
            bootstrap: JournalBootstrap,
            configStore: JournalSyncConfigStore,
            deviceIdProvider: DeviceIdProvider,
            appSettings: AppSettingsRepository,
            clock: Clock,
            ioDispatcher: CoroutineDispatcher,
        ) : this(
            operationDao = operationDao,
            serializer = serializer,
            backends = setOf(backend),
            applier = applier,
            bootstrap = bootstrap,
            configStore = configStore,
            deviceIdProvider = deviceIdProvider,
            appSettings = appSettings,
            clock = clock,
            ioDispatcher = ioDispatcher,
            sharedCoordinator = null,
        )

        override suspend fun syncNow() = syncMutex.withLock { syncNowLocked() }

        override suspend fun push() = syncMutex.withLock { pushLocked() }

        override suspend fun pull() = syncMutex.withLock { pullLocked() }

        override suspend fun previewMigration(target: SyncTarget): Result<JournalMigrationPreview> =
            syncMutex.withLock {
                runMigrationStep {
                    check(configStore.binding() != null) { "An active binding is required" }
                    check(configStore.isBootstrapDone()) { "Local journal bootstrap must finish before migration" }
                    withContext(ioDispatcher) {
                        val deviceId = deviceIdProvider.deviceId()
                        val backend = targetBackend(target)
                        val remoteOperations = mutableListOf<Operation>()
                        val peerJournals =
                            backend
                                .listPeerJournals()
                                .getOrThrow()
                                .filter { it.deviceId != deviceId }
                        for (peer in peerJournals) {
                            remoteOperations += serializer.decode(backend.downloadJournal(peer.fileId).getOrThrow())
                        }
                        val distinctRemoteOperations = remoteOperations.distinctBy { it.opId }
                        val localEntities = operationDao.knownEntityUuids().toSet()
                        JournalMigrationPreview(
                            target = target,
                            remoteOperations = distinctRemoteOperations,
                            conflictingEntityUuids =
                                distinctRemoteOperations
                                    .asSequence()
                                    .map { it.entityUuid }
                                    .filter(localEntities::contains)
                                    .toSet(),
                        )
                    }
                }
            }

        override suspend fun applyMigration(
            preview: JournalMigrationPreview,
            resolution: MigrationResolution,
        ): Result<Unit> =
            syncMutex.withLock {
                runMigrationStep {
                    withContext(ioDispatcher) {
                        val operations =
                            when (resolution) {
                                MigrationResolution.KeepLocal ->
                                    preview.remoteOperations.filterNot {
                                        it.entityUuid in preview.conflictingEntityUuids
                                    }
                                MigrationResolution.UseTarget -> preview.remoteOperations
                            }
                        check(!applier.apply(operations).hadSkips) {
                            "Migration could not apply every staged operation"
                        }
                    }
                }
            }

        private suspend fun syncNowLocked() {
            if (configStore.binding()?.provider == CloudProvider.Shared) {
                requireNotNull(sharedCoordinator) { "Shared sync coordinator is unavailable" }
                    .get()
                    .syncNow()
                    .getOrThrow()
                return
            }
            if (activeBackend() == null) return
            bootstrap.runIfNeeded()
            withContext(ioDispatcher) {
                val backend = activeBackend() ?: return@withContext
                pull(backend)
                push(backend)
            }
        }

        private suspend fun pushLocked() {
            withContext(ioDispatcher) {
                val backend = activeBackend() ?: return@withContext
                push(backend)
            }
        }

        private suspend fun pullLocked() {
            withContext(ioDispatcher) {
                val backend = activeBackend() ?: return@withContext
                pull(backend)
            }
        }

        private suspend fun activeBackend(): JournalBackend? =
            configStore.binding()?.provider?.toSyncTarget()?.let { target ->
                targetBackend(target)
            }

        private fun targetBackend(target: SyncTarget): JournalBackend =
            checkNotNull(backends.singleOrNull { it.target == target }) {
                "No journal backend is configured for $target"
            }

        private suspend fun push(backend: JournalBackend) {
            val deviceId = deviceIdProvider.deviceId()
            val unsynced = operationDao.unsyncedLocal()
            val localOps = operationDao.localOps()
            if (localOps.isEmpty()) return
            val bytes = serializer.encode(localOps.map { it.toDomain() })
            backend.uploadJournal(deviceId = deviceId, bytes = bytes).getOrThrow()
            if (unsynced.isNotEmpty()) {
                operationDao.markSynced(unsynced.map { op -> op.opId })
            }
            appSettings.update { it.copy(lastSyncAt = clock.millis()) }
        }

        private suspend fun pull(backend: JournalBackend) {
            val deviceId = deviceIdProvider.deviceId()
            val peers =
                backend
                    .listPeerJournals()
                    .getOrThrow()
                    .filter { it.deviceId != deviceId }
            var applied = false
            for (peer in peers) {
                if (peer.modifiedAtEpochMs <= configStore.peerHighWaterMs(peer.fileId)) continue
                val bytes = backend.downloadJournal(peer.fileId).getOrThrow()
                val result = applier.apply(serializer.decode(bytes))
                // Skipped ops carry unresolved cross-device FKs; leave the high-water so the next
                // pull retries this file once the dependency (another peer's file) is applied.
                if (!result.hadSkips) {
                    configStore.setPeerHighWaterMs(peer.fileId, peer.modifiedAtEpochMs)
                }
                applied = true
            }
            if (applied) {
                appSettings.update { it.copy(lastSyncAt = clock.millis()) }
            }
        }

        private suspend fun <T> runMigrationStep(block: suspend () -> T): Result<T> =
            try {
                Result.success(block())
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                Result.failure(t)
            }

        private companion object {
            val syncMutex = Mutex()
        }
    }
