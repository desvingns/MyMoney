package com.kshavrin.mymoney.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.kshavrin.mymoney.core.domain.model.EntitlementState
import com.kshavrin.mymoney.core.domain.model.EntitlementWarning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import java.time.Instant

class DataStoreEntitlementWarningStoreTest {
    private lateinit var file: File
    private val storeJobs = mutableListOf<Job>()

    @Before
    fun setUp() {
        file = Files.createTempFile("entitlement-warning-store", ".preferences_pb").toFile()
        file.delete()
    }

    @After
    fun tearDown() {
        storeJobs.forEach { it.cancel() }
        file.delete()
    }

    @Test
    fun `previousState is null when no state has been persisted`() =
        runTest(UnconfinedTestDispatcher()) {
            val store = DataStoreEntitlementWarningStore(createDataStore())

            assertNull(store.previousState())
        }

    @Test
    fun `previousState returns the value set by setPreviousState`() =
        runTest(UnconfinedTestDispatcher()) {
            val store = DataStoreEntitlementWarningStore(createDataStore())

            store.setPreviousState(EntitlementState.GRACE)

            assertEquals(EntitlementState.GRACE, store.previousState())
        }

    @Test
    fun `setPreviousState overwrites a previously stored state`() =
        runTest(UnconfinedTestDispatcher()) {
            val store = DataStoreEntitlementWarningStore(createDataStore())

            store.setPreviousState(EntitlementState.TRIAL)
            store.setPreviousState(EntitlementState.ACTIVE)

            assertEquals(EntitlementState.ACTIVE, store.previousState())
        }

    @Test
    fun `previousState survives a fresh DataStore instance over the same file`() =
        runTest(UnconfinedTestDispatcher()) {
            val writeJob = Job()
            DataStoreEntitlementWarningStore(createDataStore(writeJob)).setPreviousState(EntitlementState.GRACE)
            writeJob.cancelAndJoin()

            val readJob = Job()
            try {
                val readStore = DataStoreEntitlementWarningStore(createDataStore(readJob))
                assertEquals(EntitlementState.GRACE, readStore.previousState())
            } finally {
                readJob.cancelAndJoin()
            }
        }

    @Test
    fun `wasNotified is false before markNotified is called`() =
        runTest(UnconfinedTestDispatcher()) {
            val store = DataStoreEntitlementWarningStore(createDataStore())
            val expiresAt = Instant.parse("2026-09-01T00:00:00Z")

            assertFalse(store.wasNotified(EntitlementWarning.TRIAL_ENDING_3D, expiresAt))
        }

    @Test
    fun `wasNotified is true after markNotified is called for the same warning and expiresAt`() =
        runTest(UnconfinedTestDispatcher()) {
            val store = DataStoreEntitlementWarningStore(createDataStore())
            val expiresAt = Instant.parse("2026-09-01T00:00:00Z")

            store.markNotified(EntitlementWarning.TRIAL_ENDING_3D, expiresAt)

            assertTrue(store.wasNotified(EntitlementWarning.TRIAL_ENDING_3D, expiresAt))
        }

    @Test
    fun `wasNotified distinguishes different warnings for the same expiresAt`() =
        runTest(UnconfinedTestDispatcher()) {
            val store = DataStoreEntitlementWarningStore(createDataStore())
            val expiresAt = Instant.parse("2026-09-01T00:00:00Z")

            store.markNotified(EntitlementWarning.TRIAL_ENDING_3D, expiresAt)

            assertTrue(store.wasNotified(EntitlementWarning.TRIAL_ENDING_3D, expiresAt))
            assertFalse(store.wasNotified(EntitlementWarning.GRACE_ENTERED, expiresAt))
            assertFalse(store.wasNotified(EntitlementWarning.EXPIRY_IMMINENT_1D, expiresAt))
        }

    @Test
    fun `wasNotified distinguishes different expiresAt values for the same warning`() =
        runTest(UnconfinedTestDispatcher()) {
            val store = DataStoreEntitlementWarningStore(createDataStore())
            val firstExpiry = Instant.parse("2026-09-01T00:00:00Z")
            val secondExpiry = Instant.parse("2026-10-01T00:00:00Z")

            store.markNotified(EntitlementWarning.TRIAL_ENDING_3D, firstExpiry)

            assertTrue(store.wasNotified(EntitlementWarning.TRIAL_ENDING_3D, firstExpiry))
            assertFalse(store.wasNotified(EntitlementWarning.TRIAL_ENDING_3D, secondExpiry))
        }

    @Test
    fun `wasNotified treats null expiresAt as a distinct discriminator`() =
        runTest(UnconfinedTestDispatcher()) {
            val store = DataStoreEntitlementWarningStore(createDataStore())
            val expiresAt = Instant.parse("2026-09-01T00:00:00Z")

            store.markNotified(EntitlementWarning.GRACE_ENTERED, null)

            assertTrue(store.wasNotified(EntitlementWarning.GRACE_ENTERED, null))
            assertFalse(store.wasNotified(EntitlementWarning.GRACE_ENTERED, expiresAt))
        }

    @Test
    fun `markNotified is idempotent for the same pair`() =
        runTest(UnconfinedTestDispatcher()) {
            val store = DataStoreEntitlementWarningStore(createDataStore())
            val expiresAt = Instant.parse("2026-09-01T00:00:00Z")

            store.markNotified(EntitlementWarning.TRIAL_ENDING_3D, expiresAt)
            store.markNotified(EntitlementWarning.TRIAL_ENDING_3D, expiresAt)

            assertTrue(store.wasNotified(EntitlementWarning.TRIAL_ENDING_3D, expiresAt))
        }

    @Test
    fun `notified pair survives a fresh DataStore instance over the same file`() =
        runTest(UnconfinedTestDispatcher()) {
            val expiresAt = Instant.parse("2026-09-01T00:00:00Z")
            val writeJob = Job()
            DataStoreEntitlementWarningStore(createDataStore(writeJob)).markNotified(
                EntitlementWarning.EXPIRY_IMMINENT_1D,
                expiresAt,
            )
            writeJob.cancelAndJoin()

            val readJob = Job()
            try {
                val readStore = DataStoreEntitlementWarningStore(createDataStore(readJob))
                assertTrue(readStore.wasNotified(EntitlementWarning.EXPIRY_IMMINENT_1D, expiresAt))
                assertFalse(readStore.wasNotified(EntitlementWarning.TRIAL_ENDING_3D, expiresAt))
            } finally {
                readJob.cancelAndJoin()
            }
        }

    @Test
    fun `all three warning types can be marked and queried independently`() =
        runTest(UnconfinedTestDispatcher()) {
            val store = DataStoreEntitlementWarningStore(createDataStore())
            val expiresAt = Instant.parse("2026-09-01T00:00:00Z")

            store.markNotified(EntitlementWarning.TRIAL_ENDING_3D, expiresAt)
            store.markNotified(EntitlementWarning.GRACE_ENTERED, expiresAt)
            store.markNotified(EntitlementWarning.EXPIRY_IMMINENT_1D, expiresAt)

            assertTrue(store.wasNotified(EntitlementWarning.TRIAL_ENDING_3D, expiresAt))
            assertTrue(store.wasNotified(EntitlementWarning.GRACE_ENTERED, expiresAt))
            assertTrue(store.wasNotified(EntitlementWarning.EXPIRY_IMMINENT_1D, expiresAt))
        }

    private fun createDataStore(job: Job = Job()): DataStore<Preferences> =
        PreferenceDataStoreFactory
            .create(
                scope = CoroutineScope(job + Dispatchers.IO),
                produceFile = { file },
            ).also { storeJobs += job }
}
