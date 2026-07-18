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

    // ─── helpers ──────────────────────────────────────────────────────────────

    private fun operationEntity(
        opId: String,
        syncedToRemote: Boolean = false,
        appliedFromRemote: Boolean = false,
    ) = OperationEntity(
        opId = opId,
        deviceId = "device-test",
        entityKind = "Account",
        entityUuid = "uuid-${opId.hashCode()}",
        opType = "Upsert",
        payload = null,
        updatedAt = System.currentTimeMillis(),
        syncedToRemote = syncedToRemote,
        appliedFromRemote = appliedFromRemote,
    )
}
