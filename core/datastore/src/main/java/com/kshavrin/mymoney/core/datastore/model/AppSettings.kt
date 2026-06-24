package com.kshavrin.mymoney.core.datastore.model

data class AppSettings(
    val language: String = "system",
    val themeMode: String = "system",
    val biometricLockEnabled: Boolean = false,
    val biometricIdleTimeoutSec: Int = 60,
    val soundEnabled: Boolean = true,
    val hapticEnabled: Boolean = true,
    val defaultAccountId: Long = -1L,
    val dashboardSelectionMode: String = "specific_account",
    val defaultPeriod: String = "month",
    val dateFirstDayOfWeek: Int = 1,
    val currencySymbolPosition: String = "before",
    val onboardingCompletedAt: Long? = null,
    val lastSyncAt: Long? = null,
    val autoSyncEnabled: Boolean = true,
    val budgetModeEnabled: Boolean = true,
    val firstPositiveSeen: Boolean = false,
    val importFocusEpochMs: Long = 0L,
    val importFocusCurrencyId: Long = -1L,
    // Epoch-millis anchor (any instant within the month) of the last dashboard period the
    // user viewed. Survives process death so a cold start restores that month instead of
    // snapping back to the current month — otherwise data imported into a past month becomes
    // invisible once the transient import focus is cleared. 0L = unset → default to now().
    val dashboardPeriodEpochMs: Long = 0L,
    val tzNormalizedAt: Long? = null,
    val chartVisible: Boolean = true,
    val chartStyle: String = "smooth_area",
    val chartPeriodType: String = "follow",
    val chartPointCount: Int = 5,
    val chartMetric: String = "cumulative",
    val chartShowGridlines: Boolean = true,
    val chartShowLabels: Boolean = true,
    val chartColorRule: String = "by_sign",
    val chartAutoMode: Boolean = true,
)
