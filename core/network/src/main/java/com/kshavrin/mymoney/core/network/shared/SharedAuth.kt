package com.kshavrin.mymoney.core.network.shared

data class SharedSession(
    val user: SharedUser,
    val accessToken: String,
)

interface SharedAuth {
    fun currentSession(): SharedSession?

    suspend fun signInWithGoogle(googleIdToken: String): Result<SharedSession>

    suspend fun signOut(): Result<Unit>

    companion object {
        val GOOGLE_SCOPES = listOf("openid", "email", "profile")
    }
}
