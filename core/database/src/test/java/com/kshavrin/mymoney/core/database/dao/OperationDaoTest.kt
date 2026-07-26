package com.kshavrin.mymoney.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kshavrin.mymoney.core.database.MoneyDatabase
import com.kshavrin.mymoney.core.database.entity.OperationEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class OperationDaoTest {
    private lateinit var db: MoneyDatabase
    private lateinit var dao: OperationDao

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    MoneyDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        dao = db.operationDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `deleteAll empties the op_journal table`() =
        runTest {
            dao.insert(operationEntity("op-1"))
            dao.insert(operationEntity("op-2"))
            dao.insert(operationEntity("op-3"))

            dao.deleteAll()

            assertTrue("knownOpIds must be empty after deleteAll", dao.knownOpIds().isEmpty())
            assertTrue("unsyncedLocal must be empty after deleteAll", dao.unsyncedLocal().isEmpty())
        }

    @Test
    fun `deleteAll on empty table does not throw`() =
        runTest {
            dao.deleteAll()

            assertTrue(dao.knownOpIds().isEmpty())
        }

    @Test
    fun `insert makes op visible via knownOpIds`() =
        runTest {
            dao.insert(operationEntity("op-x"))

            val ids = dao.knownOpIds()
            assertEquals(1, ids.size)
            assertEquals("op-x", ids.first())
        }

    @Test
    fun `insertAll inserts multiple ops and all appear in knownOpIds`() =
        runTest {
            dao.insertAll(
                listOf(
                    operationEntity("op-a"),
                    operationEntity("op-b"),
                ),
            )

            val ids = dao.knownOpIds().toSet()
            assertTrue(ids.contains("op-a"))
            assertTrue(ids.contains("op-b"))
        }

    @Test
    fun `unsyncedLocal returns only ops not yet synced and not applied from remote`() =
        runTest {
            val local = operationEntity("op-local", syncedToRemote = false, appliedFromRemote = false)
            val synced = operationEntity("op-synced", syncedToRemote = true, appliedFromRemote = false)
            val remote = operationEntity("op-remote", syncedToRemote = false, appliedFromRemote = true)
            dao.insertAll(listOf(local, synced, remote))

            val unsynced = dao.unsyncedLocal()

            assertEquals(1, unsynced.size)
            assertEquals("op-local", unsynced.first().opId)
        }

    @Test
    fun `markSynced sets synced_to_remote to true for specified op ids`() =
        runTest {
            dao.insert(operationEntity("op-1"))
            dao.insert(operationEntity("op-2"))

            dao.markSynced(listOf("op-1"))

            val unsynced = dao.unsyncedLocal()
            assertTrue("op-1 must be gone from unsynced after markSynced", unsynced.none { it.opId == "op-1" })
            assertTrue("op-2 must still be unsynced", unsynced.any { it.opId == "op-2" })
        }

    @Test
    fun `deleteAll after markSynced still empties the table`() =
        runTest {
            dao.insert(operationEntity("op-1"))
            dao.markSynced(listOf("op-1"))

            dao.deleteAll()

            assertTrue(dao.knownOpIds().isEmpty())
        }

    // ─── localOps ─────────────────────────────────────────────────────────────

    @Test
    fun `localOps returns all local-origin ops regardless of synced_to_remote, ordered by updated_at ASC`() =
        runTest {
            // op-synced has syncedToRemote=true (already pushed) but applied_from_remote=0 so it IS local
            val opSynced = operationEntity("op-synced", syncedToRemote = true, appliedFromRemote = false, updatedAt = 1_000L)
            val opUnsyncedLater = operationEntity("op-unsynced-later", syncedToRemote = false, appliedFromRemote = false, updatedAt = 3_000L)
            val opUnsyncedMid = operationEntity("op-unsynced-mid", syncedToRemote = false, appliedFromRemote = false, updatedAt = 2_000L)
            dao.insertAll(listOf(opSynced, opUnsyncedLater, opUnsyncedMid))

            val local = dao.localOps()

            assertEquals("localOps must return all three local ops", 3, local.size)
            assertEquals("first by updated_at ASC must be op-synced (1000)", "op-synced", local[0].opId)
            assertEquals("second must be op-unsynced-mid (2000)", "op-unsynced-mid", local[1].opId)
            assertEquals("third must be op-unsynced-later (3000)", "op-unsynced-later", local[2].opId)
        }

    @Test
    fun `localOps excludes ops where applied_from_remote is true`() =
        runTest {
            val localOp = operationEntity("op-local-origin", syncedToRemote = false, appliedFromRemote = false)
            val remoteOp = operationEntity("op-remote-origin", syncedToRemote = false, appliedFromRemote = true)
            dao.insertAll(listOf(localOp, remoteOp))

            val local = dao.localOps()

            assertEquals(1, local.size)
            assertEquals("op-local-origin", local.first().opId)
            assertTrue(
                "op-remote-origin (applied_from_remote=1) must be excluded from localOps",
                local.none { it.opId == "op-remote-origin" },
            )
        }

    @Test
    fun `after markSynced unsyncedLocal shrinks to empty but localOps returns the full history`() =
        runTest {
            val op1 = operationEntity("op-hist-1", syncedToRemote = false, appliedFromRemote = false, updatedAt = 1_000L)
            val op2 = operationEntity("op-hist-2", syncedToRemote = false, appliedFromRemote = false, updatedAt = 2_000L)
            dao.insertAll(listOf(op1, op2))

            dao.markSynced(listOf("op-hist-1", "op-hist-2"))

            val unsynced = dao.unsyncedLocal()
            assertTrue("unsyncedLocal must be empty after markSynced for all ids", unsynced.isEmpty())

            val local = dao.localOps()
            assertEquals("localOps must still return both ops (cumulative history)", 2, local.size)
            val ids = local.map { it.opId }.toSet()
            assertTrue(ids.contains("op-hist-1"))
            assertTrue(ids.contains("op-hist-2"))
        }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private fun operationEntity(
        opId: String,
        syncedToRemote: Boolean = false,
        appliedFromRemote: Boolean = false,
        updatedAt: Long = System.currentTimeMillis(),
    ) = OperationEntity(
        opId = opId,
        deviceId = "device-test",
        entityKind = "Account",
        entityUuid = "uuid-${opId.hashCode()}",
        opType = "Upsert",
        payload = null,
        updatedAt = updatedAt,
        syncedToRemote = syncedToRemote,
        appliedFromRemote = appliedFromRemote,
    )
}
