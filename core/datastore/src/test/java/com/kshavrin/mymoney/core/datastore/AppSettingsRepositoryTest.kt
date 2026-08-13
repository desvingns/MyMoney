package com.kshavrin.mymoney.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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
            assertEquals(false, settings.hideAppContentInRecents)
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
            assertEquals(false, settings.supporterBadgeEarned)
            assertEquals(0, settings.supportPurchaseCount)
            assertEquals(0L, settings.importFocusEpochMs)
            assertEquals(-1L, settings.importFocusCurrencyId)
            assertEquals(0L, settings.dashboardPeriodEpochMs)
            assertEquals(null, settings.tzNormalizedAt)
            assertEquals(true, settings.chartVisible)
            assertEquals("smooth_area", settings.chartStyle)
            assertEquals("follow", settings.chartPeriodType)
            assertEquals(5, settings.chartPointCount)
            assertEquals("cumulative", settings.chartMetric)
            assertEquals(true, settings.chartShowGridlines)
            assertEquals(true, settings.chartShowLabels)
            assertEquals("by_sign", settings.chartColorRule)
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
                    hideAppContentInRecents = true,
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
                    supporterBadgeEarned = true,
                    supportPurchaseCount = 3,
                    importFocusEpochMs = 1700000002000L,
                    importFocusCurrencyId = 9L,
                    dashboardPeriodEpochMs = 1772323200000L,
                    tzNormalizedAt = 1700000003000L,
                    chartVisible = false,
                    chartStyle = "bars",
                    chartPeriodType = "fixed_month",
                    chartPointCount = 12,
                    chartMetric = "period_net",
                    chartShowGridlines = false,
                    chartShowLabels = false,
                    chartColorRule = "fixed",
                )
            repository.update { target }
            val read = repository.settings.first()
            assertEquals(target, read)
        }

    @Test
    fun `chart settings round-trip with non-default values`() =
        runTest(UnconfinedTestDispatcher()) {
            repository.update {
                it.copy(
                    chartStyle = "bars",
                    chartMetric = "period_net",
                    chartVisible = false,
                )
            }
            val read = repository.settings.first()
            assertEquals("bars", read.chartStyle)
            assertEquals("period_net", read.chartMetric)
            assertEquals(false, read.chartVisible)
        }

    @Test
    fun `chart fields use defaults when prefs contain no chart keys`() {
        val legacyPrefs =
            mutablePreferencesOf().also { prefs ->
                prefs[AppSettingsKeys.LANGUAGE] = "ru"
                prefs[AppSettingsKeys.THEME_MODE] = "dark"
            }
        val settings = legacyPrefs.toAppSettings()
        assertEquals(true, settings.chartVisible)
        assertEquals("smooth_area", settings.chartStyle)
        assertEquals("follow", settings.chartPeriodType)
        assertEquals(5, settings.chartPointCount)
        assertEquals("cumulative", settings.chartMetric)
        assertEquals(true, settings.chartShowGridlines)
        assertEquals(true, settings.chartShowLabels)
        assertEquals("by_sign", settings.chartColorRule)
        assertEquals(false, settings.supporterBadgeEarned)
        assertEquals(0, settings.supportPurchaseCount)
    }

    @Test
    fun `supporter fields are persisted in DataStore preferences`() =
        runTest(UnconfinedTestDispatcher()) {
            repository.update {
                it.copy(
                    supporterBadgeEarned = true,
                    supportPurchaseCount = 7,
                )
            }

            val preferences = dataStore.data.first()

            assertEquals(true, preferences[AppSettingsKeys.SUPPORTER_BADGE_EARNED])
            assertEquals(7, preferences[AppSettingsKeys.SUPPORT_PURCHASE_COUNT])
        }

    // Regression for the cold-start-only empty-dashboard bug: a Monefy import into a past month
    // showed in-session but the dashboard was empty after a real process restart, because the
    // selected period lived only in transient state and snapped back to the current month. The
    // fix persists the period anchor. This drives a genuine disk round-trip across two separate
    // DataStore instances over the SAME file (the second instance models the cold-started
    // process) — not a single reused in-memory store, which would prove nothing about restart.
    @Test
    fun dashboard_period_anchor_survives_a_real_disk_round_trip_across_datastore_instances() =
        runTest(UnconfinedTestDispatcher()) {
            val marchAnchorMs = 1772323200000L

            // First "process": write the anchor, then cancel-and-join the scope to release the
            // file lock. DataStore forbids two live instances over one file, so fully tearing the
            // first one down models the real process death that the previous regression test never
            // exercised (it reused a single store, proving nothing about a disk round-trip).
            val writeJob = Job()
            val writeScope = CoroutineScope(writeJob + Dispatchers.IO)
            val writeStore = PreferenceDataStoreFactory.create(scope = writeScope, produceFile = { tempFile })
            AppSettingsRepositoryImpl(writeStore).update {
                it.copy(
                    dashboardPeriodEpochMs = marchAnchorMs,
                    importFocusEpochMs = 0L,
                    importFocusCurrencyId = -1L,
                    hideAppContentInRecents = true,
                )
            }
            writeJob.cancelAndJoin()

            // Second "process": a fresh DataStore over the SAME file reads back from disk.
            val coldStartJob = Job()
            val coldStartScope = CoroutineScope(coldStartJob + Dispatchers.IO)
            try {
                val coldStartStore = PreferenceDataStoreFactory.create(scope = coldStartScope, produceFile = { tempFile })
                val coldStartSettings = AppSettingsRepositoryImpl(coldStartStore).settings.first()

                assertEquals(marchAnchorMs, coldStartSettings.dashboardPeriodEpochMs)
                assertEquals(0L, coldStartSettings.importFocusEpochMs)
                assertEquals(true, coldStartSettings.hideAppContentInRecents)
            } finally {
                coldStartJob.cancelAndJoin()
            }
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
    fun `supporter badge cannot be reset from true to false`() =
        runTest(UnconfinedTestDispatcher()) {
            repository.update {
                it.copy(
                    supporterBadgeEarned = true,
                    supportPurchaseCount = 2,
                )
            }

            try {
                repository.update { it.copy(supporterBadgeEarned = false) }
                fail("supporterBadgeEarned should not flip from true to false")
            } catch (_: IllegalStateException) {
            }

            val settings = repository.settings.first()
            assertEquals(true, settings.supporterBadgeEarned)
            assertEquals(2, settings.supportPurchaseCount)
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
    fun `reset clears stored settings while preserving device id and supporter state`() =
        runTest(UnconfinedTestDispatcher()) {
            val preservedDeviceId = DeviceIdProviderImpl(dataStore).deviceId()
            repository.update {
                AppSettings(
                    language = "ru",
                    themeMode = "dark",
                    biometricLockEnabled = true,
                    hideAppContentInRecents = true,
                    lastSyncAt = 456L,
                    autoSyncEnabled = false,
                    firstPositiveSeen = true,
                    supporterBadgeEarned = true,
                    supportPurchaseCount = 4,
                    tzNormalizedAt = 789L,
                )
            }

            repository.reset()

            assertEquals(
                AppSettings(supporterBadgeEarned = true, supportPurchaseCount = 4),
                repository.settings.first(),
            )
            assertEquals(preservedDeviceId, dataStore.data.first()[AppSettingsKeys.DEVICE_ID])
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
