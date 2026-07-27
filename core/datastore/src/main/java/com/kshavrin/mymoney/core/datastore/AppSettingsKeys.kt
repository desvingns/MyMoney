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
    val HIDE_APP_CONTENT_IN_RECENTS = booleanPreferencesKey("hide_app_content_in_recents")
    val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
    val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
    val DEFAULT_ACCOUNT_ID = longPreferencesKey("default_account_id")
    val DASHBOARD_SELECTION_MODE = stringPreferencesKey("dashboard_selection_mode")
    val DEFAULT_PERIOD = stringPreferencesKey("default_period")
    val DATE_FIRST_DAY_OF_WEEK = intPreferencesKey("date_first_day_of_week")
    val CURRENCY_SYMBOL_POSITION = stringPreferencesKey("currency_symbol_position")
    val ONBOARDING_COMPLETED_AT = longPreferencesKey("onboarding_completed_at")
    val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
    val AUTO_SYNC_ENABLED = booleanPreferencesKey("auto_sync_enabled")
    val BUDGET_MODE_ENABLED = booleanPreferencesKey("budget_mode_enabled")
    val FIRST_POSITIVE_SEEN = booleanPreferencesKey("first_positive_seen")
    val IMPORT_FOCUS_EPOCH_MS = longPreferencesKey("import_focus_epoch_ms")
    val IMPORT_FOCUS_CURRENCY_ID = longPreferencesKey("import_focus_currency_id")
    val DASHBOARD_PERIOD_EPOCH_MS = longPreferencesKey("dashboard_period_epoch_ms")
    val TZ_NORMALIZED_AT = longPreferencesKey("tz_normalized_at")
    val CHART_VISIBLE = booleanPreferencesKey("chart_visible")
    val CHART_STYLE = stringPreferencesKey("chart_style")
    val CHART_PERIOD_TYPE = stringPreferencesKey("chart_period_type")
    val CHART_POINT_COUNT = intPreferencesKey("chart_point_count")
    val CHART_METRIC = stringPreferencesKey("chart_metric")
    val CHART_SHOW_GRIDLINES = booleanPreferencesKey("chart_show_gridlines")
    val CHART_SHOW_LABELS = booleanPreferencesKey("chart_show_labels")
    val CHART_COLOR_RULE = stringPreferencesKey("chart_color_rule")
    val CHART_AUTO_MODE = booleanPreferencesKey("chart_auto_mode")
    val SETTINGS_REVISION = longPreferencesKey("settings_revision")
    val DEVICE_ID = stringPreferencesKey("device_id")
}
