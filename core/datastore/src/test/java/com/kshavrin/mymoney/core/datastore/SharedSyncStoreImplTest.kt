package com.kshavrin.mymoney.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SharedSyncStoreImplTest {
    private lateinit var file: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: SharedSyncStoreImpl

    @Before
    fun setUp() {
        file = Files.createTempFile("shared-sync", ".preferences_pb").toFile().also { it.delete() }
        dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        store = SharedSyncStoreImpl(dataStore)
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun `defaults are inactive with a zero cursor`() =
        runTest {
            assertEquals(0L, store.cursor())
            assertFalse(store.isMembershipActive())
        }

    @Test
    fun `cursor only advances for a higher sequence`() =
        runTest {
            store.setCursor(42L)
            store.setCursor(17L)
            assertEquals(42L, store.cursor())

            store.setCursor(42L)
            assertEquals(42L, store.cursor())

            store.setCursor(99L)
            assertEquals(99L, store.cursor())
        }

    @Test
    fun `membership can be toggled and clear resets shared state only`() =
        runTest {
            val unrelatedKey = stringPreferencesKey("unrelated_preference")
            dataStore.edit { prefs -> prefs[unrelatedKey] = "keep" }

            store.setMembershipActive(true)
            assertTrue(store.isMembershipActive())
            store.setMembershipActive(false)
            assertFalse(store.isMembershipActive())

            store.setCursor(7L)
            store.setMembershipActive(true)
            store.clear()

            assertEquals(0L, store.cursor())
            assertFalse(store.isMembershipActive())
            assertEquals("keep", dataStore.data.first()[unrelatedKey])
        }
}
