package com.kshavrin.mymoney.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.datastore.model.VersionedAppSettings
import com.kshavrin.mymoney.core.datastore.supporter.SupporterPurchaseStoreKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSettingsRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : AppSettingsRepository {
        override val versionedSettings: Flow<VersionedAppSettings> =
            dataStore.data
                .map { preferences ->
                    VersionedAppSettings(
                        settings = preferences.toAppSettings(),
                        revision = preferences[AppSettingsKeys.SETTINGS_REVISION] ?: 0L,
                    )
                }.distinctUntilChanged()

        override val settings: Flow<AppSettings> =
            versionedSettings
                .map { it.settings }
                .distinctUntilChanged()

        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            dataStore.edit { prefs ->
                val current = prefs.toAppSettings()
                val next = transform(current)
                if (current.firstPositiveSeen && !next.firstPositiveSeen) {
                    throw IllegalStateException("firstPositiveSeen is monotonic — cannot flip true to false")
                }
                if (current.supporterBadgeEarned && !next.supporterBadgeEarned) {
                    throw IllegalStateException("supporterBadgeEarned is monotonic - cannot flip true to false")
                }
                next.writeTo(prefs)
                prefs[AppSettingsKeys.SETTINGS_REVISION] =
                    (prefs[AppSettingsKeys.SETTINGS_REVISION] ?: 0L) + 1L
            }
        }

        override suspend fun reset() {
            dataStore.edit { prefs ->
                val deviceId = prefs[AppSettingsKeys.DEVICE_ID]
                val supporterBadgeEarned = prefs[AppSettingsKeys.SUPPORTER_BADGE_EARNED] ?: false
                val supportPurchaseCount = prefs[AppSettingsKeys.SUPPORT_PURCHASE_COUNT] ?: 0
                val supporterPurchaseTokens = prefs[AppSettingsKeys.SUPPORTER_PURCHASE_TOKENS].orEmpty()
                val pendingSupporterPurchases = prefs[SupporterPurchaseStoreKeys.PENDING_PURCHASES]
                val revision = (prefs[AppSettingsKeys.SETTINGS_REVISION] ?: 0L) + 1L
                prefs.clear()
                if (deviceId != null) {
                    prefs[AppSettingsKeys.DEVICE_ID] = deviceId
                }
                prefs[AppSettingsKeys.SUPPORTER_BADGE_EARNED] = supporterBadgeEarned
                prefs[AppSettingsKeys.SUPPORT_PURCHASE_COUNT] = supportPurchaseCount
                prefs[AppSettingsKeys.SUPPORTER_PURCHASE_TOKENS] = supporterPurchaseTokens
                if (pendingSupporterPurchases != null) {
                    prefs[SupporterPurchaseStoreKeys.PENDING_PURCHASES] = pendingSupporterPurchases
                }
                prefs[AppSettingsKeys.SETTINGS_REVISION] = revision
            }
        }
    }

