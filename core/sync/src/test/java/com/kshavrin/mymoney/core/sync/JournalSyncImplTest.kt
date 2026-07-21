package com.kshavrin.mymoney.core.sync

import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
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
import org.junit.Assert.assertFalse
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
    private val codec = OperationPayloadCodec()

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
    fun `push propagates upload failure and does not mark ops synced`() =
        runTest {
            configStore.setFolderId("folder-1")
            operationDao.seed(listOf(localOp(opId = "op-fail")))
            backend.simulateUploadFailure(SyncError.Network)

            val failure = runCatching { journalSync.push() }.exceptionOrNull()

            assertTrue("upload failure must propagate to the caller", failure is SyncException)
            assertEquals(SyncError.Network, (failure as SyncException).syncError)
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
    fun `pull propagates list error and does not apply journals`() =
        runTest {
            configStore.setFolderId("folder-1")
            backend.simulateListFailure(SyncError.Network)

            val callsBefore = applierOperationDao.insertAppliedCalls

            val failure = runCatching { journalSync.pull() }.exceptionOrNull()

            assertTrue("list failure must propagate to the caller", failure is SyncException)
            assertEquals(SyncError.Network, (failure as SyncException).syncError)
            assertEquals("applier must not be called on list failure", callsBefore, applierOperationDao.insertAppliedCalls)
        }

    @Test
    fun `pull propagates download error without advancing the peer high water mark`() =
        runTest {
            configStore.setFolderId("folder-1")
            val peerDeviceId = "device-download-failure"
            backend.uploadJournal(
                folderId = "folder-1",
                deviceId = peerDeviceId,
                bytes = buildPeerJournal(peerDeviceId = peerDeviceId, opId = "download-failure-op"),
            )
            val peerFile = backend.listPeerJournals("folder-1").getOrThrow().single()
            backend.simulateDownloadFailure(SyncError.Network)

            val failure = runCatching { journalSync.pull() }.exceptionOrNull()

            assertTrue("download failure must propagate to the caller", failure is SyncException)
            assertEquals(SyncError.Network, (failure as SyncException).syncError)
            assertEquals(0L, configStore.peerHighWaterMs(peerFile.fileId))
        }

    @Test
    fun `pull stops applying further peers when folderId is cleared mid-loop`() =
        runTest {
            // folderId() returns "folder-1" for the first 2 calls (initial guard + loop iter 1),
            // then blank on the 3rd call (loop iter 2 re-check) — triggers early return before
            // the second peer is applied.
            configStore.clearAfterFolderIdCallN = 3
            configStore.setFolderId("folder-1")

            val peerBytesA = buildPeerJournal(peerDeviceId = "device-mid-A", opId = "mid-op-a")
            val peerBytesB = buildPeerJournal(peerDeviceId = "device-mid-B", opId = "mid-op-b")
            backend.uploadJournal(folderId = "folder-1", deviceId = "device-mid-A", bytes = peerBytesA)
            backend.uploadJournal(folderId = "folder-1", deviceId = "device-mid-B", bytes = peerBytesB)

            val callsBefore = applierOperationDao.insertAppliedCalls

            journalSync.pull()

            assertEquals(
                "only the first peer must be applied; folderId cleared mid-loop stops the second",
                callsBefore + 1,
                applierOperationDao.insertAppliedCalls,
            )
        }

    // ─── push: cumulative journal (FIX 1) ────────────────────────────────────

    @Test
    fun `push uploads full cumulative local journal including previously-synced ops`() =
        runTest {
            // op-history was pushed in a prior session: syncedToRemote=true.
            // op-new is fresh and has not been pushed yet.
            configStore.setFolderId("folder-1")
            operationDao.seed(
                listOf(
                    localOp(opId = "op-history", updatedAt = 1_000L, syncedToRemote = true),
                    localOp(opId = "op-new", updatedAt = 2_000L, syncedToRemote = false),
                ),
            )

            journalSync.push()

            assertEquals("exactly one upload call expected", 1, backend.uploadCalls.size)
            val uploadedOps = JournalSerializer().decode(backend.uploadCalls.single().bytes)
            val uploadedIds = uploadedOps.map { it.opId }.toSet()
            assertTrue(
                "upload must contain the previously-synced op (cumulative journal, not delta)",
                uploadedIds.contains("op-history"),
            )
            assertTrue(
                "upload must contain the new unsynced op",
                uploadedIds.contains("op-new"),
            )
            assertEquals("uploaded journal must carry exactly the two local ops", 2, uploadedOps.size)
            // markSynced only targets the unsynced guard list — not the already-synced op
            assertTrue("op-new must be marked synced", operationDao.syncedIds.contains("op-new"))
            assertFalse("op-history must not be re-marked synced", operationDao.syncedIds.contains("op-history"))
        }

    @Test
    fun `push is no-op when unsyncedLocal is empty even if localOps has previously-synced ops`() =
        runTest {
            configStore.setFolderId("folder-1")
            // Only a previously-synced op: unsyncedLocal() returns empty → guard triggers early return.
            operationDao.seed(
                listOf(
                    localOp(opId = "op-already-synced", syncedToRemote = true),
                ),
            )

            journalSync.push()

            assertTrue(
                "no upload must occur when unsyncedLocal is empty, even if localOps is non-empty",
                backend.uploadCalls.isEmpty(),
            )
            assertTrue("markSynced must not be called", operationDao.syncedIds.isEmpty())
        }

    // ─── pull: conditional high-water (FIX 2) ─────────────────────────────────

    @Test
    fun `pull does not advance high water when applier reports hadSkips for an unresolved op`() =
        runTest {
            configStore.setFolderId("folder-1")
            val peerDeviceId = "device-peer-skips"
            // Null payload forces applyAccount to return false → hadSkips=true → high-water stays.
            val peerBytes = buildNullPayloadPeerJournal(peerDeviceId = peerDeviceId, opId = "skip-op")
            backend.uploadJournal(folderId = "folder-1", deviceId = peerDeviceId, bytes = peerBytes)

            val peerFile =
                backend
                    .listPeerJournals("folder-1")
                    .getOrThrow()
                    .single { it.deviceId == peerDeviceId }

            journalSync.pull()

            assertEquals(
                "high-water must NOT advance when hadSkips=true (peer file must be re-pulled on next sync)",
                0L,
                configStore.peerHighWaterMs(peerFile.fileId),
            )
        }

    @Test
    fun `pull does not advance high water on transaction FK miss and advances after cumulative journal resolves the dependency`() =
        runTest {
            // ── Phase 1: peer journal contains ONLY a Transaction op whose account UUID is not
            //   present in the local DB.  The applier returns hadSkips=true → high water stays 0.
            val fkMissFolderId = "folder-fk-miss"
            val fkConvergeFolderId = "folder-fk-convergence"
            val peerDeviceId = "device-peer-fk"
            val missingAccountUuid = "acc-fk-miss"
            val txEntityUuid = "tx-fk-miss"

            configStore.setFolderId(fkMissFolderId)

            val txOnlyBytes =
                buildTxOnlyPeerJournal(
                    peerDeviceId = peerDeviceId,
                    txOpId = "tx-op-fk-miss",
                    txEntityUuid = txEntityUuid,
                    referencedAccountUuid = missingAccountUuid,
                )
            backend.uploadJournal(
                folderId = fkMissFolderId,
                deviceId = peerDeviceId,
                bytes = txOnlyBytes,
            )
            val fkMissPeerFile =
                backend
                    .listPeerJournals(fkMissFolderId)
                    .getOrThrow()
                    .single { it.deviceId == peerDeviceId }

            journalSync.pull()

            assertEquals(
                "high water must NOT advance when applyTransaction returns false due to missing account FK",
                0L,
                configStore.peerHighWaterMs(fkMissPeerFile.fileId),
            )

            // ── Phase 2 (convergence): fresh JournalSyncImpl wired with StoringAccountDao so that
            //   applyAccount deposits the account and the follow-on applyTransaction can resolve it
            //   within the same apply() call — hadSkips=false → high water advances.
            val storingAccountDao = StoringAccountDao()
            val localCodec = OperationPayloadCodec()
            val localNoOpRunner =
                object : TransactionRunner {
                    override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
                }
            val convergenceApplierOpDao = ApplierRecordingOperationDao()
            val convergenceApplier =
                JournalApplier(
                    transactionDao = NoOpTransactionDao(),
                    categoryDao = NoOpCategoryDao(),
                    accountDao = storingAccountDao,
                    operationDao = convergenceApplierOpDao,
                    payloadCodec = localCodec,
                    transactionRunner = localNoOpRunner,
                )
            val backend2 = FakeJournalBackend(ownDeviceId = deviceId)
            val configStore2 = FakeJournalSyncConfigStore()
            configStore2.setFolderId(fkConvergeFolderId)
            val convergenceBootstrapOpDao = BootstrapRecordingOperationDao()
            val convergenceSync =
                JournalSyncImpl(
                    operationDao = FakeOperationDao().also { it.seed(emptyList()) },
                    serializer = JournalSerializer(),
                    backend = backend2,
                    applier = convergenceApplier,
                    bootstrap =
                        JournalBootstrap(
                            accountDao = storingAccountDao,
                            categoryDao = NoOpCategoryDao(),
                            transactionDao = NoOpTransactionDao(),
                            operationDao = convergenceBootstrapOpDao,
                            payloadCodec = localCodec,
                            deviceIdProvider = FakeDeviceIdProvider(deviceId),
                            transactionRunner = localNoOpRunner,
                            configStore = configStore2,
                        ),
                    configStore = configStore2,
                    deviceIdProvider = FakeDeviceIdProvider(deviceId),
                    appSettings = FakeAppSettingsRepository(),
                    clock = clock,
                    ioDispatcher = dispatcher,
                )

            val cumulativeBytes =
                buildCumulativePeerJournal(
                    peerDeviceId = peerDeviceId,
                    accountOpId = "acc-op-fk-cumulative",
                    accountEntityUuid = missingAccountUuid,
                    txOpId = "tx-op-fk-cumulative",
                    txEntityUuid = txEntityUuid,
                    referencedAccountUuid = missingAccountUuid,
                )
            backend2.uploadJournal(
                folderId = fkConvergeFolderId,
                deviceId = peerDeviceId,
                bytes = cumulativeBytes,
            )
            val convergencePeerFile =
                backend2
                    .listPeerJournals(fkConvergeFolderId)
                    .getOrThrow()
                    .single { it.deviceId == peerDeviceId }

            convergenceSync.pull()

            assertTrue(
                "high water must advance after cumulative journal resolves the account FK dependency",
                configStore2.peerHighWaterMs(convergencePeerFile.fileId) >= convergencePeerFile.modifiedAtEpochMs,
            )
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
        syncedToRemote: Boolean = false,
    ) = OperationEntity(
        opId = opId,
        deviceId = deviceId,
        entityKind = EntityKind.Account.name,
        entityUuid = entityUuid,
        opType = OpType.Upsert.name,
        payload = null,
        updatedAt = updatedAt,
        syncedToRemote = syncedToRemote,
        appliedFromRemote = false,
    )

    /**
     * Builds a serialised peer journal containing a single Account Upsert with a VALID payload.
     * With a valid payload, [JournalApplier.applyAccount] returns true (no skip), so
     * [ApplyResult.hadSkips] is false and [JournalSyncImpl.pull] correctly advances the
     * peer high-water mark — the intended behaviour for a clean remote file.
     */
    private fun buildPeerJournal(
        peerDeviceId: String,
        opId: String,
    ): ByteArray {
        val entityUuid = "peer-entity-uuid-${opId.hashCode()}"
        val payload =
            codec.encodeAccount(
                AccountEntity(
                    uuid = entityUuid,
                    deviceId = peerDeviceId,
                    name = "Peer Account",
                    currencyId = 1L,
                    initialBalance = 0.0,
                    type = "cash",
                    colorHex = "#FFFFFF",
                    iconKey = "ic_account_cash",
                    isDefault = false,
                    sortOrder = 1,
                    createdAt = 1_000L,
                    updatedAt = 1_000L,
                    isArchived = false,
                ),
            )
        val op =
            Operation(
                opId = opId,
                deviceId = peerDeviceId,
                entityKind = EntityKind.Account,
                entityUuid = entityUuid,
                opType = OpType.Upsert,
                payload = payload,
                updatedAt = Instant.ofEpochMilli(1_000L),
            )
        return JournalSerializer().encode(listOf(op))
    }

    /**
     * Builds a peer journal with a null-payload Account Upsert op. The applier's [applyAccount]
     * hits the `payload ?: return false` guard → returns false → [ApplyResult.hadSkips] = true.
     * Used to verify that pull does NOT advance the high-water mark when the applier skips ops.
     */
    private fun buildNullPayloadPeerJournal(
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

    /**
     * Builds a serialised peer journal containing ONLY a Transaction Upsert that references
     * [referencedAccountUuid]. Because no Account Upsert is included, [JournalApplier.applyTransaction]
     * will call [AccountDao.findByUuid] and get null → return false → [ApplyResult.hadSkips] = true.
     */
    private fun buildTxOnlyPeerJournal(
        peerDeviceId: String,
        txOpId: String,
        txEntityUuid: String,
        referencedAccountUuid: String,
    ): ByteArray {
        val txPayload =
            codec.encodeTransaction(
                entity =
                    TransactionEntity(
                        uuid = txEntityUuid,
                        deviceId = peerDeviceId,
                        kind = "expense",
                        amount = 5.0,
                        currencyId = 1L,
                        accountId = 0L,
                        categoryId = null,
                        note = null,
                        occurredAt = 1_000L,
                        createdAt = 1_000L,
                        updatedAt = 1_000L,
                        isDeleted = false,
                        toAccountId = null,
                        toAmount = null,
                        exchangeRate = null,
                    ),
                accountUuid = referencedAccountUuid,
                categoryUuid = null,
                toAccountUuid = null,
            )
        val op =
            Operation(
                opId = txOpId,
                deviceId = peerDeviceId,
                entityKind = EntityKind.Transaction,
                entityUuid = txEntityUuid,
                opType = OpType.Upsert,
                payload = txPayload,
                updatedAt = Instant.ofEpochMilli(1_000L),
            )
        return JournalSerializer().encode(listOf(op))
    }

    /**
     * Builds a serialised peer journal containing an Account Upsert followed by a Transaction Upsert
     * that references the same account UUID.  The applier processes the account first, deposits it
     * via [AccountDao.upsert], and the transaction op then resolves its FK — [ApplyResult.hadSkips]
     * is false and the peer high-water mark advances.
     */
    private fun buildCumulativePeerJournal(
        peerDeviceId: String,
        accountOpId: String,
        accountEntityUuid: String,
        txOpId: String,
        txEntityUuid: String,
        referencedAccountUuid: String,
    ): ByteArray {
        val accountPayload =
            codec.encodeAccount(
                AccountEntity(
                    uuid = accountEntityUuid,
                    deviceId = peerDeviceId,
                    name = "Peer Account Resolved",
                    currencyId = 1L,
                    initialBalance = 0.0,
                    type = "cash",
                    colorHex = "#FFFFFF",
                    iconKey = "ic_account_cash",
                    isDefault = false,
                    sortOrder = 1,
                    createdAt = 900L,
                    updatedAt = 900L,
                    isArchived = false,
                ),
            )
        val txPayload =
            codec.encodeTransaction(
                entity =
                    TransactionEntity(
                        uuid = txEntityUuid,
                        deviceId = peerDeviceId,
                        kind = "expense",
                        amount = 5.0,
                        currencyId = 1L,
                        accountId = 0L,
                        categoryId = null,
                        note = null,
                        occurredAt = 1_000L,
                        createdAt = 1_000L,
                        updatedAt = 1_000L,
                        isDeleted = false,
                        toAccountId = null,
                        toAmount = null,
                        exchangeRate = null,
                    ),
                accountUuid = referencedAccountUuid,
                categoryUuid = null,
                toAccountUuid = null,
            )
        val ops =
            listOf(
                Operation(
                    opId = accountOpId,
                    deviceId = peerDeviceId,
                    entityKind = EntityKind.Account,
                    entityUuid = accountEntityUuid,
                    opType = OpType.Upsert,
                    payload = accountPayload,
                    updatedAt = Instant.ofEpochMilli(900L),
                ),
                Operation(
                    opId = txOpId,
                    deviceId = peerDeviceId,
                    entityKind = EntityKind.Transaction,
                    entityUuid = txEntityUuid,
                    opType = OpType.Upsert,
                    payload = txPayload,
                    updatedAt = Instant.ofEpochMilli(1_000L),
                ),
            )
        return JournalSerializer().encode(ops)
    }

    // ─── inner fakes ──────────────────────────────────────────────────────────

    private inner class FakeOperationDao : OperationDao {
        // Stores all local ops (including already-synced ones).  markSynced mutates the flag
        // in-place; unsyncedLocal() filters the live list so the invariant is always maintained
        // without a separate "unsynced" variable.
        private val allOps: MutableList<OperationEntity> = mutableListOf()
        val syncedIds: MutableList<String> = mutableListOf()

        fun seed(ops: List<OperationEntity>) {
            allOps.clear()
            allOps.addAll(ops)
        }

        override suspend fun insert(op: OperationEntity) = Unit

        override suspend fun insertAll(ops: List<OperationEntity>) = Unit

        override suspend fun insertApplied(ops: List<OperationEntity>) = Unit

        override suspend fun unsyncedLocal(): List<OperationEntity> =
            allOps.filter { !it.syncedToRemote && !it.appliedFromRemote }

        override suspend fun localOps(): List<OperationEntity> =
            allOps.filter { !it.appliedFromRemote }.sortedBy { it.updatedAt }

        override suspend fun markSynced(opIds: List<String>) {
            syncedIds += opIds
            val idsSet = opIds.toHashSet()
            val updated = allOps.map { if (it.opId in idsSet) it.copy(syncedToRemote = true) else it }
            allOps.clear()
            allOps.addAll(updated)
        }

        override suspend fun knownOpIds(): List<String> = emptyList()

        override suspend fun knownEntityUuids(): List<String> = emptyList()

        override suspend fun existsByOpId(opId: String): Boolean = false

        override suspend fun opsForEntity(entityUuid: String): List<OperationEntity> = emptyList()

        override suspend fun deleteAll() {
            allOps.clear()
            syncedIds.clear()
        }
    }

    private class FakeJournalSyncConfigStore : JournalSyncConfigStore {
        private var folderId: String = ""
        private val peerHighWater: MutableMap<String, Long> = mutableMapOf()
        private var bootstrapDone: Boolean = false
        // When set, folderId() returns blank starting from the N-th call (1-based).
        // Defaults to MAX_VALUE so existing tests are unaffected.
        var clearAfterFolderIdCallN: Int = Int.MAX_VALUE
        private var folderIdCallCount = 0

        override suspend fun folderId(): String {
            folderIdCallCount++
            if (folderIdCallCount >= clearAfterFolderIdCallN) {
                folderId = ""
            }
            return folderId
        }

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

        override suspend fun localOps(): List<OperationEntity> = emptyList()

        override suspend fun markSynced(opIds: List<String>) = Unit

        override suspend fun knownOpIds(): List<String> = emptyList()

        override suspend fun knownEntityUuids(): List<String> = emptyList()

        override suspend fun existsByOpId(opId: String): Boolean = false

        override suspend fun opsForEntity(entityUuid: String): List<OperationEntity> = emptyList()

        override suspend fun deleteAll() = Unit
    }

    private class BootstrapRecordingOperationDao : OperationDao {
        var insertAllCalls: Int = 0

        override suspend fun insert(op: OperationEntity) = Unit

        override suspend fun insertAll(ops: List<OperationEntity>) {
            insertAllCalls++
        }

        override suspend fun insertApplied(ops: List<OperationEntity>) = Unit

        override suspend fun unsyncedLocal(): List<OperationEntity> = emptyList()

        override suspend fun localOps(): List<OperationEntity> = emptyList()

        override suspend fun markSynced(opIds: List<String>) = Unit

        override suspend fun knownOpIds(): List<String> = emptyList()

        override suspend fun knownEntityUuids(): List<String> = emptyList()

        override suspend fun existsByOpId(opId: String): Boolean = false

        override suspend fun opsForEntity(entityUuid: String): List<OperationEntity> = emptyList()

        override suspend fun deleteAll() = Unit
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

/**
 * AccountDao stub that actually stores accounts by UUID — required for the convergence half of the
 * FK-miss integration test where [JournalApplier.applyAccount] must deposit an account so that a
 * subsequent [JournalApplier.applyTransaction] can resolve its FK within the same [apply] call.
 */
private class StoringAccountDao : AccountDao() {
    private val store = mutableMapOf<String, AccountEntity>()
    private var nextId = 1L

    override fun observeActive(): Flow<List<AccountEntity>> = flowOf(emptyList())

    override suspend fun findById(id: Long): AccountEntity? = store.values.firstOrNull { it.id == id }

    override suspend fun findByUuid(uuid: String): AccountEntity? = store[uuid]

    override suspend fun listAll(): List<AccountEntity> = store.values.toList()

    override suspend fun stampMissingDeviceId(deviceId: String) = Unit

    override suspend fun archiveByUuid(uuid: String) = Unit

    override suspend fun findDefault(): AccountEntity? = null

    override suspend fun listDefaults(): List<AccountEntity> = emptyList()

    override suspend fun computeBalance(id: Long): Double = 0.0

    override suspend fun upsert(account: AccountEntity): Long {
        val id = if (account.id == 0L) nextId++ else account.id
        store[account.uuid] = account.copy(id = id)
        return id
    }

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
