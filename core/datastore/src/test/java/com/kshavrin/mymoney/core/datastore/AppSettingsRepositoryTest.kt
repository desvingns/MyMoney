package com.kshavrin.mymoney.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AppSettingsRepositoryTest {

    private lateinit var tempFile: File
    private lateinit var repository: AppSettingsRepository

    @Before
    fun setUp() {
        tempFile = Files.createTempFile("test_settings", ".preferences_pb").toFile()
        tempFile.delete()
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { tempFile },
        )
        repository = AppSettingsRepositoryImpl(dataStore)
    }

    @After
    fun tearDown() {
        tempFile.delete()
    }

    @Test
    fun defaults_are_returned_when_no_data_persisted() = runTest(UnconfinedTestDispatcher()) {
        val settings = repository.settings.first()
        assertEquals("system", settings.language)
        assertEquals("system", settings.themeMode)
        assertEquals(false, settings.biometricLockEnabled)
        assertEquals(60, settings.biometricIdleTimeoutSec)
        assertEquals(true, settings.soundEnabled)
        assertEquals(true, settings.hapticEnabled)
        assertEquals(-1L, settings.defaultAccountId)
        assertEquals("specific_account", settings.dashboardSelectionMode)
        assertEquals("month", settings.defaultPeriod)
        assertEquals(1, settings.dateFirstDayOfWeek)
        assertEquals("before", settings.currencySymbolPosition)
        assertEquals(null, settings.onboardingCompletedAt)
        assertEquals(null, settings.lastSyncAt)
        assertEquals(true, settings.autoSyncEnabled)
        assertEquals(true, settings.budgetModeEnabled)
        assertEquals(false, settings.firstPositiveSeen)
        assertEquals(0L, settings.importFocusEpochMs)
        assertEquals(-1L, settings.importFocusCurrencyId)
    }

    @Test
    fun round_trip_all_fields() = runTest(UnconfinedTestDispatcher()) {
        val target = AppSettings(
            language = "en",
            themeMode = "dark",
            biometricLockEnabled = true,
            biometricIdleTimeoutSec = 120,
            soundEnabled = false,
            hapticEnabled = false,
            defaultAccountId = 42L,
            dashboardSelectionMode = "all_accounts",
            defaultPeriod = "week",
            dateFirstDayOfWeek = 7,
            currencySymbolPosition = "after",
            onboardingCompletedAt = 1700000000000L,
            lastSyncAt = 1700000001000L,
            autoSyncEnabled = false,
            budgetModeEnabled = false,
            firstPositiveSeen = true,
            importFocusEpochMs = 1700000002000L,
            importFocusCurrencyId = 9L,
        )
        repository.update { target }
        val read = repository.settings.first()
        assertEquals(target, read)
    }

    @Test
    fun first_positive_seen_is_monotonic() = runTest(UnconfinedTestDispatcher()) {
        repository.update { it.copy(firstPositiveSeen = true) }
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                repository.update { it.copy(firstPositiveSeen = false) }
            }
        }
    }

    @Test
    fun null_clearing_for_optional_timestamps() = runTest(UnconfinedTestDispatcher()) {
        repository.update { it.copy(onboardingCompletedAt = 123L, lastSyncAt = 456L) }
        repository.update { it.copy(onboardingCompletedAt = null, lastSyncAt = null) }
        val read = repository.settings.first()
        assertEquals(null, read.onboardingCompletedAt)
        assertEquals(null, read.lastSyncAt)
    }

    @Test
    fun `reset clears stored settings including the monotonic flag`() = runTest(UnconfinedTestDispatcher()) {
        repository.update {
            AppSettings(
                language = "ru",
                themeMode = "dark",
                biometricLockEnabled = true,
                lastSyncAt = 456L,
                autoSyncEnabled = false,
                firstPositiveSeen = true,
            )
        }

        repository.reset()

        assertEquals(AppSettings(), repository.settings.first())
    }
}
