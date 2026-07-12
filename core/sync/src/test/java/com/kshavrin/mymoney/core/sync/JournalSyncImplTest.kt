package com.kshavrin.mymoney.core.sync

import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.database.dao.AccountDao
import com.kshavrin.mymoney.core.database.dao.CategoryDao
import com.kshavrin.mymoney.core.database.dao.OperationDao
import com.kshavrin.mymoney.core.database.dao.TransactionDao
import com.kshavrin.mymoney.core.database.entity.AccountEntity
import com.kshavrin.mymoney.core.database.entity.CategoryEntity
import com.kshavrin.mymoney.core.database.entity.OperationEntity
import com.kshavrin.mymoney.core.database.entity.TransactionEntity
import com.kshavrin.mymoney.core.database.journal.JournalApplier
import com.kshavrin.mymoney.core.database.journal.JournalBootstrap
import com.kshavrin.mymoney.core.database.journal.OperationPayloadCodec
import com.kshavrin.mymoney.core.database.projection.CategoryGroupRow
import com.kshavrin.mymoney.core.database.projection.CategorySummaryRow
import com.kshavrin.mymoney.core.database.projection.TransactionDedupRow
import com.kshavrin.mymoney.core.database.projection.TransferRow
import com.kshavrin.mymoney.core.datastore.JournalSyncConfigStore
import com.kshavrin.mymoney.core.domain.sync.DeviceIdProvider
import com.kshavrin.mymoney.core.domain.sync.EntityKind
import com.kshavrin.mymoney.core.domain.sync.OpType
import com.kshavrin.mymoney.core.domain.sync.Operation
import com.kshavrin.mymoney.core.domain.transaction.TransactionRunner
import com.kshavrin.mymoney.core.testing.fake.FakeAppSettingsRepository
import com.kshavrin.mymoney.core.sync.fake.FakeJournalBackend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class JournalSyncImplTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val clock: Clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC)
    private val deviceId = "device-local"

    private lateinit var backend: FakeJournalBackend
    private lateinit var operationDao: FakeOperationDao
    private lateinit var configStore: FakeJournalSyncConfigStore
    private lateinit var appSettings: FakeAppSettingsRepository
    private lateinit var applierOperationDao: ApplierRecordingOperationDao
    private lateinit var bootstrapOperationDao: BootstrapRecordingOperationDao
    private lateinit var journalSync: JournalSyncImpl

    @Before
    fun setUp() {
        backend = FakeJournalBackend(ownDeviceId = deviceId)
        operationDao = FakeOperationDao()
        configStore = FakeJournalSyncConfigStore()
        appSettings = FakeAppSettingsRepository()
        applierOperationDao = ApplierRecordingOperationDao()
        bootstrapOperationDao = BootstrapRecordingOperationDao()

        val codec = OperationPayloadCodec()
        val noOpRunner =
            object : TransactionRunner {
                override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
            }
        val noOpAccountDao = NoOpAccountDao()
        val noOpCategoryDao = NoOpCategoryDao()
        val noOpTransactionDao = NoOpTransactionDao()

        val applier =
            JournalApplier(
                transactionDao = noOpTransactionDao,
                categoryDao = noOpCategoryDao,
                accountDao = noOpAccountDao,
                operationDao = applierOperationDao,
                payloadCodec = codec,
                transactionRunner = noOpRunner,
            )

        val bootstrap =
            JournalBootstrap(
                accountDao = noOpAccountDao,
                categoryDao = noOpCategoryDao,
                transactionDao = noOpTransactionDao,
                operationDao = bootstrapOperationDao,
                payloadCodec = codec,
                deviceIdProvider = FakeDeviceIdProvider(deviceId),
                transactionRunner = noOpRunner,
                configStore = configStore,
            )

        journalSync =
            JournalSyncImpl(
                operationDao = operationDao,
                serializer = JournalSerializer(),
                backend = backend,
                applier = applier,
                bootstrap = bootstrap,
                configStore = configStore,
                deviceIdProvider = FakeDeviceIdProvider(deviceId),
                appSettings = appSettings,
                clock = clock,
                ioDispatcher = dispatcher,
            )
    }

    // ─── push ─────────────────────────────────────────────────────────────────

    @Test
    fun `push uploads unsynced local ops and marks them synced on success`() =
        runTest {
            configStore.setFolderId("folder-1")
            operationDao.seed(listOf(localOp(opId = "op-1", entityUuid = "e-1", updatedAt = 1_000L)))

            journalSync.push()

            assertEquals("one upload call must occur for unsynced ops", 1, backend.uploadCalls.size)
            assertEquals("folder-1", backend.uploadCalls.first().folderId)
            assertEquals(deviceId, backend.uploadCalls.first().deviceId)
            assertTrue("op-1 must be marked synced after upload", operationDao.syncedIds.contains("op-1"))
        }

    @Test
    fun `push updates lastSyncAt in app settings on success`() =
        runTest {
            configStore.setFolderId("folder-1")
            operationDao.seed(listOf(localOp(opId = "op-1")))

            journalSync.push()

            assertEquals(clock.millis(), appSettings.current().lastSyncAt)
        }

    @Test
    fun `push is a no-op when folderId is blank`() =
        runTest {
            configStore.setFolderId("")
            operationDao.seed(listOf(localOp(opId = "op-1")))

            journalSync.push()

            assertTrue("no upload must happen when folderId is blank", backend.uploadCalls.isEmpty())
            assertTrue("markSynced must not be called when push is skipped", operationDao.syncedIds.isEmpty())
        }

    @Test
    fun `push is a no-op when there are no unsynced local ops`() =
        runTest {
            configStore.setFolderId("folder-1")
            operationDao.seed(emptyList())

            journalSync.push()

            assertTrue("no upload when nothing to push", backend.uploadCalls.isEmpty())
        }

    @Test
    fun `push does not mark ops synced when upload fails`() =
        runTest {
            configStore.setFolderId("folder-1")
            operationDao.seed(listOf(localOp(opId = "op-fail")))
            backend.simulateUploadFailure(SyncError.Network)

            journalSync.push()

            assertTrue("op must remain unsynced after upload failure", operationDao.syncedIds.isEmpty())
        }

    @Test
    fun `push uploads all unsynced ops in a single batch call`() =
        runTest {
            configStore.setFolderId("folder-1")
            operationDao.seed(
                listOf(
                    localOp(opId = "op-a", updatedAt = 1_000L),
                    localOp(opId = "op-b", updatedAt = 2_000L),
                    localOp(opId = "op-c", updatedAt = 3_000L),
                ),
            )

            journalSync.push()

            assertEquals("one upload call for all unsynced ops", 1, backend.uploadCalls.size)
            assertTrue(operationDao.syncedIds.containsAll(listOf("op-a", "op-b", "op-c")))
        }

    // ─── pull ─────────────────────────────────────────────────────────────────

    @Test
    fun `pull downloads and records a peer journal when peer file is newer than high water`() =
        runTest {
            configStore.setFolderId("folder-1")
            val peerDeviceId = "device-peer"
            val peerBytes = buildPeerJournal(peerDeviceId = peerDeviceId, opId = "peer-op-1")
            backend.uploadJournal(folderId = "folder-1", deviceId = peerDeviceId, bytes = peerBytes)

            journalSync.pull()

            val peerFiles = backend.listPeerJournals("folder-1").getOrNull()!!
            val peerFile = peerFiles.first { it.deviceId == peerDeviceId }
            val hw = configStore.peerHighWaterMs(peerFile.fileId)
            assertTrue("high water must advance to peer file modifiedAt after pull", hw >= peerFile.modifiedAtEpochMs)
        }

    @Test
    fun `pull advances the high water mark after processing a peer journal`() =
        runTest {
            configStore.setFolderId("folder-1")
            val peerDeviceId = "device-peer-2"
            val peerBytes = buildPeerJournal(peerDeviceId = peerDeviceId, opId = "peer-op-2")
            backend.uploadJournal(folderId = "folder-1", deviceId = peerDeviceId, bytes = peerBytes)

            journalSync.pull()

            val peerFiles = backend.listPeerJournals("folder-1").getOrNull()!!
            val peerFile = peerFiles.first { it.deviceId == peerDeviceId }
            val hw = configStore.peerHighWaterMs(peerFile.fileId)
            assertTrue("high water must be ≥ peer file modifiedAt after applying", hw >= peerFile.modifiedAtEpochMs)
        }

    @Test
    fun `pull skips peer file when its modifiedAt does not exceed the stored high water`() =
        runTest {
            configStore.setFolderId("folder-1")
            val peerDeviceId = "device-peer-3"
            val peerBytes = buildPeerJournal(peerDeviceId = peerDeviceId, opId = "peer-op-3")
            backend.uploadJournal(folderId = "folder-1", deviceId = peerDeviceId, bytes = peerBytes)

            val peerFiles = backend.listPeerJournals("folder-1").getOrNull()!!
            val peerFile = peerFiles.first()
            configStore.setPeerHighWaterMs(peerFile.fileId, peerFile.modifiedAtEpochMs)

            val applierCallsBefore = applierOperationDao.insertAppliedCalls

            journalSync.pull()

            assertEquals(
                "applier insertApplied must not be called when peer is not newer than high water",
                applierCallsBefore,
                applierOperationDao.insertAppliedCalls,
            )
        }

    @Test
    fun `pull does not apply own device journal when listed as peer`() =
        runTest {
            configStore.setFolderId("folder-1")
            val ownBytes = buildPeerJournal(peerDeviceId = deviceId, opId = "own-op")
            backend.uploadJournal(folderId = "folder-1", deviceId = deviceId, bytes = ownBytes)

            val callsBefore = applierOperationDao.insertAppliedCalls

            journalSync.pull()

            assertEquals(
                "own device journal must be filtered out during pull",
                callsBefore,
                applierOperationDao.insertAppliedCalls,
            )
        }

    @Test
    fun `pull is a no-op when folderId is blank`() =
        runTest {
            configStore.setFolderId("")
            val peerBytes = buildPeerJournal(peerDeviceId = "device-peer", opId = "noop-op")
            backend.uploadJournal(folderId = "folder-1", deviceId = "device-peer", bytes = peerBytes)

            val callsBefore = applierOperationDao.insertAppliedCalls

            journalSync.pull()

            assertEquals(
                "applier must not be called when folder is not configured",
                callsBefore,
                applierOperationDao.insertAppliedCalls,
            )
        }

    @Test
    fun `pull is a no-op when there are no peer journals`() =
        runTest {
            configStore.setFolderId("folder-empty")

            val callsBefore = applierOperationDao.insertAppliedCalls

            journalSync.pull()

            assertEquals(callsBefore, applierOperationDao.insertAppliedCalls)
        }

    @Test
    fun `pull updates lastSyncAt when at least one peer is applied`() =
        runTest {
            configStore.setFolderId("folder-1")
            val peerBytes = buildPeerJournal(peerDeviceId = "device-peer-4", opId = "peer-op-4")
            backend.uploadJournal(folderId = "folder-1", deviceId = "device-peer-4", bytes = peerBytes)

            journalSync.pull()

            assertEquals(clock.millis(), appSettings.current().lastSyncAt)
        }

    @Test
    fun `pull does not update lastSyncAt when no peer is applied`() =
        runTest {
            configStore.setFolderId("folder-1")
            val settingsBefore = appSettings.current()

            journalSync.pull()

            assertEquals(
                "lastSyncAt must not change when pull is a no-op",
                settingsBefore.lastSyncAt,
                appSettings.current().lastSyncAt,
            )
        }

    @Test
    fun `pull survives list error and does not throw`() =
        runTest {
            configStore.setFolderId("folder-1")
            backend.simulateListFailure(SyncError.Network)

            val callsBefore = applierOperationDao.insertAppliedCalls

            journalSync.pull()

            assertEquals("applier must not be called on list failure", callsBefore, applierOperationDao.insertAppliedCalls)
        }

    // ─── syncNow ordering ─────────────────────────────────────────────────────

    @Test
    fun `syncNow runs bootstrap then pull then push in the right order`() =
        runTest {
            configStore.setFolderId("folder-1")
            operationDao.seed(listOf(localOp(opId = "op-sync-now")))
            val peerBytes = buildPeerJournal(peerDeviceId = "device-peer-5", opId = "peer-op-5")
            backend.uploadJournal(folderId = "folder-1", deviceId = "device-peer-5", bytes = peerBytes)

            journalSync.syncNow()

            assertTrue("bootstrap must have marked done during syncNow", configStore.isBootstrapDone())
            assertTrue("push must have uploaded local op", backend.uploadCalls.isNotEmpty())
            assertTrue("op must be marked synced after syncNow", operationDao.syncedIds.contains("op-sync-now"))
        }

    @Test
    fun `syncNow with empty folderId is a complete no-op except for bootstrap`() =
        runTest {
            configStore.setFolderId("")
            operationDao.seed(listOf(localOp(opId = "op-noop")))

            journalSync.syncNow()

            assertTrue("bootstrap must run even when folderId is blank", configStore.isBootstrapDone())
            assertTrue("no upload must happen when folderId is blank", backend.uploadCalls.isEmpty())
        }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private fun localOp(
        opId: String,
        entityUuid: String = "entity-uuid",
        updatedAt: Long = 1_000L,
    ) = OperationEntity(
        opId = opId,
        deviceId = deviceId,
        entityKind = EntityKind.Account.name,
        entityUuid = entityUuid,
        opType = OpType.Upsert.name,
        payload = null,
        updatedAt = updatedAt,
        syncedToRemote = false,
        appliedFromRemote = false,
    )

    private fun buildPeerJournal(
        peerDeviceId: String,
        opId: String,
    ): ByteArray {
        val op =
            Operation(
                opId = opId,
                deviceId = peerDeviceId,
                entityKind = EntityKind.Account,
                entityUuid = "peer-entity-uuid-${opId.hashCode()}",
                opType = OpType.Upsert,
                payload = null,
                updatedAt = Instant.ofEpochMilli(1_000L),
            )
        return JournalSerializer().encode(listOf(op))
    }

    // ─── inner fakes ──────────────────────────────────────────────────────────

    private inner class FakeOperationDao : OperationDao {
        private var unsynced: List<OperationEntity> = emptyList()
        val syncedIds: MutableList<String> = mutableListOf()

        fun seed(ops: List<OperationEntity>) {
            unsynced = ops
        }

        override suspend fun insert(op: OperationEntity) = Unit

        override suspend fun insertAll(ops: List<OperationEntity>) = Unit

        override suspend fun insertApplied(ops: List<OperationEntity>) = Unit

        override suspend fun unsyncedLocal(): List<OperationEntity> = unsynced

        override suspend fun markSynced(opIds: List<String>) {
            syncedIds += opIds
            unsynced = unsynced.filterNot { it.opId in opIds }
        }

        override suspend fun knownOpIds(): List<String> = emptyList()

        override suspend fun knownEntityUuids(): List<String> = emptyList()

        override suspend fun existsByOpId(opId: String): Boolean = false

        override suspend fun opsForEntity(entityUuid: String): List<OperationEntity> = emptyList()
    }

    private class FakeJournalSyncConfigStore : JournalSyncConfigStore {
        private var folderId: String = ""
        private val peerHighWater: MutableMap<String, Long> = mutableMapOf()
        private var bootstrapDone: Boolean = false

        override suspend fun folderId(): String = folderId

        override suspend fun setFolderId(folderId: String) {
            this.folderId = folderId
        }

        override suspend fun peerHighWaterMs(fileId: String): Long = peerHighWater[fileId] ?: 0L

        override suspend fun setPeerHighWaterMs(
            fileId: String,
            modifiedAtMs: Long,
        ) {
            val current = peerHighWater[fileId] ?: 0L
            if (modifiedAtMs > current) peerHighWater[fileId] = modifiedAtMs
        }

        override suspend fun isBootstrapDone(): Boolean = bootstrapDone

        override suspend fun markBootstrapDone() {
            bootstrapDone = true
        }
    }

    private class FakeDeviceIdProvider(
        private val id: String,
    ) : DeviceIdProvider {
        override suspend fun deviceId(): String = id
    }

    /**
     * Records calls to [insertApplied] — used to verify that the JournalApplier ran.
     * All other queries return empty/no-op to keep JournalApplier's logic from doing real work
     * inside the JVM test (no Room).
     */
    private class ApplierRecordingOperationDao : OperationDao {
        var insertAppliedCalls: Int = 0

        override suspend fun insert(op: OperationEntity) = Unit

        override suspend fun insertAll(ops: List<OperationEntity>) = Unit

        override suspend fun insertApplied(ops: List<OperationEntity>) {
            insertAppliedCalls++
        }

        override suspend fun unsyncedLocal(): List<OperationEntity> = emptyList()

        override suspend fun markSynced(opIds: List<String>) = Unit

        override suspend fun knownOpIds(): List<String> = emptyList()

        override suspend fun knownEntityUuids(): List<String> = emptyList()

        override suspend fun existsByOpId(opId: String): Boolean = false

        override suspend fun opsForEntity(entityUuid: String): List<OperationEntity> = emptyList()
    }

    private class BootstrapRecordingOperationDao : OperationDao {
        var insertAllCalls: Int = 0

        override suspend fun insert(op: OperationEntity) = Unit

        override suspend fun insertAll(ops: List<OperationEntity>) {
            insertAllCalls++
        }

        override suspend fun insertApplied(ops: List<OperationEntity>) = Unit

        override suspend fun unsyncedLocal(): List<OperationEntity> = emptyList()

        override suspend fun markSynced(opIds: List<String>) = Unit

        override suspend fun knownOpIds(): List<String> = emptyList()

        override suspend fun knownEntityUuids(): List<String> = emptyList()

        override suspend fun existsByOpId(opId: String): Boolean = false

        override suspend fun opsForEntity(entityUuid: String): List<OperationEntity> = emptyList()
    }
}

