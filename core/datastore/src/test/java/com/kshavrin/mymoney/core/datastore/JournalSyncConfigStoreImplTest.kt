package com.kshavrin.mymoney.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class JournalSyncConfigStoreImplTest {
    private lateinit var tempFile: File
    private lateinit var store: JournalSyncConfigStoreImpl

    @Before
    fun setUp() {
        tempFile = Files.createTempFile("test_journal_config", ".preferences_pb").toFile()
        tempFile.delete()
        val dataStore =
            PreferenceDataStoreFactory.create(
                produceFile = { tempFile },
            )
        store = JournalSyncConfigStoreImpl(dataStore)
    }

    @After
    fun tearDown() {
        tempFile.delete()
    }

    // ─── folderId ─────────────────────────────────────────────────────────────

    @Test
    fun `folderId returns empty string when not set`() =
        runTest(UnconfinedTestDispatcher()) {
            assertEquals("", store.folderId())
        }

    @Test
    fun `folderId round-trips after setFolderId`() =
        runTest(UnconfinedTestDispatcher()) {
            store.setFolderId("drive-folder-abc123")
            assertEquals("drive-folder-abc123", store.folderId())
        }

    @Test
    fun `folderId is overwritten by a second setFolderId call`() =
        runTest(UnconfinedTestDispatcher()) {
            store.setFolderId("folder-v1")
            store.setFolderId("folder-v2")
            assertEquals("folder-v2", store.folderId())
        }

    @Test
    fun `folderId blank after setFolderId with empty string`() =
        runTest(UnconfinedTestDispatcher()) {
            store.setFolderId("folder-set")
            store.setFolderId("")
            assertTrue("folderId must be blank after explicit empty-string write", store.folderId().isBlank())
        }

    // ─── peerHighWaterMs ──────────────────────────────────────────────────────

    @Test
    fun `peerHighWaterMs returns zero for unknown fileId`() =
        runTest(UnconfinedTestDispatcher()) {
            assertEquals(0L, store.peerHighWaterMs("ghost-file-id"))
        }

    @Test
    fun `peerHighWaterMs round-trips after setPeerHighWaterMs`() =
        runTest(UnconfinedTestDispatcher()) {
            store.setPeerHighWaterMs("file-a", 1_700_000_100_000L)
            assertEquals(1_700_000_100_000L, store.peerHighWaterMs("file-a"))
        }

    @Test
    fun `setPeerHighWaterMs advances when new value is greater`() =
        runTest(UnconfinedTestDispatcher()) {
            store.setPeerHighWaterMs("file-b", 100L)
            store.setPeerHighWaterMs("file-b", 200L)
            assertEquals(200L, store.peerHighWaterMs("file-b"))
        }

    @Test
    fun `setPeerHighWaterMs does not retreat when new value is smaller`() =
        runTest(UnconfinedTestDispatcher()) {
            store.setPeerHighWaterMs("file-c", 500L)
            store.setPeerHighWaterMs("file-c", 100L)
            assertEquals("high water must not retreat to a smaller value", 500L, store.peerHighWaterMs("file-c"))
        }

    @Test
    fun `peerHighWaterMs tracks distinct fileIds independently`() =
        runTest(UnconfinedTestDispatcher()) {
            store.setPeerHighWaterMs("file-x", 111L)
            store.setPeerHighWaterMs("file-y", 999L)
            assertEquals(111L, store.peerHighWaterMs("file-x"))
            assertEquals(999L, store.peerHighWaterMs("file-y"))
        }

    // ─── bootstrapDone ────────────────────────────────────────────────────────

    @Test
    fun `isBootstrapDone returns false when never marked`() =
        runTest(UnconfinedTestDispatcher()) {
            assertFalse(store.isBootstrapDone())
        }

    @Test
    fun `isBootstrapDone returns true after markBootstrapDone`() =
        runTest(UnconfinedTestDispatcher()) {
            store.markBootstrapDone()
            assertTrue(store.isBootstrapDone())
        }

    @Test
    fun `markBootstrapDone is idempotent — calling twice does not throw`() =
        runTest(UnconfinedTestDispatcher()) {
            store.markBootstrapDone()
            store.markBootstrapDone()
            assertTrue(store.isBootstrapDone())
        }
}
