package com.kshavrin.mymoney.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AppSettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data
        .map { it.toAppSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        val current = settings.first()
        val next = transform(current)
        if (current.firstPositiveSeen && !next.firstPositiveSeen) {
            throw IllegalStateException("firstPositiveSeen is monotonic — cannot flip true to false")
        }
        dataStore.edit { prefs ->
            next.writeTo(prefs)
        }
    }

    override suspend fun reset() {
        dataStore.edit { prefs -> prefs.clear() }
    }
}

internal fun Preferences.toAppSettings(): AppSettings = AppSettings(
    language = this[AppSettingsKeys.LANGUAGE] ?: "system",
    themeMode = this[AppSettingsKeys.THEME_MODE] ?: "system",
    biometricLockEnabled = this[AppSettingsKeys.BIOMETRIC_LOCK_ENABLED] ?: false,
    biometricIdleTimeoutSec = this[AppSettingsKeys.BIOMETRIC_IDLE_TIMEOUT_SEC] ?: 60,
    soundEnabled = this[AppSettingsKeys.SOUND_ENABLED] ?: true,
    hapticEnabled = this[AppSettingsKeys.HAPTIC_ENABLED] ?: true,
    defaultAccountId = this[AppSettingsKeys.DEFAULT_ACCOUNT_ID] ?: -1L,
    dashboardSelectionMode = this[AppSettingsKeys.DASHBOARD_SELECTION_MODE] ?: "specific_account",
    defaultPeriod = this[AppSettingsKeys.DEFAULT_PERIOD] ?: "month",
    dateFirstDayOfWeek = this[AppSettingsKeys.DATE_FIRST_DAY_OF_WEEK] ?: 1,
    currencySymbolPosition = this[AppSettingsKeys.CURRENCY_SYMBOL_POSITION] ?: "before",
    onboardingCompletedAt = this[AppSettingsKeys.ONBOARDING_COMPLETED_AT],
    lastSyncAt = this[AppSettingsKeys.LAST_SYNC_AT],
    autoSyncEnabled = this[AppSettingsKeys.AUTO_SYNC_ENABLED] ?: true,
    budgetModeEnabled = this[AppSettingsKeys.BUDGET_MODE_ENABLED] ?: true,
    firstPositiveSeen = this[AppSettingsKeys.FIRST_POSITIVE_SEEN] ?: false,
    importFocusEpochMs = this[AppSettingsKeys.IMPORT_FOCUS_EPOCH_MS] ?: 0L,
    importFocusCurrencyId = this[AppSettingsKeys.IMPORT_FOCUS_CURRENCY_ID] ?: -1L,
    tzNormalizedAt = this[AppSettingsKeys.TZ_NORMALIZED_AT],
)

internal fun AppSettings.writeTo(prefs: androidx.datastore.preferences.core.MutablePreferences) {
    prefs[AppSettingsKeys.LANGUAGE] = language
    prefs[AppSettingsKeys.THEME_MODE] = themeMode
    prefs[AppSettingsKeys.BIOMETRIC_LOCK_ENABLED] = biometricLockEnabled
    prefs[AppSettingsKeys.BIOMETRIC_IDLE_TIMEOUT_SEC] = biometricIdleTimeoutSec
    prefs[AppSettingsKeys.SOUND_ENABLED] = soundEnabled
    prefs[AppSettingsKeys.HAPTIC_ENABLED] = hapticEnabled
    prefs[AppSettingsKeys.DEFAULT_ACCOUNT_ID] = defaultAccountId
    prefs[AppSettingsKeys.DASHBOARD_SELECTION_MODE] = dashboardSelectionMode
    prefs[AppSettingsKeys.DEFAULT_PERIOD] = defaultPeriod
    prefs[AppSettingsKeys.DATE_FIRST_DAY_OF_WEEK] = dateFirstDayOfWeek
    prefs[AppSettingsKeys.CURRENCY_SYMBOL_POSITION] = currencySymbolPosition
    if (onboardingCompletedAt != null) {
        prefs[AppSettingsKeys.ONBOARDING_COMPLETED_AT] = onboardingCompletedAt
    } else {
        prefs.remove(AppSettingsKeys.ONBOARDING_COMPLETED_AT)
    }
    if (lastSyncAt != null) {
        prefs[AppSettingsKeys.LAST_SYNC_AT] = lastSyncAt
    } else {
        prefs.remove(AppSettingsKeys.LAST_SYNC_AT)
    }
    prefs[AppSettingsKeys.AUTO_SYNC_ENABLED] = autoSyncEnabled
    prefs[AppSettingsKeys.BUDGET_MODE_ENABLED] = budgetModeEnabled
    prefs[AppSettingsKeys.FIRST_POSITIVE_SEEN] = firstPositiveSeen
    prefs[AppSettingsKeys.IMPORT_FOCUS_EPOCH_MS] = importFocusEpochMs
    prefs[AppSettingsKeys.IMPORT_FOCUS_CURRENCY_ID] = importFocusCurrencyId
    if (tzNormalizedAt != null) {
        prefs[AppSettingsKeys.TZ_NORMALIZED_AT] = tzNormalizedAt
    } else {
        prefs.remove(AppSettingsKeys.TZ_NORMALIZED_AT)
    }
}
