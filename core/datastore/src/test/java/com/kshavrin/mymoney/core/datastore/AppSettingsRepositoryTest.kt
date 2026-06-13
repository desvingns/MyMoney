package com.kshavrin.mymoney.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AppSettingsRepositoryTest {
    private lateinit var tempFile: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: AppSettingsRepository

    @Before
    fun setUp() {
        tempFile = Files.createTempFile("test_settings", ".preferences_pb").toFile()
        tempFile.delete()
        dataStore =
            PreferenceDataStoreFactory.create(
                produceFile = { tempFile },
            )
        repository = AppSettingsRepositoryImpl(dataStore)
    }

    @After
    fun tearDown() {
        tempFile.delete()
    }

    @Test
    fun defaults_are_returned_when_no_data_persisted() =
        runTest(UnconfinedTestDispatcher()) {
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
            assertEquals(null, settings.tzNormalizedAt)
        }

    @Test
    fun round_trip_all_fields() =
        runTest(UnconfinedTestDispatcher()) {
            val target =
                AppSettings(
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
                    tzNormalizedAt = 1700000003000L,
                )
            repository.update { target }
            val read = repository.settings.first()
            assertEquals(target, read)
        }

    @Test
    fun first_positive_seen_is_monotonic() =
        runTest(UnconfinedTestDispatcher()) {
            repository.update { it.copy(firstPositiveSeen = true) }
            try {
                repository.update { it.copy(firstPositiveSeen = false) }
                fail("firstPositiveSeen should not flip from true to false")
            } catch (_: IllegalStateException) {
            }
        }

    @Test
    fun update_transform_preserves_changes_committed_before_edit_applies() =
        runTest {
            val interleavedDataStore = InterleavedDataStore()
            val interleavedRepository = AppSettingsRepositoryImpl(interleavedDataStore)
            interleavedDataStore.beforeNextUpdate = { prefs ->
                prefs.copySettings { it.copy(themeMode = "dark") }
            }

            interleavedRepository.update {
                it.copy(defaultAccountId = 5L)
            }

            val read = interleavedRepository.settings.first()
            assertEquals(5L, read.defaultAccountId)
            assertEquals("dark", read.themeMode)
        }

    @Test
    fun account_update_does_not_restore_import_focus_cleared_before_edit_applies() =
        runTest {
            val interleavedDataStore = InterleavedDataStore()
            val interleavedRepository = AppSettingsRepositoryImpl(interleavedDataStore)
            interleavedRepository.update {
                it.copy(
                    defaultAccountId = -1L,
                    dashboardSelectionMode = "specific_account",
                    importFocusEpochMs = 1700000000000L,
                    importFocusCurrencyId = 9L,
                )
            }
            interleavedDataStore.beforeNextUpdate = { prefs ->
                prefs.copySettings { it.copy(importFocusEpochMs = 0L, importFocusCurrencyId = -1L) }
            }

            interleavedRepository.update {
                it.copy(defaultAccountId = 5L, dashboardSelectionMode = "specific_account")
            }

            val read = interleavedRepository.settings.first()
            assertEquals(5L, read.defaultAccountId)
            assertEquals("specific_account", read.dashboardSelectionMode)
            assertEquals(0L, read.importFocusEpochMs)
            assertEquals(-1L, read.importFocusCurrencyId)
        }

    @Test
    fun null_clearing_for_optional_timestamps_and_timezone_normalization_flag() =
        runTest(UnconfinedTestDispatcher()) {
            repository.update { it.copy(onboardingCompletedAt = 123L, lastSyncAt = 456L, tzNormalizedAt = 789L) }
            repository.update { it.copy(onboardingCompletedAt = null, lastSyncAt = null, tzNormalizedAt = null) }
            val read = repository.settings.first()
            assertEquals(null, read.onboardingCompletedAt)
            assertEquals(null, read.lastSyncAt)
            assertEquals(null, read.tzNormalizedAt)
        }

    @Test
    fun `reset clears stored settings including the monotonic flag`() =
        runTest(UnconfinedTestDispatcher()) {
            repository.update {
                AppSettings(
                    language = "ru",
                    themeMode = "dark",
                    biometricLockEnabled = true,
                    lastSyncAt = 456L,
                    autoSyncEnabled = false,
                    firstPositiveSeen = true,
                    tzNormalizedAt = 789L,
                )
            }

            repository.reset()

            assertEquals(AppSettings(), repository.settings.first())
        }

    private fun Preferences.copySettings(transform: (AppSettings) -> AppSettings): Preferences =
        mutablePreferencesOf().also { prefs ->
            transform(toAppSettings()).writeTo(prefs)
        }

    private class InterleavedDataStore : DataStore<Preferences> {
        private val values = MutableStateFlow<Preferences>(mutablePreferencesOf())
        var beforeNextUpdate: ((Preferences) -> Preferences)? = null

        override val data: Flow<Preferences> = values

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val current = beforeNextUpdate?.invoke(values.value) ?: values.value
            beforeNextUpdate = null
            val next = transform(current)
            values.value = next
            return next
        }
    }
}