// ─── top-level no-op DAO stubs ────────────────────────────────────────────────────────────────
// These are used both as dependencies for JournalApplier/JournalBootstrap in the unit test and
// by TransactionRepositoryImplTest (separate file). They live at file scope to keep them concise.

private class NoOpAccountDao : AccountDao() {
    override fun observeActive(): Flow<List<AccountEntity>> = flowOf(emptyList())

    override suspend fun findById(id: Long): AccountEntity? = null

    override suspend fun findByUuid(uuid: String): AccountEntity? = null

    override suspend fun listAll(): List<AccountEntity> = emptyList()

    override suspend fun stampMissingDeviceId(deviceId: String) = Unit

    override suspend fun archiveByUuid(uuid: String) = Unit

    override suspend fun findDefault(): AccountEntity? = null

    override suspend fun listDefaults(): List<AccountEntity> = emptyList()

    override suspend fun computeBalance(id: Long): Double = 0.0

    override suspend fun upsert(account: AccountEntity): Long = account.id

    override suspend fun archive(id: Long) = Unit

    override suspend fun countByCurrency(id: Long): Int = 0

    override suspend fun deleteAll() = Unit

    override suspend fun clearDefaults() = Unit

    override suspend fun markDefault(id: Long) = Unit
}

private class NoOpCategoryDao : CategoryDao {
    override fun observeByKind(kind: String): Flow<List<CategoryEntity>> = flowOf(emptyList())

