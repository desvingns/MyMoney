package com.kshavrin.mymoney.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class JournalSyncConfigStoreImplTest {
    private lateinit var file: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: JournalSyncConfigStoreImpl

    @Before
    fun setUp() {
        file = Files.createTempFile("journal-config", ".preferences_pb").toFile().also { it.delete() }
        dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        store = JournalSyncConfigStoreImpl(dataStore)
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun `binding round trips and one active provider account is retained`() =
        runTest {
            val binding = CloudBinding(CloudProvider.GoogleDrive, "permission-123", "me@example.com")
            store.setBinding(binding)
            assertEquals(binding, store.binding())
        }

    @Test
    fun `legacy folder key is ignored`() =
        runTest {
            dataStore.edit { it[stringPreferencesKey("journal_folder_id")] = "old-folder" }
            assertNull(store.binding())
            assertEquals(0L, store.peerHighWaterMs("peer"))
        }

    @Test
    fun `peer high water and bootstrap are scoped by provider and account`() =
        runTest {
            store.setBinding(CloudBinding(CloudProvider.Dropbox, "acct-a", "A"))
            store.setPeerHighWaterMs("peer", 42L)
            store.markBootstrapDone()
            store.setBinding(CloudBinding(CloudProvider.Dropbox, "acct-b", "B"))
            assertEquals(0L, store.peerHighWaterMs("peer"))
            assertFalse(store.isBootstrapDone())
            store.setBinding(CloudBinding(CloudProvider.Dropbox, "acct-a", "A"))
            assertEquals(42L, store.peerHighWaterMs("peer"))
            assertTrue(store.isBootstrapDone())
        }

    @Test
    fun `clear binding clears active scoped state`() =
        runTest {
            store.setBinding(CloudBinding(CloudProvider.GoogleDrive, "acct", "A"))
            store.setPeerHighWaterMs("peer", 99L)
            store.markBootstrapDone()
            store.clearBinding()
            assertNull(store.binding())
            assertEquals(0L, store.peerHighWaterMs("peer"))
            assertFalse(store.isBootstrapDone())
        }

    @Test
    fun `clear removes all journal state`() =
        runTest {
            store.setBinding(CloudBinding(CloudProvider.Dropbox, "acct", "A"))
            store.setPeerHighWaterMs("peer", 7L)
            store.markBootstrapDone()
            store.clear()
            assertNull(store.binding())
            assertEquals(0L, store.peerHighWaterMs("peer"))
            assertFalse(store.isBootstrapDone())
        }
}
