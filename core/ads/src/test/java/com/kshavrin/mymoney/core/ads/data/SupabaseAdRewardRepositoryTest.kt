package com.kshavrin.mymoney.core.ads.data

import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.domain.ads.AdRewardState
import com.kshavrin.mymoney.core.domain.ads.ConfirmationOutcome
import com.kshavrin.mymoney.core.domain.ads.FrozenReason
import com.kshavrin.mymoney.core.network.shared.SharedAuth
import com.kshavrin.mymoney.core.network.shared.SharedSession
import com.kshavrin.mymoney.core.network.shared.SharedUser
import com.kshavrin.mymoney.core.network.shared.SupabaseConfig
import com.kshavrin.mymoney.core.network.shared.SupabaseHttpTransport
import com.kshavrin.mymoney.core.network.shared.AuthSessionLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SupabaseAdRewardRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var auth: FakeSharedAuth
    private lateinit var authSessionLifecycle: RecordingAuthSessionLifecycle
    private lateinit var repository: SupabaseAdRewardRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        auth = FakeSharedAuth()
        authSessionLifecycle = RecordingAuthSessionLifecycle()
        val config =
            SupabaseConfig(
                url = server.url("/").toString().removeSuffix("/"),
                anonKey = "anon-key",
            )
        repository =
            SupabaseAdRewardRepository(
                auth = auth,
                http = SupabaseHttpTransport(config, OkHttpClient(), Json),
                backoff = AdRewardBackoff(delaysMillis = emptyList(), maximumWaitMillis = 10_000L),
                ioDispatcher = Dispatchers.Unconfined,
                authSessionLifecycle = authSessionLifecycle,
            )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `refresh strictly maps the complete RPC object and preserves server fields`() =
        runBlocking {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"progress":3,"required":5,"frozen":true,"frozenReason":"plus_active:admob","plusActive":true,"plusProvider":"admob","plusExpiresAt":"2026-08-14T12:00:00Z"}""",
                ),
            )

            val expected =
                AdRewardState(
                    progress = 3,
                    required = 5,
                    frozen = true,
                    frozenReason = FrozenReason.PlusActive("admob"),
                    plusActive = true,
                    plusProvider = "admob",
                    plusExpiresAt = Instant.parse("2026-08-14T12:00:00Z"),
                )
            val actual = repository.refresh().getOrThrow()

            assertEquals(expected, actual)
            assertEquals(expected, repository.state.value)
            val request = server.takeRequest()
            assertEquals("/rest/v1/rpc/get_ad_reward_state", request.path)
            assertEquals("Bearer access-token", request.getHeader("Authorization"))
            assertEquals("anon-key", request.getHeader("apikey"))
            assertEquals("{}", request.body.readUtf8())
        }

    @Test
    fun `refresh maps an unknown frozen reason to Unknown without rejecting the object`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200).setBody(stateJson(frozenReason = "future_reason")))

            val state = repository.refresh().getOrThrow()

            assertEquals(FrozenReason.Unknown, state.frozenReason)
            assertFalse(state.plusActive)
            assertNull(state.plusProvider)
            assertNull(state.plusExpiresAt)
        }

    @Test
    fun `refresh maps malformed and non-object RPC responses to a server error`() =
        runBlocking {
            listOf("not-json", "[]", "\"state\"", "{}")
                .forEach { responseBody ->
                    server.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

                    val result = repository.refresh()

                    assertSyncError(result, SyncError.Server)
                    assertNull(repository.state.value)
                }
        }

    @Test
    fun `refresh preserves cancellation from the authentication boundary`() =
        runBlocking {
            val cancellation = CancellationException("caller cancelled")
            auth.accessTokenResult = Result.failure(cancellation)

            val thrown =
                try {
                    repository.refresh()
                    error("refresh should preserve cancellation")
                } catch (error: CancellationException) {
                    error
                }

            assertSame(cancellation, thrown)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `refresh returns auth without a network call when there is no session`() =
        runBlocking {
            auth.session = null

            val result = repository.refresh()

            assertSyncError(result, SyncError.Auth)
            assertEquals(0, auth.accessTokenCalls)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `refresh clears cached state when the authenticated session disappears`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200).setBody(stateJson(progress = 2)))
            repository.refresh().getOrThrow()
            auth.session = null

            val result = repository.refresh()

            assertSyncError(result, SyncError.Auth)
            assertNull(repository.state.value)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `refresh keeps the current state on a network failure and clears it on auth failure`() =
        runBlocking {
            val previous = rewardState(progress = 2)
            server.enqueue(MockResponse().setResponseCode(200).setBody(stateJson(progress = 2)))
            repository.refresh().getOrThrow()

            server.enqueue(MockResponse().setResponseCode(500).setBody("server down"))
            val networkFailure = repository.refresh()

            assertSyncError(networkFailure, SyncError.Server)
            assertEquals(previous, repository.state.value)

            server.enqueue(MockResponse().setResponseCode(401))
            val authFailure = repository.refresh()

            assertSyncError(authFailure, SyncError.Auth)
            assertNull(repository.state.value)
        }

    @Test
    fun `invalidateSession clears the StateFlow before a different session can refresh`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200).setBody(stateJson(progress = 1)))
            repository.refresh().getOrThrow()

            repository.invalidateSession()

            assertNull(repository.state.value)
            auth.session = session(userId = "user-2", accessToken = "second-token")
            auth.accessTokenResult = Result.success("second-token")
            server.enqueue(MockResponse().setResponseCode(200).setBody(stateJson(progress = 4)))

            assertEquals(rewardState(progress = 4), repository.refresh().getOrThrow())
            assertEquals(rewardState(progress = 4), repository.state.value)
        }

    @Test
    fun `stale confirmation does not clear a newer server state`() =
        runBlocking {
            val previous = rewardState(progress = 2)
            val current = rewardState(progress = 3)
            seed(previous)
            server.enqueue(MockResponse().setResponseCode(200).setBody(stateJson(current)))
            assertEquals(current, repository.refresh().getOrThrow())

            assertEquals(ConfirmationOutcome.PendingConfirmation, repository.awaitConfirmation(previous))
            assertEquals(current, repository.state.value)
        }

    @Test
    fun `auth lifecycle invalidation clears a held StateFlow collector`() =
        runBlocking {
            val previous = rewardState(progress = 2)
            seed(previous)
            var observed: AdRewardState? = null
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                repository.state.collect { observed = it }
            }

            authSessionLifecycle.invalidate()
            yield()

            assertNull(repository.state.value)
            assertNull(observed)
            collector.cancelAndJoin()
        }

    @Test
    fun `concurrent refreshes publish responses in invocation order`() =
        runBlocking {
            val firstStarted = CountDownLatch(1)
            val secondStarted = CountDownLatch(1)
            val releaseFirst = CountDownLatch(1)
            val requestCount = AtomicInteger()
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        return if (requestCount.incrementAndGet() == 1) {
                            firstStarted.countDown()
                            check(releaseFirst.await(1, TimeUnit.SECONDS)) { "first response was not released" }
                            MockResponse().setResponseCode(200).setBody(stateJson(progress = 1))
                        } else {
                            secondStarted.countDown()
                            MockResponse().setResponseCode(200).setBody(stateJson(progress = 2))
                        }
                    }
                }

            val first = async(start = CoroutineStart.UNDISPATCHED) { repository.refresh().getOrThrow() }
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS))
            val second = async(start = CoroutineStart.UNDISPATCHED) { repository.refresh().getOrThrow() }

            assertFalse(secondStarted.await(200, TimeUnit.MILLISECONDS))
            releaseFirst.countDown()

            assertEquals(rewardState(progress = 1), first.await())
            assertEquals(rewardState(progress = 2), second.await())
            assertEquals(rewardState(progress = 2), repository.state.value)
        }

    @Test
    fun `awaitConfirmation reports progress increased after the server advances progress`() =
        runBlocking {
            val previous = rewardState(progress = 2)
            val updated = rewardState(progress = 3)
            seed(previous)
            server.enqueue(MockResponse().setResponseCode(200).setBody(stateJson(progress = 3)))

            val outcome = repository.awaitConfirmation(previous)

            assertEquals(ConfirmationOutcome.ProgressIncreased(updated), outcome)
        }

    @Test
    fun `awaitConfirmation grants Plus only on a false to true transition`() =
        runBlocking {
            val previous = rewardState(progress = 4, plusActive = false)
            val updated =
                rewardState(
                    progress = 4,
                    plusActive = true,
                    plusProvider = "admob",
                    plusExpiresAt = Instant.parse("2026-08-15T12:00:00Z"),
                )
            seed(previous)
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    stateJson(
                        progress = 4,
                        plusActive = true,
                        plusProvider = "admob",
                        plusExpiresAt = Instant.parse("2026-08-15T12:00:00Z"),
                    ),
                ),
            )

            val outcome = repository.awaitConfirmation(previous)

            assertEquals(ConfirmationOutcome.PlusGranted(updated), outcome)
        }

    @Test
    fun `awaitConfirmation reports progress instead of Plus when Plus was already active`() =
        runBlocking {
            val previous = rewardState(progress = 2, plusActive = true)
            val updated = rewardState(progress = 3, plusActive = true)
            seed(previous)
            server.enqueue(MockResponse().setResponseCode(200).setBody(stateJson(progress = 3, plusActive = true)))

            val outcome = repository.awaitConfirmation(previous)

            assertEquals(ConfirmationOutcome.ProgressIncreased(updated), outcome)
        }

    @Test
    fun `awaitConfirmation returns pending when confirmation is missing`() =
        runBlocking {
            val previous = rewardState(progress = 2)
            seed(previous)
            server.enqueue(MockResponse().setResponseCode(200).setBody(stateJson(progress = 2)))

            val outcome = repository.awaitConfirmation(previous)

            assertEquals(ConfirmationOutcome.PendingConfirmation, outcome)
            assertEquals(previous, repository.state.value)
        }

    @Test
    fun `awaitConfirmation returns pending when the confirmation refresh hits the network`() =
        runBlocking {
            val previous = rewardState(progress = 2)
            seed(previous)
            server.enqueue(MockResponse().setResponseCode(500).setBody("temporary outage"))

            val outcome = repository.awaitConfirmation(previous)

            assertEquals(ConfirmationOutcome.PendingConfirmation, outcome)
            assertEquals(previous, repository.state.value)
        }

    @Test
    fun `awaitConfirmation returns pending and clears state when the session changes before polling`() =
        runBlocking {
            val previous = rewardState(progress = 2)
            seed(previous)
            auth.session = session(userId = "user-2", accessToken = "second-token")
            auth.accessTokenResult = Result.success("second-token")

            val outcome = repository.awaitConfirmation(previous)

            assertEquals(ConfirmationOutcome.PendingConfirmation, outcome)
            assertNull(repository.state.value)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `awaitConfirmation returns pending when the session changes during a refresh`() =
        runBlocking {
            val previous = rewardState(progress = 2)
            seed(previous)
            val requestStarted = CountDownLatch(1)
            val releaseResponse = CountDownLatch(1)
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        requestStarted.countDown()
                        check(releaseResponse.await(1, TimeUnit.SECONDS)) { "response was not released" }
                        return MockResponse().setResponseCode(200).setBody(stateJson(progress = 3))
                    }
                }

            val confirmation = async(start = CoroutineStart.UNDISPATCHED) { repository.awaitConfirmation(previous) }
            assertTrue(requestStarted.await(1, TimeUnit.SECONDS))
            auth.session = session(userId = "user-2", accessToken = "second-token")
            releaseResponse.countDown()

            assertEquals(ConfirmationOutcome.PendingConfirmation, confirmation.await())
            assertNull(repository.state.value)
        }

    private suspend fun seed(state: AdRewardState) {
        server.enqueue(MockResponse().setResponseCode(200).setBody(stateJson(state)))
        assertEquals(state, repository.refresh().getOrThrow())
    }

    private fun rewardState(
        progress: Int,
        required: Int = 5,
        frozen: Boolean = false,
        frozenReason: FrozenReason? = null,
        plusActive: Boolean = false,
        plusProvider: String? = null,
        plusExpiresAt: Instant? = null,
    ) =
        AdRewardState(
            progress = progress,
            required = required,
            frozen = frozen,
            frozenReason = frozenReason,
            plusActive = plusActive,
            plusProvider = plusProvider,
            plusExpiresAt = plusExpiresAt,
        )

    private fun stateJson(
        state: AdRewardState = rewardState(progress = 0),
        progress: Int = state.progress,
        frozenReason: String? = state.frozenReason.toJsonValue(),
        plusActive: Boolean = state.plusActive,
        plusProvider: String? = state.plusProvider,
        plusExpiresAt: Instant? = state.plusExpiresAt,
    ): String =
        """{"progress":$progress,"required":${state.required},"frozen":${state.frozen},"frozenReason":${frozenReason?.let(::quote) ?: "null"},"plusActive":$plusActive,"plusProvider":${plusProvider?.let(::quote) ?: "null"},"plusExpiresAt":${plusExpiresAt?.toString()?.let(::quote) ?: "null"}}"""

    private fun FrozenReason?.toJsonValue(): String? =
        when (this) {
            null -> null
            FrozenReason.Unknown -> "unknown"
            is FrozenReason.PlusActive -> "plus_active:$provider"
        }

    private fun quote(value: String): String = "\"$value\""

    private fun session(
        userId: String = "user-1",
        accessToken: String = "access-token",
    ) = SharedSession(SharedUser(userId, "$userId@example.com"), accessToken)

    private fun assertSyncError(
        result: Result<*>,
        expected: SyncError,
    ) {
        assertEquals(expected, (result.exceptionOrNull() as? SyncException)?.syncError)
    }

    private class FakeSharedAuth : SharedAuth {
        var session: SharedSession? = SharedSession(SharedUser("user-1", "user-1@example.com"), "access-token")
        var accessTokenResult: Result<String> = Result.success("access-token")
        var accessTokenCalls = 0

        override fun currentSession(): SharedSession? = session

        override suspend fun accessToken(): Result<String> {
            accessTokenCalls++
            return accessTokenResult
        }

        override suspend fun signInWithGoogle(
            googleIdToken: String,
            nonce: String,
        ): Result<SharedSession> = Result.failure(UnsupportedOperationException())

        override suspend fun signOut(): Result<Unit> {
            session = null
            return Result.success(Unit)
        }
    }

    private class RecordingAuthSessionLifecycle : AuthSessionLifecycle {
        private val listeners = CopyOnWriteArrayList<() -> Unit>()

        override fun addInvalidationListener(listener: () -> Unit) {
            listeners += listener
        }

        override fun invalidate() {
            listeners.forEach { it() }
        }
    }
}
