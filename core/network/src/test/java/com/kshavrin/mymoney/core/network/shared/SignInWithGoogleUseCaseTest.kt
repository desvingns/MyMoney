package com.kshavrin.mymoney.core.network.shared

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInWithGoogleUseCaseTest {

    private inner class FakeSharedAuth(
        private val signInResult: Result<SharedSession> =
            Result.success(SharedSession(SharedUser("u-1", "u@example.com"), "tok")),
    ) : SharedAuth {
        var capturedToken: String? = null
        var capturedNonce: String? = null

        override fun currentSession(): SharedSession? = null

        override suspend fun signInWithGoogle(
            googleIdToken: String,
            nonce: String,
        ): Result<SharedSession> {
            capturedToken = googleIdToken
            capturedNonce = nonce
            return signInResult
        }

        override suspend fun signOut(): Result<Unit> = Result.success(Unit)
    }

    @Test
    fun `returns the SharedSession on successful sign-in`() =
        runTest {
            val expected = SharedSession(SharedUser("u-42", "u42@example.com"), "session-tok")
            val fakeAuth = FakeSharedAuth(signInResult = Result.success(expected))
            val useCase = SignInWithGoogleUseCase(fakeAuth)

            val result = useCase("id-token", "nonce")

            assertTrue(result.isSuccess)
            assertEquals(expected, result.getOrThrow())
        }

    @Test
    fun `propagates failure when SharedAuth returns an error`() =
        runTest {
            val cause = RuntimeException("server auth error")
            val fakeAuth = FakeSharedAuth(signInResult = Result.failure(cause))
            val useCase = SignInWithGoogleUseCase(fakeAuth)

            val result = useCase("id-token", "nonce")

            assertTrue(result.isFailure)
            assertEquals(cause, result.exceptionOrNull())
        }

    @Test
    fun `forwards id token and nonce to SharedAuth verbatim`() =
        runTest {
            val fakeAuth = FakeSharedAuth()
            val useCase = SignInWithGoogleUseCase(fakeAuth)

            useCase("google-id-token-xyz", "raw-nonce-abc")

            assertEquals("google-id-token-xyz", fakeAuth.capturedToken)
            assertEquals("raw-nonce-abc", fakeAuth.capturedNonce)
        }
}