    override fun observeAll(): Flow<List<CategoryEntity>> = flowOf(emptyList())

    override suspend fun findById(id: Long): CategoryEntity? = null

    override suspend fun findByUuid(uuid: String): CategoryEntity? = null

    override suspend fun listAll(): List<CategoryEntity> = emptyList()

    override suspend fun stampMissingDeviceId(deviceId: String) = Unit

    override suspend fun archiveByUuid(uuid: String) = Unit

    override suspend fun upsert(category: CategoryEntity): Long = category.id

    override suspend fun upsertAll(categories: List<CategoryEntity>) = Unit

    override suspend fun archive(id: Long) = Unit

    override suspend fun rename(
        id: Long,
        name: String,
    ) = Unit

    override suspend fun deleteAll() = Unit

    override suspend fun deleteByIds(ids: List<Long>) = Unit
}

private class NoOpTransactionDao : TransactionDao {
    override fun pagedByAccount(
        accountId: Long,
        from: Long,
        to: Long,
    ): androidx.paging.PagingSource<Int, TransactionEntity> = error("unused")

    override fun pagedByPeriod(
        accountId: Long,
        categoryId: Long?,
        from: Long,
        to: Long,
    ): androidx.paging.PagingSource<Int, TransactionEntity> = error("unused")

    override fun observeRecent(limit: Int): Flow<List<TransactionEntity>> = flowOf(emptyList())

