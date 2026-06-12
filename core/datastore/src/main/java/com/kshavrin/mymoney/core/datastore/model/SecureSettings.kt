package com.kshavrin.mymoney.core.datastore.model

data class SecureSettings(
    val dropboxRefreshToken: String? = null,
    val gdriveAccountEmail: String? = null,
    val pinHash: String? = null,
    val failedPinAttempts: Int = 0,
    val pinLockoutDeadlineEpochMs: Long? = null,
)
