package com.kshavrin.mymoney.core.ads.token

import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.network.shared.SharedAuth
import com.kshavrin.mymoney.core.network.shared.SharedSession
import com.kshavrin.mymoney.core.network.shared.SharedUser
import com.kshavrin.mymoney.core.network.shared.SupabaseConfig
import com.kshavrin.mymoney.core.network.shared.SupabaseHttpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class SupabaseRewardTokenSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var auth: FakeSharedAuth
    private lateinit var source: SupabaseRewardTokenSource
    private val responseBody = AtomicReference("{}")
    private val requestCount = AtomicInteger()
    private var lastRequestPath: String? = null
    private var lastAuthorization: String? = null
    private var lastRequestBody: String? = null

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requestCount.incrementAndGet()
                    lastRequestPath = request.requestUrl?.encodedPath
                    lastAuthorization = request.getHeader("Authorization")
                    lastRequestBody = request.body.readUtf8()
                    return MockResponse().setResponseCode(200).setBody(responseBody.get())
                }
            }
        server.start()
        auth = FakeSharedAuth()
        val config =
            SupabaseConfig(
                url = server.url("/").toString().removeSuffix("/"),
                anonKey = "anon-key",
            )
        source =
            SupabaseRewardTokenSource(
                auth = auth,
                http = SupabaseHttpTransport(config, OkHttpClient(), Json),
                ioDispatcher = Dispatchers.Unconfined,
            )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `parses epoch expiry and sends the authenticated empty token request`() =
        runTest {
            responseBody.set("""{"custom_data":"epoch-token","expires_at":1700000000}""")

            val result = source.requestToken().getOrThrow()

            assertEquals("epoch-token", result.customData)
            assertEquals(Instant.ofEpochSecond(1_700_000_000L), result.expiresAt)
            assertEquals("user-1", result.sessionUserId)
            assertEquals(1, requestCount.get())
            assertEquals("/functions/v1/create-ad-reward-token", lastRequestPath)
            assertEquals("Bearer access-token", lastAuthorization)
            assertEquals("{}", lastRequestBody)
        }

    @Test
    fun `parses ISO expiry without changing the server token`() =
        runTest {
            val expiry = "2026-08-14T12:00:00Z"
            responseBody.set("""{"custom_data":"iso-token","expires_at":"$expiry"}""")

            val result = source.requestToken().getOrThrow()

            assertEquals("iso-token", result.customData)
            assertEquals(Instant.parse(expiry), result.expiresAt)
        }

    @Test
    fun `rejects malformed or incomplete token responses`() =
        runTest {
            val responses =
                listOf(
                    "{}",
                    """{"custom_data":"token"}""",
                    """{"custom_data":"token","expires_at":"not-an-instant"}""",
                )

            responses.forEach { response ->
                responseBody.set(response)
                val result = source.requestToken()

                assertTrue(result.isFailure)
                assertNotNull(result.exceptionOrNull())
            }
        }

    @Test
    fun `fails closed without an access token and does not call the edge function`() =
        runTest {
            auth.accessTokenResult = Result.failure(SyncException(SyncError.Auth))

            val result = source.requestToken()

            assertSyncError(result, SyncError.Auth)
            assertEquals(0, requestCount.get())
        }

    @Test
    fun `fails closed when the session bearer does not match the access token`() =
        runTest {
            auth.session = SharedSession(SharedUser("user-1", "user@example.com"), "different-token")

            val result = source.requestToken()

            assertSyncError(result, SyncError.Auth)
            assertEquals(0, requestCount.get())
        }

    private fun assertSyncError(
        result: Result<*>,
        expected: SyncError,
    ) {
        assertEquals(expected, (result.exceptionOrNull() as? SyncException)?.syncError)
    }

    private class FakeSharedAuth : SharedAuth {
        var accessTokenResult: Result<String> = Result.success("access-token")
        var session: SharedSession? = SharedSession(SharedUser("user-1", "user@example.com"), "access-token")

        override fun currentSession(): SharedSession? = session

        override suspend fun accessToken(): Result<String> = accessTokenResult

        override suspend fun signInWithGoogle(
            googleIdToken: String,
            nonce: String,
        ): Result<SharedSession> = Result.failure(UnsupportedOperationException())

        override suspend fun signOut(): Result<Unit> = Result.success(Unit)
    }
}
