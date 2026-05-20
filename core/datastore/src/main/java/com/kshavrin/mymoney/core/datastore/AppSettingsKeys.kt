package com.kshavrin.mymoney.core.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

internal object AppSettingsKeys {
    val LANGUAGE = stringPreferencesKey("language")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val BIOMETRIC_LOCK_ENABLED = booleanPreferencesKey("biometric_lock_enabled")
    val BIOMETRIC_IDLE_TIMEOUT_SEC = intPreferencesKey("biometric_idle_timeout_sec")
    val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
    val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
    val DEFAULT_ACCOUNT_ID = longPreferencesKey("default_account_id")
    val DEFAULT_PERIOD = stringPreferencesKey("default_period")
    val DATE_FIRST_DAY_OF_WEEK = intPreferencesKey("date_first_day_of_week")
    val CURRENCY_SYMBOL_POSITION = stringPreferencesKey("currency_symbol_position")
    val ONBOARDING_COMPLETED_AT = longPreferencesKey("onboarding_completed_at")
    val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
    val AUTO_SYNC_ENABLED = booleanPreferencesKey("auto_sync_enabled")
    val BUDGET_MODE_ENABLED = booleanPreferencesKey("budget_mode_enabled")
    val FIRST_POSITIVE_SEEN = booleanPreferencesKey("first_positive_seen")
}
