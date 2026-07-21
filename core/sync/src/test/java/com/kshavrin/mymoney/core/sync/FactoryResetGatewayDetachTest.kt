package com.kshavrin.mymoney.core.sync

import com.kshavrin.mymoney.core.database.dao.OperationDao
import com.kshavrin.mymoney.core.database.entity.OperationEntity
import com.kshavrin.mymoney.core.datastore.JournalSyncConfigStore
import com.kshavrin.mymoney.core.datastore.SecureStorage
import com.kshavrin.mymoney.core.datastore.model.SecureSettings
import com.kshavrin.mymoney.core.domain.sync.EntityKind
import com.kshavrin.mymoney.core.domain.sync.OpType
import com.kshavrin.mymoney.core.sync.fake.FakeBackupRepository
import com.kshavrin.mymoney.core.testing.fake.FakeAppSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies the op-journal reset semantics required by the factory-reset feature:
 *
 * (a) After [FactoryResetGatewayImpl.detachCloudSync] the op_journal table is empty.
 * (b) After detach, [JournalSyncConfigStore.folderId], bootstrapDone, and all peer high-water
 *     marks are cleared — a blank folderId prevents any subsequent sync from touching the backend.
 *
 * Invariant (c) — that [JournalSyncImpl.syncNow] is a complete no-op when folderId is blank — is
 * already pinned by [JournalSyncImplTest]'s "syncNow with empty folderId…" and
 * "push is a no-op when folderId is blank" test cases, so it is not duplicated here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FactoryResetGatewayDetachTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val callOrder = mutableListOf<String>()
    private lateinit var operationDao: TrackingOperationDao
    private lateinit var configStore: TrackingJournalSyncConfigStore
    private lateinit var syncScheduler: FakeSyncScheduler
    private lateinit var gateway: FactoryResetGatewayImpl

    @Before
    fun setUp() {
        operationDao = TrackingOperationDao(callOrder)
        configStore = TrackingJournalSyncConfigStore()
        syncScheduler = FakeSyncScheduler(callOrder)
        gateway =
            FactoryResetGatewayImpl(
                backupRepository = FakeBackupRepository(),
                appSettingsRepository = FakeAppSettingsRepository(),
                secureStorage = NoOpSecureStorage,
                journalSyncConfigStore = configStore,
                operationDao = operationDao,
                syncScheduler = syncScheduler,
                ioDispatcher = dispatcher,
            )
    }

    @Test
    fun `detachCloudSync calls deleteAll so op_journal is empty afterward`() =
        runTest {
            operationDao.seed(operationEntity("op-1"), operationEntity("op-2"))

            gateway.detachCloudSync()

            assertTrue("deleteAll must be called on the operation DAO", operationDao.deleteAllCalled)
            assertTrue("op_journal must be empty after detach", operationDao.knownOpIds().isEmpty())
        }

    @Test
    fun `detachCloudSync disables periodic sync before wiping op_journal`() =
        runTest {
            operationDao.seed(operationEntity("op-order-check"))

            gateway.detachCloudSync()

            val disableIdx = callOrder.indexOf("disablePeriodicSync")
            val deleteAllIdx = callOrder.indexOf("deleteAll")
            assertTrue("disablePeriodicSync must be called during detach", syncScheduler.disablePeriodicSyncCalled)
            assertTrue("deleteAll must be called during detach", operationDao.deleteAllCalled)
            assertTrue(
                "disablePeriodicSync must precede deleteAll (cancel-first invariant)",
                disableIdx < deleteAllIdx,
            )
        }

    @Test
    fun `detachCloudSync clears folderId preventing cloud resurrection`() =
        runTest {
            configStore.setFolderId("drive-folder-abc")

            gateway.detachCloudSync()

            assertTrue(
                "folderId must be blank after detach — JournalSyncImpl will be a no-op",
                configStore.folderId().isBlank(),
            )
        }

    @Test
    fun `detachCloudSync clears bootstrap done flag`() =
        runTest {
            configStore.markBootstrapDone()

            gateway.detachCloudSync()

            assertFalse(
                "bootstrapDone must be cleared so a fresh bootstrap runs if the device re-links",
                configStore.isBootstrapDone(),
            )
        }

    @Test
    fun `detachCloudSync clears all peer high water marks`() =
        runTest {
            configStore.setPeerHighWaterMs("file-a", 1_000L)
            configStore.setPeerHighWaterMs("file-b", 2_000L)

            gateway.detachCloudSync()

            assertEquals(
                "peer hw for file-a must be zero after clear",
                0L,
                configStore.peerHighWaterMs("file-a"),
            )
            assertEquals(
                "peer hw for file-b must be zero after clear",
                0L,
                configStore.peerHighWaterMs("file-b"),
            )
        }

    @Test
    fun `detachCloudSync on already empty dao and blank config does not throw`() =
        runTest {
            gateway.detachCloudSync()

            assertTrue(operationDao.deleteAllCalled)
            assertTrue(configStore.folderId().isBlank())
        }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private fun operationEntity(opId: String) =
        OperationEntity(
            opId = opId,
            deviceId = "device-local",
            entityKind = EntityKind.Account.name,
            entityUuid = "entity-${opId.hashCode()}",
            opType = OpType.Upsert.name,
            payload = null,
            updatedAt = 1_000L,
        )

    // ─── inner fakes ──────────────────────────────────────────────────────────

    private class TrackingOperationDao(
        private val callOrder: MutableList<String>,
    ) : OperationDao {
        var deleteAllCalled = false
        private val ops: MutableList<OperationEntity> = mutableListOf()

        fun seed(vararg entities: OperationEntity) {
            ops.addAll(entities)
        }

        override suspend fun insert(op: OperationEntity) {
            ops += op
        }

        override suspend fun insertAll(ops: List<OperationEntity>) {
            this.ops.addAll(ops)
        }

        override suspend fun insertApplied(ops: List<OperationEntity>) = Unit

        override suspend fun unsyncedLocal(): List<OperationEntity> =
            ops.filter { !it.syncedToRemote && !it.appliedFromRemote }

        override suspend fun localOps(): List<OperationEntity> =
            ops.filter { !it.appliedFromRemote }.sortedBy { it.updatedAt }

        override suspend fun markSynced(opIds: List<String>) {
            val ids = opIds.toSet()
            ops.replaceAll { if (it.opId in ids) it.copy(syncedToRemote = true) else it }
        }

        override suspend fun knownOpIds(): List<String> = ops.map { it.opId }

        override suspend fun knownEntityUuids(): List<String> = ops.map { it.entityUuid }.distinct()

        override suspend fun existsByOpId(opId: String): Boolean = ops.any { it.opId == opId }

        override suspend fun opsForEntity(entityUuid: String): List<OperationEntity> =
            ops.filter { it.entityUuid == entityUuid }

        override suspend fun deleteAll() {
            callOrder += "deleteAll"
            deleteAllCalled = true
            ops.clear()
        }
    }

    private class TrackingJournalSyncConfigStore : JournalSyncConfigStore {
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

        override suspend fun clear() {
            folderId = ""
            bootstrapDone = false
            peerHighWater.clear()
        }
    }

    private object NoOpSecureStorage : SecureStorage {
        override fun read(): SecureSettings = SecureSettings()

        override fun writeDropboxRefreshToken(token: String?) = Unit

        override fun writeGdriveAccountEmail(email: String?) = Unit

        override fun writePinHash(hash: String?) = Unit

        override fun clearAll() = Unit
    }

    private class FakeSyncScheduler(
        private val callOrder: MutableList<String>,
    ) : SyncScheduler {
        var disablePeriodicSyncCalled = false

        override fun enablePeriodicSync() = Unit

        override fun disablePeriodicSync() {
            callOrder += "disablePeriodicSync"
            disablePeriodicSyncCalled = true
        }

        override fun syncNow(target: SyncTarget?) = Unit
    }
}