    override fun observeAll(): Flow<List<TransactionEntity>> = flowOf(emptyList())

    override suspend fun getCategorySummary(
        accountId: Long,
        from: Long,
        to: Long,
        kind: String,
    ) = emptyList<CategorySummaryRow>()

    override suspend fun getCategoryGroups(
        accountId: Long,
        from: Long,
        to: Long,
    ) = emptyList<CategoryGroupRow>()

    override suspend fun listByPeriod(
        accountId: Long,
        from: Long,
        to: Long,
    ): List<TransactionEntity> = emptyList()

    override suspend fun getTransfers(
        accountId: Long?,
        from: Long,
        to: Long,
    ) = emptyList<TransferRow>()

    override suspend fun searchByNote(
        q: String,
        limit: Int,
    ): List<TransactionEntity> = emptyList()

    override suspend fun findById(id: Long): TransactionEntity? = null

    override suspend fun findByUuid(uuid: String): TransactionEntity? = null

    override suspend fun softDeleteByUuid(
        uuid: String,
        now: Long,
    ) = Unit

    override suspend fun listForTimezoneNormalization(): List<TransactionEntity> = emptyList()

    override suspend fun listAll(): List<TransactionEntity> = emptyList()

    override suspend fun stampMissingDeviceId(deviceId: String) = Unit

    override suspend fun upsert(transaction: TransactionEntity): Long = transaction.id

    override suspend fun updateOccurredAt(
        id: Long,
        occurredAt: Long,
        updatedAt: Long,
    ) = Unit

    override suspend fun softDelete(
        id: Long,
        now: Long,
    ) = Unit

    override suspend fun restore(
        id: Long,
        now: Long,
    ) = Unit

    override suspend fun pruneDeleted(before: Long) = Unit

    override suspend fun countByAccount(id: Long): Int = 0

    override suspend fun countByCategory(id: Long): Int = 0

    override suspend fun countByCurrency(id: Long): Int = 0

    override suspend fun listDedupRows(): List<TransactionDedupRow> = emptyList()

    override suspend fun deleteAll() = Unit

    override suspend fun reassignCategory(
        oldId: Long,
        newId: Long,
    ) = Unit

    override suspend fun deleteByCategory(categoryId: Long) = Unit
}
