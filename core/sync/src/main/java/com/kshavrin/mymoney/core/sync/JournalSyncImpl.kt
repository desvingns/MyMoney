package com.kshavrin.mymoney.core.sync

import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.database.dao.OperationDao
import com.kshavrin.mymoney.core.database.journal.JournalApplier
import com.kshavrin.mymoney.core.database.journal.JournalBootstrap
import com.kshavrin.mymoney.core.database.journal.toDomain
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.JournalSyncConfigStore
import com.kshavrin.mymoney.core.domain.sync.DeviceIdProvider
import kotlinx.coroutines.CoroutineDispatcher
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
        private val backend: JournalBackend,
        private val applier: JournalApplier,
        private val bootstrap: JournalBootstrap,
        private val configStore: JournalSyncConfigStore,
        private val deviceIdProvider: DeviceIdProvider,
        private val appSettings: AppSettingsRepository,
        private val clock: Clock,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : JournalSync {
        override suspend fun syncNow() {
            bootstrap.runIfNeeded()
            pull()
            push()
        }

        override suspend fun push() {
            withContext(ioDispatcher) {
                val folderId = configStore.folderId()
                if (folderId.isBlank()) return@withContext
                val deviceId = deviceIdProvider.deviceId()
                val unsynced = operationDao.unsyncedLocal()
                if (unsynced.isEmpty()) return@withContext
                val bytes = serializer.encode(operationDao.localOps().map { it.toDomain() })
                backend
                    .uploadJournal(folderId = folderId, deviceId = deviceId, bytes = bytes)
                    .getOrThrow()
                operationDao.markSynced(unsynced.map { op -> op.opId })
                appSettings.update { it.copy(lastSyncAt = clock.millis()) }
            }
        }

        override suspend fun pull() {
            withContext(ioDispatcher) {
                val folderId = configStore.folderId()
                if (folderId.isBlank()) return@withContext
                val deviceId = deviceIdProvider.deviceId()
                val peers =
                    backend
                        .listPeerJournals(folderId)
                        .getOrThrow()
                        .filter { it.deviceId != deviceId }
                var applied = false
                for (peer in peers) {
                    if (configStore.folderId().isBlank()) return@withContext
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
        }
    }
