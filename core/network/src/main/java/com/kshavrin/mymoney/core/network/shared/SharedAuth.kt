package com.kshavrin.mymoney.core.network.shared

import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException

data class SharedSession(
    val user: SharedUser,
    val accessToken: String,
)

data class StoredSharedSession(
    val userId: String,
    val userEmail: String,
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAtEpochSeconds: Long,
)

interface SharedSessionStore {
    fun readSharedSession(): StoredSharedSession? = null

    fun writeSharedSession(session: StoredSharedSession) = Unit

    fun clearSharedSession() = Unit
}

interface SharedAuth {
    fun currentSession(): SharedSession?

    suspend fun accessToken(): Result<String> =
        currentSession()
            ?.accessToken
            ?.takeIf(String::isNotBlank)
            ?.let(Result.Companion::success)
            ?: Result.failure(SyncException(SyncError.Auth))

    suspend fun signInWithGoogle(
        googleIdToken: String,
        nonce: String,
    ): Result<SharedSession>

    suspend fun signOut(): Result<Unit>

    suspend fun clearLocalSession() = Unit

    companion object {
        val GOOGLE_SCOPES = listOf("openid", "email", "profile")
    }
}
