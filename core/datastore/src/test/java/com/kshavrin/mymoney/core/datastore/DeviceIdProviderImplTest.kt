package com.kshavrin.mymoney.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DeviceIdProviderImplTest {
    private val tempFile =
        Files
            .createTempFile("device_id", ".preferences_pb")
            .toFile()
            .apply { delete() }
    private var activeJob: Job? = null

    @After
    fun tearDown() =
        runTest {
            activeJob?.cancelAndJoin()
            activeJob = null
            tempFile.delete()
        }

    @Test
    fun `deviceId persists one generated install id and reuses it across provider instances`() =
        runTest {
            val firstJob = Job()
            activeJob = firstJob
            val firstStore = createStore(firstJob)
            val firstProvider = DeviceIdProviderImpl(firstStore)

            val first = firstProvider.deviceId()
            val second = firstProvider.deviceId()

            assertEquals(first, second)
            assertEquals(first, firstStore.data.first()[AppSettingsKeys.DEVICE_ID])
            assertTrue(first.matches(Regex("^[0-9a-fA-F-]{36}$")))

            firstJob.cancelAndJoin()
            activeJob = null

            val secondJob = Job()
            activeJob = secondJob
            val secondStore = createStore(secondJob)
            val secondProvider = DeviceIdProviderImpl(secondStore)

            assertEquals(first, secondProvider.deviceId())
            assertEquals(first, secondStore.data.first()[AppSettingsKeys.DEVICE_ID])
        }

    private fun createStore(job: Job): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(job + Dispatchers.IO),
            produceFile = { tempFile },
        )
}
