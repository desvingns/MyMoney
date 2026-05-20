package com.kshavrin.mymoney.core.datastore.model

data class AppSettings(
    val language: String = "system",
    val themeMode: String = "system",
    val biometricLockEnabled: Boolean = false,
    val biometricIdleTimeoutSec: Int = 60,
    val soundEnabled: Boolean = true,
    val hapticEnabled: Boolean = true,
    val defaultAccountId: Long = -1L,
    val defaultPeriod: String = "month",
    val dateFirstDayOfWeek: Int = 1,
    val currencySymbolPosition: String = "before",
    val onboardingCompletedAt: Long? = null,
    val lastSyncAt: Long? = null,
    val autoSyncEnabled: Boolean = true,
    val budgetModeEnabled: Boolean = true,
    val firstPositiveSeen: Boolean = false,
)
