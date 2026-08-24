package com.kshavrin.mymoney.feature.support.googlesignin

import android.app.Activity
import androidx.lifecycle.ViewModelStore
import com.kshavrin.mymoney.core.network.shared.GoogleIdTokenProvider
import com.kshavrin.mymoney.core.network.shared.GoogleIdTokenResult
import com.kshavrin.mymoney.core.network.shared.SharedAuth
import com.kshavrin.mymoney.core.network.shared.SharedSession
import com.kshavrin.mymoney.core.network.shared.SharedUser
import com.kshavrin.mymoney.core.network.shared.SignInWithGoogleUseCase
import com.kshavrin.mymoney.feature.support.util.MainDispatcherRule
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric supplies a real android.app.Activity so signIn() can be called with a non-null
// receiver; FakeGoogleIdTokenProvider ignores the Activity parameter.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class GoogleSignInViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val viewModelStore = ViewModelStore()
    private val activity =
        Robolectric.buildActivity(android.app.Activity::class.java).create().get()

    @After
    fun tearDown() {
        viewModelStore.clear()
    }

    // --- Fakes ---

    private inner class FakeGoogleIdTokenProvider(
        private val result: Result<GoogleIdTokenResult> =
            Result.success(GoogleIdTokenResult("id-token", "nonce")),
        private val neverFetch: Boolean = false,
    ) : GoogleIdTokenProvider {
        var callCount = 0

        override suspend fun fetchIdToken(activity: Activity): Result<GoogleIdTokenResult> {
            callCount++
            if (neverFetch) {
                suspendCancellableCoroutine<Nothing> {}
            }
            return result
        }
    }

    private inner class FakeSharedAuth(
        private val signInResult: Result<SharedSession> =
            Result.success(SharedSession(SharedUser("u-1", "u@example.com"), "tok")),
    ) : SharedAuth {
        override fun currentSession(): SharedSession? = null

        override suspend fun signInWithGoogle(
            googleIdToken: String,
            nonce: String,
        ): Result<SharedSession> = signInResult

        override suspend fun signOut(): Result<Unit> = Result.success(Unit)
    }

    private fun createViewModel(
        fakeProvider: FakeGoogleIdTokenProvider = FakeGoogleIdTokenProvider(),
        fakeAuth: FakeSharedAuth = FakeSharedAuth(),
    ): GoogleSignInViewModel {
        val vm =
            GoogleSignInViewModel(
                googleIdTokenProvider = fakeProvider,
                signInWithGoogle = SignInWithGoogleUseCase(fakeAuth),
                ioDispatcher = mainDispatcherRule.testDispatcher,
            )
        viewModelStore.put("google_sign_in", vm)
        return vm
    }

    // --- Initial state ---

    @Test
    fun `initial state is Idle before any interaction`() {
        val vm = createViewModel()

        assertEquals(GoogleSignInStatus.Idle, vm.state.value.status)
    }

    // --- Success path ---

    @Test
    fun `signIn transitions from Idle through Loading to Success on valid credentials`() =
        runTest {
            val vm = createViewModel()

            vm.signIn(activity)
            runCurrent()

            assertEquals(GoogleSignInStatus.Success, vm.state.value.status)
        }

    @Test
    fun `state is Loading while token fetch has not yet resolved`() =
        runTest {
            val vm = createViewModel(fakeProvider = FakeGoogleIdTokenProvider(neverFetch = true))

            vm.signIn(activity)

            // The coroutine is suspended inside fetchIdToken — Loading is the honest state.
            assertEquals(GoogleSignInStatus.Loading, vm.state.value.status)
        }

    // --- Failure paths ---

    @Test
    fun `signIn sets Error when token provider fails to return a credential`() =
        runTest {
            val vm =
                createViewModel(
                    fakeProvider =
                        FakeGoogleIdTokenProvider(
                            result = Result.failure(RuntimeException("no credential")),
                        ),
                )

            vm.signIn(activity)
            runCurrent()

            assertEquals(GoogleSignInStatus.Error, vm.state.value.status)
        }

    @Test
    fun `signIn sets Error when signInWithGoogle returns a failure`() =
        runTest {
            val vm =
                createViewModel(
                    fakeAuth =
                        FakeSharedAuth(
                            signInResult = Result.failure(RuntimeException("server rejected")),
                        ),
                )

            vm.signIn(activity)
            runCurrent()

            assertEquals(GoogleSignInStatus.Error, vm.state.value.status)
        }

    // --- Guard: duplicate call while in flight ---

    @Test
    fun `duplicate signIn while first job is active is a no-op`() =
        runTest {
            val provider = FakeGoogleIdTokenProvider(neverFetch = true)
            val vm = createViewModel(fakeProvider = provider)

            vm.signIn(activity)
            assertEquals(GoogleSignInStatus.Loading, vm.state.value.status)

            vm.signIn(activity)

            // fetchIdToken was called exactly once — the guard short-circuited the second call.
            assertEquals(1, provider.callCount)
        }

    // --- No false Success before operations complete ---

    @Test
    fun `status is not Success before async operations resolve`() =
        runTest {
            val vm = createViewModel(fakeProvider = FakeGoogleIdTokenProvider(neverFetch = true))

            vm.signIn(activity)

            assertNotEquals(GoogleSignInStatus.Success, vm.state.value.status)
        }
}