internal fun Preferences.toAppSettings(): AppSettings =
    AppSettings(
        language = this[AppSettingsKeys.LANGUAGE] ?: "system",
        themeMode = this[AppSettingsKeys.THEME_MODE] ?: "system",
        biometricLockEnabled = this[AppSettingsKeys.BIOMETRIC_LOCK_ENABLED] ?: false,
        biometricIdleTimeoutSec = this[AppSettingsKeys.BIOMETRIC_IDLE_TIMEOUT_SEC] ?: 60,
        hideAppContentInRecents = this[AppSettingsKeys.HIDE_APP_CONTENT_IN_RECENTS] ?: false,
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
        supporterBadgeEarned = this[AppSettingsKeys.SUPPORTER_BADGE_EARNED] ?: false,
        supportPurchaseCount = this[AppSettingsKeys.SUPPORT_PURCHASE_COUNT] ?: 0,
        supporterPurchaseTokens = this[AppSettingsKeys.SUPPORTER_PURCHASE_TOKENS].orEmpty(),
        importFocusEpochMs = this[AppSettingsKeys.IMPORT_FOCUS_EPOCH_MS] ?: 0L,
        importFocusCurrencyId = this[AppSettingsKeys.IMPORT_FOCUS_CURRENCY_ID] ?: -1L,
        dashboardPeriodEpochMs = this[AppSettingsKeys.DASHBOARD_PERIOD_EPOCH_MS] ?: 0L,
        tzNormalizedAt = this[AppSettingsKeys.TZ_NORMALIZED_AT],
        chartVisible = this[AppSettingsKeys.CHART_VISIBLE] ?: true,
        chartStyle = this[AppSettingsKeys.CHART_STYLE] ?: "smooth_area",
        chartPeriodType = this[AppSettingsKeys.CHART_PERIOD_TYPE] ?: "follow",
        chartPointCount = this[AppSettingsKeys.CHART_POINT_COUNT] ?: 5,
        chartMetric = this[AppSettingsKeys.CHART_METRIC] ?: "cumulative",
        chartShowGridlines = this[AppSettingsKeys.CHART_SHOW_GRIDLINES] ?: true,
        chartShowLabels = this[AppSettingsKeys.CHART_SHOW_LABELS] ?: true,
        chartColorRule = this[AppSettingsKeys.CHART_COLOR_RULE] ?: "by_sign",
        chartAutoMode = this[AppSettingsKeys.CHART_AUTO_MODE] ?: true,
    )

internal fun AppSettings.writeTo(prefs: androidx.datastore.preferences.core.MutablePreferences) {
    prefs[AppSettingsKeys.LANGUAGE] = language
    prefs[AppSettingsKeys.THEME_MODE] = themeMode
    prefs[AppSettingsKeys.BIOMETRIC_LOCK_ENABLED] = biometricLockEnabled
    prefs[AppSettingsKeys.BIOMETRIC_IDLE_TIMEOUT_SEC] = biometricIdleTimeoutSec
    prefs[AppSettingsKeys.HIDE_APP_CONTENT_IN_RECENTS] = hideAppContentInRecents
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
    prefs[AppSettingsKeys.SUPPORTER_BADGE_EARNED] = supporterBadgeEarned
    prefs[AppSettingsKeys.SUPPORT_PURCHASE_COUNT] = supportPurchaseCount
    prefs[AppSettingsKeys.SUPPORTER_PURCHASE_TOKENS] = supporterPurchaseTokens
    prefs[AppSettingsKeys.IMPORT_FOCUS_EPOCH_MS] = importFocusEpochMs
    prefs[AppSettingsKeys.IMPORT_FOCUS_CURRENCY_ID] = importFocusCurrencyId
    prefs[AppSettingsKeys.DASHBOARD_PERIOD_EPOCH_MS] = dashboardPeriodEpochMs
    if (tzNormalizedAt != null) {
        prefs[AppSettingsKeys.TZ_NORMALIZED_AT] = tzNormalizedAt
    } else {
        prefs.remove(AppSettingsKeys.TZ_NORMALIZED_AT)
    }
    prefs[AppSettingsKeys.CHART_VISIBLE] = chartVisible
    prefs[AppSettingsKeys.CHART_STYLE] = chartStyle
    prefs[AppSettingsKeys.CHART_PERIOD_TYPE] = chartPeriodType
    prefs[AppSettingsKeys.CHART_POINT_COUNT] = chartPointCount
    prefs[AppSettingsKeys.CHART_METRIC] = chartMetric
    prefs[AppSettingsKeys.CHART_SHOW_GRIDLINES] = chartShowGridlines
    prefs[AppSettingsKeys.CHART_SHOW_LABELS] = chartShowLabels
    prefs[AppSettingsKeys.CHART_COLOR_RULE] = chartColorRule
    prefs[AppSettingsKeys.CHART_AUTO_MODE] = chartAutoMode
}
