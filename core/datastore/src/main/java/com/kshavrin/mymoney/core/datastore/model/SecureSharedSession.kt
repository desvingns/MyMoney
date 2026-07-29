package com.kshavrin.mymoney.core.datastore.model

data class SecureSharedSession(
    val userId: String,
    val userEmail: String,
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAtEpochSeconds: Long,
)
