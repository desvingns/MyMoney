package com.kshavrin.mymoney.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kshavrin.mymoney.core.database.entity.OperationEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OperationDaoTest {
    private lateinit var db: MoneyDatabase

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    MoneyDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ─── builders ──────────────────────────────────────────────────────────

    private fun op(
        opId: String = "op-1",
        deviceId: String = "device-a",
        entityKind: String = "transaction",
        entityUuid: String = "entity-uuid-1",
        opType: String = "CREATE",
        payload: String? = null,
        updatedAt: Long = 1000L,
        syncedToRemote: Boolean = false,
        appliedFromRemote: Boolean = false,
    ) = OperationEntity(
        opId = opId,
        deviceId = deviceId,
        entityKind = entityKind,
        entityUuid = entityUuid,
        opType = opType,
        payload = payload,
        updatedAt = updatedAt,
        syncedToRemote = syncedToRemote,
        appliedFromRemote = appliedFromRemote,
    )

    // ─── insert ────────────────────────────────────────────────────────────

    @Test
    fun `insert stores op and it appears in knownOpIds`() =
        runTest {
            db.operationDao().insert(op(opId = "op-abc"))
            val ids = db.operationDao().knownOpIds()
            assertTrue("knownOpIds must contain the inserted opId", ids.contains("op-abc"))
        }

    @Test
    fun `insert with same opId is ignored and does not throw`() =
        runTest {
            db.operationDao().insert(op(opId = "op-dup", opType = "CREATE"))
            db.operationDao().insert(op(opId = "op-dup", opType = "UPDATE"))
            val ids = db.operationDao().knownOpIds()
            assertEquals("duplicate insert must be ignored — only one entry", 1, ids.count { it == "op-dup" })
        }

    // ─── insertAll ─────────────────────────────────────────────────────────

    @Test
    fun `insertAll stores all ops in empty table`() =
        runTest {
            val ops =
                listOf(
                    op(opId = "op-1", entityUuid = "e1"),
                    op(opId = "op-2", entityUuid = "e2"),
                    op(opId = "op-3", entityUuid = "e3"),
                )
            db.operationDao().insertAll(ops)
            val ids = db.operationDao().knownOpIds()
            assertEquals("insertAll must store exactly 3 ops", 3, ids.size)
            assertTrue(ids.containsAll(listOf("op-1", "op-2", "op-3")))
        }

    @Test
    fun `insertAll with duplicate opIds ignores duplicates`() =
        runTest {
            db.operationDao().insert(op(opId = "op-existing"))
            db.operationDao().insertAll(
                listOf(
                    op(opId = "op-existing", opType = "UPDATE"),
                    op(opId = "op-new"),
                ),
            )
            val ids = db.operationDao().knownOpIds()
            assertEquals("only 2 distinct ops must exist after insertAll with one duplicate", 2, ids.size)
        }

    @Test
    fun `insertAll with empty list does not throw and table stays empty`() =
        runTest {
            db.operationDao().insertAll(emptyList())
            val ids = db.operationDao().knownOpIds()
            assertTrue("table must remain empty after insertAll with empty list", ids.isEmpty())
        }

    // ─── unsyncedLocal ─────────────────────────────────────────────────────

    @Test
    fun `unsyncedLocal returns ops with syncedToRemote false and appliedFromRemote false`() =
        runTest {
            db.operationDao().insert(op(opId = "local-unsynced", syncedToRemote = false, appliedFromRemote = false))
            val result = db.operationDao().unsyncedLocal()
            assertEquals("unsyncedLocal must return the locally-created unsynced op", 1, result.size)
            assertEquals("op-id must match", "local-unsynced", result.first().opId)
        }

    @Test
    fun `unsyncedLocal excludes ops already synced to remote`() =
        runTest {
            db.operationDao().insert(op(opId = "synced", syncedToRemote = true, appliedFromRemote = false))
            val result = db.operationDao().unsyncedLocal()
            assertTrue("unsyncedLocal must not return ops that are already synced", result.isEmpty())
        }

    @Test
    fun `unsyncedLocal excludes ops applied from remote`() =
        runTest {
            db.operationDao().insert(op(opId = "remote-applied", syncedToRemote = false, appliedFromRemote = true))
            val result = db.operationDao().unsyncedLocal()
            assertTrue("unsyncedLocal must not return ops applied from remote", result.isEmpty())
        }

    @Test
    fun `unsyncedLocal excludes ops that are both synced and applied`() =
        runTest {
            db.operationDao().insert(op(opId = "both", syncedToRemote = true, appliedFromRemote = true))
            val result = db.operationDao().unsyncedLocal()
            assertTrue("unsyncedLocal must not return ops that are both synced and applied", result.isEmpty())
        }

    @Test
    fun `unsyncedLocal returns results ordered by updated_at ascending`() =
        runTest {
            db.operationDao().insertAll(
                listOf(
                    op(opId = "op-late", updatedAt = 300L),
                    op(opId = "op-early", updatedAt = 100L),
                    op(opId = "op-mid", updatedAt = 200L),
                ),
            )
            val result = db.operationDao().unsyncedLocal()
            assertEquals("unsyncedLocal must order by updated_at ASC", listOf("op-early", "op-mid", "op-late"), result.map { it.opId })
        }

    @Test
    fun `unsyncedLocal returns empty list when all ops are synced`() =
        runTest {
            db.operationDao().insertAll(
                listOf(
                    op(opId = "a", syncedToRemote = true),
                    op(opId = "b", appliedFromRemote = true),
                ),
            )
            val result = db.operationDao().unsyncedLocal()
            assertTrue("unsyncedLocal must be empty when no local-only ops remain", result.isEmpty())
        }

    // ─── markSynced ────────────────────────────────────────────────────────

    @Test
    fun `markSynced sets syncedToRemote true for given opIds`() =
        runTest {
            db.operationDao().insertAll(
                listOf(
                    op(opId = "op-a"),
                    op(opId = "op-b"),
                    op(opId = "op-c"),
                ),
            )
            db.operationDao().markSynced(listOf("op-a", "op-c"))
            val unsynced = db.operationDao().unsyncedLocal()
            assertEquals("only op-b must remain unsynced", 1, unsynced.size)
            assertEquals("op-b", unsynced.first().opId)
        }

    @Test
    fun `markSynced with unknown opId does not throw`() =
        runTest {
            db.operationDao().insert(op(opId = "real-op"))
            db.operationDao().markSynced(listOf("non-existent"))
            val unsynced = db.operationDao().unsyncedLocal()
            assertEquals("real-op must remain unsynced when markSynced targeted a non-existent id", 1, unsynced.size)
        }

    @Test
    fun `markSynced with empty list is a no-op`() =
        runTest {
            db.operationDao().insert(op(opId = "untouched"))
            db.operationDao().markSynced(emptyList())
            val unsynced = db.operationDao().unsyncedLocal()
            assertEquals("op must remain unsynced after markSynced with empty list", 1, unsynced.size)
        }

    // ─── knownOpIds ────────────────────────────────────────────────────────

    @Test
    fun `knownOpIds returns empty list when table is empty`() =
        runTest {
            val ids = db.operationDao().knownOpIds()
            assertTrue("knownOpIds must be empty for fresh table", ids.isEmpty())
        }

    @Test
    fun `knownOpIds returns all inserted opIds`() =
        runTest {
            val expected = listOf("id-x", "id-y", "id-z")
            db.operationDao().insertAll(expected.map { op(opId = it) })
            val ids = db.operationDao().knownOpIds()
            assertEquals("knownOpIds count must match inserted count", expected.size, ids.size)
            assertTrue("knownOpIds must contain all inserted ids", ids.containsAll(expected))
        }

    // ─── existsByOpId ──────────────────────────────────────────────────────

    @Test
    fun `existsByOpId returns true for inserted op`() =
        runTest {
            db.operationDao().insert(op(opId = "exists-op"))
            assertTrue("existsByOpId must return true for a stored op", db.operationDao().existsByOpId("exists-op"))
        }

    @Test
    fun `existsByOpId returns false for unknown opId`() =
        runTest {
            assertFalse("existsByOpId must return false when no op with that id exists", db.operationDao().existsByOpId("ghost-op"))
        }

    @Test
    fun `existsByOpId returns false after table is empty`() =
        runTest {
            assertFalse("existsByOpId must return false on empty table", db.operationDao().existsByOpId("any-id"))
        }

    // ─── opsForEntity ──────────────────────────────────────────────────────

    @Test
    fun `opsForEntity returns only ops matching given entityUuid`() =
        runTest {
            db.operationDao().insertAll(
                listOf(
                    op(opId = "op-e1-1", entityUuid = "entity-1"),
                    op(opId = "op-e1-2", entityUuid = "entity-1"),
                    op(opId = "op-e2-1", entityUuid = "entity-2"),
                ),
            )
            val result = db.operationDao().opsForEntity("entity-1")
            assertEquals("opsForEntity must return ops for entity-1 only", 2, result.size)
            assertTrue(result.all { it.entityUuid == "entity-1" })
        }

    @Test
    fun `opsForEntity returns empty list when no ops exist for entity`() =
        runTest {
            db.operationDao().insert(op(opId = "op-other", entityUuid = "other-entity"))
            val result = db.operationDao().opsForEntity("missing-entity")
            assertTrue("opsForEntity must return empty list when no ops match", result.isEmpty())
        }

    @Test
    fun `opsForEntity returns results ordered by updated_at ascending`() =
        runTest {
            db.operationDao().insertAll(
                listOf(
                    op(opId = "op-late", entityUuid = "entity-1", updatedAt = 3000L),
                    op(opId = "op-early", entityUuid = "entity-1", updatedAt = 1000L),
                    op(opId = "op-mid", entityUuid = "entity-1", updatedAt = 2000L),
                ),
            )
            val result = db.operationDao().opsForEntity("entity-1")
            assertEquals(
                "opsForEntity must order by updated_at ASC",
                listOf("op-early", "op-mid", "op-late"),
                result.map { it.opId },
            )
        }

    @Test
    fun `opsForEntity returns single op when only one matches`() =
        runTest {
            db.operationDao().insert(op(opId = "sole-op", entityUuid = "solo-entity"))
            val result = db.operationDao().opsForEntity("solo-entity")
            assertEquals("opsForEntity must return exactly 1 op", 1, result.size)
            assertEquals("sole-op", result.first().opId)
        }

    // ─── payload field ─────────────────────────────────────────────────────

    @Test
    fun `insert stores null payload and read back returns null`() =
        runTest {
            db.operationDao().insert(op(opId = "null-payload-op", payload = null))
            val result = db.operationDao().opsForEntity("entity-uuid-1")
            assertEquals(1, result.size)
            assertEquals("payload must be null when inserted as null", null, result.first().payload)
        }

    @Test
    fun `insert stores non-null payload and read back returns it intact`() =
        runTest {
            val json = """{"amount":"1500.00","currency":"RUB"}"""
            db.operationDao().insert(op(opId = "payload-op", payload = json))
            val result = db.operationDao().opsForEntity("entity-uuid-1")
            assertEquals(1, result.size)
            assertEquals("payload must survive round-trip unchanged", json, result.first().payload)
        }
}
