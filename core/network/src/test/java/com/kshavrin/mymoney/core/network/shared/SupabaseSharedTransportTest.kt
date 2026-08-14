package com.kshavrin.mymoney.core.network.shared

import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SupabaseSharedTransportTest {
    private lateinit var server: MockWebServer
    private lateinit var auth: SupabaseSharedAuth
    private lateinit var workspaceRpc: SupabaseSharedWorkspaceRpc
    private lateinit var journalRpc: SupabaseSharedJournalRpc
    private lateinit var sessionStore: RecordingSessionStore
    private lateinit var authSessionLifecycle: RecordingAuthSessionLifecycle
    private val clock = Clock.fixed(Instant.ofEpochSecond(1_700_000_000L), ZoneOffset.UTC)
    private val validOperationJson =
        """{"id":"operation-1","workspace_id":"workspace-1","author_id":"author-1","idempotency_key":"operation-1","server_sequence":5,"base_sequence":4,"device_id":"device-1","entity_kind":"transaction","entity_id":"transaction-1","payload":null,"tombstone":false,"created_at":"2026-07-29T12:00:00Z"}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val config =
            SupabaseConfig(
                url = server.url("/").toString().removeSuffix("/"),
                anonKey = "anon-key",
                googleWebClientId = "web-client-id",
            )
        val http = SupabaseHttpTransport(config, OkHttpClient(), Json)
        sessionStore = RecordingSessionStore()
        authSessionLifecycle = RecordingAuthSessionLifecycle()
        auth = SupabaseSharedAuth(config, http, sessionStore, clock, authSessionLifecycle)
        workspaceRpc = SupabaseSharedWorkspaceRpc(auth, http)
        journalRpc = SupabaseSharedJournalRpc(auth, http)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `Google id token exchange forwards nonce without an authorization header`() =
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    sessionResponse(),
                ),
            )

            val session = auth.signInWithGoogle("google-id-token", "request-nonce").getOrThrow()

            val request = server.takeRequest()
            val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("/auth/v1/token?grant_type=id_token", request.path)
            assertEquals("anon-key", request.getHeader("apikey"))
            assertNull(request.getHeader("Authorization"))
            assertEquals("google", body["provider"]?.jsonPrimitive?.content)
            assertEquals("google-id-token", body["id_token"]?.jsonPrimitive?.content)
            assertEquals("request-nonce", body["nonce"]?.jsonPrimitive?.content)
            assertEquals("user-1", session.user.id)
            assertEquals("member@example.com", session.user.email)
            assertEquals("shared-access-token", session.accessToken)
            assertEquals(session, auth.currentSession())
            assertEquals("shared-refresh-token", sessionStore.session?.refreshToken)
        }

    @Test
    fun `auth transitions invalidate registered session listeners`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody(sessionResponse()))
            auth.signInWithGoogle("google-id-token", "request-nonce").getOrThrow()
            assertEquals(1, authSessionLifecycle.invalidationCount.get())

            server.enqueue(MockResponse().setResponseCode(204))
            auth.signOut().getOrThrow()
            assertEquals(2, authSessionLifecycle.invalidationCount.get())

            auth.resetLocalSession { }
            assertEquals(3, authSessionLifecycle.invalidationCount.get())
        }

    @Test
    fun `Google id token exchange rejects blank credentials without a network call`() =
        runTest {
            val result = auth.signInWithGoogle(googleIdToken = "", nonce = "request-nonce")

            assertSyncError(result, SyncError.Auth)
            assertEquals(0, server.requestCount)
            assertNull(auth.currentSession())
        }

    @Test
    fun `Google id token exchange maps an incomplete session payload to a server error`() =
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"access_token":"shared-access-token","refresh_token":"shared-refresh-token","expires_at":1700003600,"user":{"id":"user-1"}}""",
                ),
            )

            val result = auth.signInWithGoogle("google-id-token", "request-nonce")

            assertSyncError(result, SyncError.Server)
            assertNull(auth.currentSession())
        }

    @Test
    fun `sign out sends the session bearer and clears the cached session`() =
        runTest {
            signIn()
            server.enqueue(MockResponse().setResponseCode(204))

            auth.signOut().getOrThrow()

            val request = server.takeRequest()
            assertEquals("/auth/v1/logout", request.path)
            assertEquals("anon-key", request.getHeader("apikey"))
            assertEquals("Bearer shared-access-token", request.getHeader("Authorization"))
            assertNull(auth.currentSession())
            assertNull(sessionStore.session)
        }

    @Test
    fun `restarted auth restores an encrypted stored session before a shared RPC`() =
        runTest {
            sessionStore.session = storedSession(accessToken = "persisted-access-token", expiresAt = 1_800_000_000L)
            val config =
                SupabaseConfig(
                    url = server.url("/").toString().removeSuffix("/"),
                    anonKey = "anon-key",
                    googleWebClientId = "web-client-id",
                )
            val restartedAuth = SupabaseSharedAuth(config, SupabaseHttpTransport(config, OkHttpClient(), Json), sessionStore, clock)
            val restartedRpc = SupabaseSharedWorkspaceRpc(restartedAuth, SupabaseHttpTransport(config, OkHttpClient(), Json))
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"id":"workspace-1","name":"Budget","owner_id":"user-1","created_at":"2026-07-29T12:00:00Z"}""",
                ),
            )

            restartedRpc.createWorkspace("Budget").getOrThrow()

            assertEquals("member@example.com", restartedAuth.currentSession()?.user?.email)
            assertEquals("Bearer persisted-access-token", server.takeRequest().getHeader("Authorization"))
        }

    @Test
    fun `expired stored session refreshes and atomically rotates credentials before a shared RPC`() =
        runTest {
            sessionStore.session = storedSession(accessToken = "expired-access-token", refreshToken = "old-refresh-token", expiresAt = 1L)
            val config =
                SupabaseConfig(
                    url = server.url("/").toString().removeSuffix("/"),
                    anonKey = "anon-key",
                    googleWebClientId = "web-client-id",
                )
            val restartedAuth = SupabaseSharedAuth(config, SupabaseHttpTransport(config, OkHttpClient(), Json), sessionStore, clock)
            val restartedRpc = SupabaseSharedWorkspaceRpc(restartedAuth, SupabaseHttpTransport(config, OkHttpClient(), Json))
            server.enqueue(MockResponse().setResponseCode(200).setBody(sessionResponse("refreshed-access-token", "rotated-refresh-token")))
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"id":"workspace-1","name":"Budget","owner_id":"user-1","created_at":"2026-07-29T12:00:00Z"}""",
                ),
            )

            restartedRpc.createWorkspace("Budget").getOrThrow()

            val refreshRequest = server.takeRequest()
            val refreshBody = Json.parseToJsonElement(refreshRequest.body.readUtf8()).jsonObject
            assertEquals("/auth/v1/token?grant_type=refresh_token", refreshRequest.path)
            assertNull(refreshRequest.getHeader("Authorization"))
            assertEquals("old-refresh-token", refreshBody["refresh_token"]?.jsonPrimitive?.content)
            assertEquals("Bearer refreshed-access-token", server.takeRequest().getHeader("Authorization"))
            assertEquals("rotated-refresh-token", sessionStore.session?.refreshToken)
            assertEquals("refreshed-access-token", sessionStore.session?.accessToken)
        }

    @Test
    fun `terminal refresh auth failure clears stored credentials`() =
        runTest {
            sessionStore.session = storedSession(expiresAt = 1L)
            val config =
                SupabaseConfig(
                    url = server.url("/").toString().removeSuffix("/"),
                    anonKey = "anon-key",
                    googleWebClientId = "web-client-id",
                )
            val restartedAuth = SupabaseSharedAuth(config, SupabaseHttpTransport(config, OkHttpClient(), Json), sessionStore, clock)
            server.enqueue(MockResponse().setResponseCode(400))

            val result = restartedAuth.accessToken()

            assertSyncError(result, SyncError.Auth)
            assertNull(sessionStore.session)
            assertNull(restartedAuth.currentSession())
        }

    @Test
    fun `terminal sign out clear wins over an in-flight session restore`() {
        val interleavingStore = InterleavingSessionStore(storedSession(expiresAt = 1_800_000_000L))
        val config =
            SupabaseConfig(
                url = server.url("/").toString().removeSuffix("/"),
                anonKey = "anon-key",
                googleWebClientId = "web-client-id",
            )
        val restartedAuth =
            SupabaseSharedAuth(
                config,
                SupabaseHttpTransport(config, OkHttpClient(), Json),
                interleavingStore,
                clock,
            )
        val executor = Executors.newFixedThreadPool(2)
        server.enqueue(MockResponse().setResponseCode(204))

        try {
            val restoringSession = executor.submit<SharedSession?> { restartedAuth.currentSession() }
            assertTrue(interleavingStore.firstReadStarted.await(1, TimeUnit.SECONDS))

            val signingOut = executor.submit<Result<Unit>> { runBlocking { restartedAuth.signOut() } }
            assertFalse(interleavingStore.secondReadStarted.await(250, TimeUnit.MILLISECONDS))

            interleavingStore.allowFirstRead.countDown()
            assertEquals("user-1", restoringSession.get(1, TimeUnit.SECONDS)?.user?.id)
            assertTrue(signingOut.get(1, TimeUnit.SECONDS).isSuccess)
            assertNull(interleavingStore.session)
            assertNull(restartedAuth.currentSession())
        } finally {
            interleavingStore.allowFirstRead.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `sign in storage write and cache update win over an in-flight session restore`() {
        val interleavingStore = RestoreThenWriteSessionStore(storedSession(accessToken = "old-access-token", expiresAt = 1_800_000_000L))
        val config =
            SupabaseConfig(
                url = server.url("/").toString().removeSuffix("/"),
                anonKey = "anon-key",
                googleWebClientId = "web-client-id",
            )
        val restartedAuth =
            SupabaseSharedAuth(
                config,
                SupabaseHttpTransport(config, OkHttpClient(), Json),
                interleavingStore,
                clock,
            )
        val executor = Executors.newFixedThreadPool(2)
        server.enqueue(MockResponse().setResponseCode(200).setBody(sessionResponse("new-access-token", "new-refresh-token")))

        try {
            val restoringSession = executor.submit<SharedSession?> { restartedAuth.currentSession() }
            assertTrue(interleavingStore.firstReadStarted.await(1, TimeUnit.SECONDS))

            val signingIn =
                executor.submit<Result<SharedSession>> {
                    runBlocking { restartedAuth.signInWithGoogle("google-id-token", "request-nonce") }
                }
            assertTrue(server.takeRequest(1, TimeUnit.SECONDS) != null)
            assertFalse(interleavingStore.writeStarted.await(250, TimeUnit.MILLISECONDS))

            interleavingStore.allowFirstRead.countDown()
            assertEquals("old-access-token", restoringSession.get(1, TimeUnit.SECONDS)?.accessToken)
            assertTrue(interleavingStore.writeStarted.await(1, TimeUnit.SECONDS))
            assertEquals("new-access-token", signingIn.get(1, TimeUnit.SECONDS).getOrThrow().accessToken)
            assertEquals("new-access-token", restartedAuth.currentSession()?.accessToken)
            assertEquals("new-access-token", interleavingStore.session?.accessToken)
        } finally {
            interleavingStore.allowFirstRead.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `reset clears a concurrent sign in from memory and encrypted storage`() {
        val interleavingStore = BlockingWriteSessionStore()
        val config =
            SupabaseConfig(
                url = server.url("/").toString().removeSuffix("/"),
                anonKey = "anon-key",
                googleWebClientId = "web-client-id",
            )
        val restartedAuth =
            SupabaseSharedAuth(
                config,
                SupabaseHttpTransport(config, OkHttpClient(), Json),
                interleavingStore,
                clock,
            )
        val executor = Executors.newFixedThreadPool(2)
        server.enqueue(MockResponse().setResponseCode(200).setBody(sessionResponse()))

        try {
            val signingIn =
                executor.submit<Result<SharedSession>> {
                    runBlocking { restartedAuth.signInWithGoogle("google-id-token", "request-nonce") }
                }
            assertTrue(interleavingStore.writeStarted.await(1, TimeUnit.SECONDS))

            val resetting =
                executor.submit<Unit> {
                    runBlocking { restartedAuth.resetLocalSession(interleavingStore::clearAll) }
                }
            interleavingStore.allowWrite.countDown()

            assertTrue(signingIn.get(1, TimeUnit.SECONDS).isSuccess)
            resetting.get(1, TimeUnit.SECONDS)
            assertEquals(1, interleavingStore.clearAllCalls)
            assertNull(interleavingStore.session)
            assertNull(restartedAuth.currentSession())
        } finally {
            interleavingStore.allowWrite.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `rpc without a session fails with auth and does not issue a request`() =
        runTest {
            val result = workspaceRpc.createWorkspace("Budget")

            assertSyncError(result, SyncError.Auth)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `post applies a per-call deadline and maps the timeout to network`() =
        runTest {
            val config =
                SupabaseConfig(
                    url = server.url("/").toString().removeSuffix("/"),
                    anonKey = "anon-key",
                )
            val http = SupabaseHttpTransport(config, OkHttpClient(), Json)
            server.enqueue(
                MockResponse()
                    .setBodyDelay(500, TimeUnit.MILLISECONDS)
                    .setBody("{}"),
            )

            val result =
                http.post(
                    path = "functions/v1/create-ad-reward-token",
                    payload = buildJsonObject { },
                    callTimeoutMillis = 50,
                )

            assertSyncError(result, SyncError.Network)
            assertTrue(server.takeRequest(1, TimeUnit.SECONDS) != null)
        }

    @Test
    fun `cancelling an in-flight post preserves caller cancellation`() =
        runTest {
            val config =
                SupabaseConfig(
                    url = server.url("/").toString().removeSuffix("/"),
                    anonKey = "anon-key",
                )
            val http = SupabaseHttpTransport(config, OkHttpClient(), Json)
            server.enqueue(
                MockResponse()
                    .setBodyDelay(5, TimeUnit.SECONDS)
                    .setBody("{}"),
            )

            val request =
                async(start = CoroutineStart.UNDISPATCHED) {
                    http.post(
                        path = "rest/v1/rpc/get_ad_reward_state",
                        payload = buildJsonObject { },
                        accessToken = "access-token",
                    )
                }
            assertTrue(server.takeRequest(1, TimeUnit.SECONDS) != null)

            request.cancel(CancellationException("caller cancelled"))
            val thrown =
                try {
                    request.await()
                    error("the cancelled request should not complete")
                } catch (error: CancellationException) {
                    error
                }

            assertEquals("caller cancelled", thrown.message)
            assertFalse(thrown is SyncException)
        }

    @Test
    fun `membership denied direct workspace REST read maps to auth and exposes no rows`() =
        runTest {
            signIn()
            server.enqueue(MockResponse().setResponseCode(403).setBody("forbidden"))

            val result = workspaceRpc.currentWorkspace()

            assertSyncError(result, SyncError.Auth)
            val request = server.takeRequest()
            assertEquals(
                "/rest/v1/workspace_members?select=workspace:workspaces!inner(id,name,owner_id,created_at)&active=eq.true&limit=1",
                request.path,
            )
            assertEquals("Bearer shared-access-token", request.getHeader("Authorization"))
        }

    @Test
    fun `journal rpc decodes a single push operation returned in an array`() =
        runTest {
            signIn()
            server.enqueue(MockResponse().setResponseCode(200).setBody("[$validOperationJson]"))

            val result =
                journalRpc.pushOperation(
                    workspaceId = "workspace-1",
                    idempotencyKey = "operation-1",
                    baseSequence = 4,
                    deviceId = "device-1",
                    entityKind = "transaction",
                    entityId = "transaction-1",
                    payload = null,
                    tombstone = false,
                )

            assertEquals("operation-1", result.getOrThrow().id)
            assertEquals(5L, result.getOrThrow().serverSequence)
            server.takeRequest()
        }

    @Test
    fun `journal rpc decodes a single resolve conflict operation returned as an object`() =
        runTest {
            signIn()
            server.enqueue(MockResponse().setResponseCode(200).setBody(validOperationJson))

            val result = journalRpc.resolveConflict("conflict-1", "operation-1")

            assertEquals("operation-1", result.getOrThrow().id)
            assertEquals(5L, result.getOrThrow().serverSequence)
            server.takeRequest()
        }

    @Test
    fun `journal rpc rejects empty and multiple operation response arrays`() =
        runTest {
            signIn()
            server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
            server.enqueue(MockResponse().setResponseCode(200).setBody("[$validOperationJson,$validOperationJson]"))

            val emptyResult =
                journalRpc.pushOperation(
                    workspaceId = "workspace-1",
                    idempotencyKey = "operation-empty",
                    baseSequence = 4,
                    deviceId = "device-1",
                    entityKind = "transaction",
                    entityId = "transaction-1",
                    payload = null,
                    tombstone = false,
                )
            val multipleResult =
                journalRpc.pushOperation(
                    workspaceId = "workspace-1",
                    idempotencyKey = "operation-multiple",
                    baseSequence = 4,
                    deviceId = "device-1",
                    entityKind = "transaction",
                    entityId = "transaction-1",
                    payload = null,
                    tombstone = false,
                )

            assertTrue(emptyResult.isFailure)
            assertTrue(multipleResult.isFailure)
            server.takeRequest()
            server.takeRequest()
        }

    @Test
    fun `workspace rpc uses the Supabase bearer session and RPC parameter names`() =
        runTest {
            signIn()
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"id":"workspace-1","name":"Budget","owner_id":"user-1","created_at":"2026-07-29T12:00:00Z"}""",
                ),
            )

            val workspace = workspaceRpc.createWorkspace("Budget").getOrThrow()

            val request = server.takeRequest()
            val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("/rest/v1/rpc/create_workspace", request.path)
            assertEquals("Bearer shared-access-token", request.getHeader("Authorization"))
            assertEquals("anon-key", request.getHeader("apikey"))
            assertEquals("Budget", body["p_name"]?.jsonPrimitive?.content)
            assertEquals("workspace-1", workspace.id)
        }

    @Test
    fun `journal rpc preserves payload and maps forbidden response to auth failure`() =
        runTest {
            signIn()
            server.enqueue(MockResponse().setResponseCode(403).setBody("forbidden"))

            val result =
                journalRpc.pushOperation(
                    workspaceId = "workspace-1",
                    idempotencyKey = "operation-1",
                    baseSequence = 4,
                    deviceId = "device-1",
                    entityKind = "transaction",
                    entityId = "transaction-1",
                    payload = buildJsonObject { put("amount", "42.50") },
                    tombstone = false,
                )

            val request = server.takeRequest()
            val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("/rest/v1/rpc/push_operation", request.path)
            assertEquals(
                "42.50",
                body["p_payload"]
                    ?.jsonObject
                    ?.get("amount")
                    ?.jsonPrimitive
                    ?.content,
            )
            assertTrue(result.exceptionOrNull() is SyncException)
            assertEquals(SyncError.Auth, (result.exceptionOrNull() as SyncException).syncError)
        }

    @Test
    fun `journal rpc maps Supabase HTTP statuses to sync errors`() =
        runTest {
            signIn()
            val statusMappings =
                listOf(
                    401 to SyncError.Auth,
                    409 to SyncError.Conflict,
                    429 to SyncError.Quota,
                    500 to SyncError.Server,
                    404 to SyncError.Unknown,
                )

            statusMappings.forEachIndexed { index, (status, expectedError) ->
                server.enqueue(MockResponse().setResponseCode(status))
                val result =
                    journalRpc.pushOperation(
                        workspaceId = "workspace-1",
                        idempotencyKey = "operation-$index",
                        baseSequence = 0,
                        deviceId = "device-1",
                        entityKind = "account",
                        entityId = "account-1",
                        payload = null,
                        tombstone = true,
                    )

                assertSyncError(result, expectedError)
                server.takeRequest()
            }
        }

    @Test
    fun `journal rpc maps only SQLSTATE 42501 on HTTP400 to auth`() =
        runTest {
            signIn()
            server.enqueue(
                MockResponse()
                    .setResponseCode(400)
                    .setBody("""{"code":"42501","message":"row-level security policy"}"""),
            )
            val membershipDenied =
                journalRpc.pushOperation(
                    workspaceId = "workspace-1",
                    idempotencyKey = "operation-denied",
                    baseSequence = 0,
                    deviceId = "device-1",
                    entityKind = "account",
                    entityId = "account-1",
                    payload = null,
                    tombstone = true,
                )
            assertSyncError(membershipDenied, SyncError.Auth)
            server.takeRequest()

            server.enqueue(
                MockResponse()
                    .setResponseCode(400)
                    .setBody("""{"code":"425010","message":"bad request"}"""),
            )
            val unrelatedBadRequest =
                journalRpc.pushOperation(
                    workspaceId = "workspace-1",
                    idempotencyKey = "operation-bad-request",
                    baseSequence = 0,
                    deviceId = "device-1",
                    entityKind = "account",
                    entityId = "account-1",
                    payload = null,
                    tombstone = true,
                )
            assertSyncError(unrelatedBadRequest, SyncError.Unknown)
            server.takeRequest()
        }

    @Test
    fun `void workspace RPCs succeed on empty 204 responses`() =
        runTest {
            signIn()
            repeat(3) { server.enqueue(MockResponse().setResponseCode(204)) }

            workspaceRpc.revokeInvite("invite-1").getOrThrow()
            workspaceRpc.leaveWorkspace("workspace-1").getOrThrow()
            workspaceRpc.deleteWorkspace("workspace-1").getOrThrow()

            assertEquals("/rest/v1/rpc/revoke_invite", server.takeRequest().path)
            assertEquals("/rest/v1/rpc/leave_workspace", server.takeRequest().path)
            assertEquals("/rest/v1/rpc/delete_workspace", server.takeRequest().path)
        }

    private suspend fun signIn() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                sessionResponse(),
            ),
        )
        auth.signInWithGoogle("google-id-token", "request-nonce").getOrThrow()
        server.takeRequest()
    }

    private fun sessionResponse(
        accessToken: String = "shared-access-token",
        refreshToken: String = "shared-refresh-token",
    ): String =
        """{"access_token":"$accessToken","refresh_token":"$refreshToken","expires_at":1700003600,"user":{"id":"user-1","email":"member@example.com"}}"""

    private fun storedSession(
        accessToken: String = "shared-access-token",
        refreshToken: String = "shared-refresh-token",
        expiresAt: Long,
    ): StoredSharedSession =
        StoredSharedSession(
            userId = "user-1",
            userEmail = "member@example.com",
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessTokenExpiresAtEpochSeconds = expiresAt,
        )

    private class RecordingSessionStore : SharedSessionStore {
        var session: StoredSharedSession? = null

        override fun readSharedSession(): StoredSharedSession? = session

        override fun writeSharedSession(session: StoredSharedSession) {
            this.session = session
        }

        override fun clearSharedSession() {
            session = null
        }
    }

    private class RecordingAuthSessionLifecycle : AuthSessionLifecycle {
        private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
        val invalidationCount = AtomicInteger()

        override fun addInvalidationListener(listener: () -> Unit) {
            listeners += listener
        }

        override fun invalidate() {
            invalidationCount.incrementAndGet()
            listeners.forEach { it() }
        }
    }

    private class InterleavingSessionStore(
        @Volatile var session: StoredSharedSession?,
    ) : SharedSessionStore {
        private val readCount = AtomicInteger()
        val firstReadStarted = CountDownLatch(1)
        val secondReadStarted = CountDownLatch(1)
        val allowFirstRead = CountDownLatch(1)

        override fun readSharedSession(): StoredSharedSession? {
            val snapshot = session
            when (readCount.incrementAndGet()) {
                1 -> {
                    firstReadStarted.countDown()
                    check(allowFirstRead.await(1, TimeUnit.SECONDS)) { "first session read was not released" }
                }
                2 -> secondReadStarted.countDown()
            }
            return snapshot
        }

        override fun clearSharedSession() {
            session = null
        }
    }

    private class RestoreThenWriteSessionStore(
        @Volatile var session: StoredSharedSession?,
    ) : SharedSessionStore {
        val firstReadStarted = CountDownLatch(1)
        val allowFirstRead = CountDownLatch(1)
        val writeStarted = CountDownLatch(1)

        override fun readSharedSession(): StoredSharedSession? {
            val snapshot = session
            firstReadStarted.countDown()
            check(allowFirstRead.await(1, TimeUnit.SECONDS)) { "session restore was not released" }
            return snapshot
        }

        override fun writeSharedSession(session: StoredSharedSession) {
            writeStarted.countDown()
            this.session = session
        }
    }

    private class BlockingWriteSessionStore : SharedSessionStore {
        @Volatile var session: StoredSharedSession? = null
        val writeStarted = CountDownLatch(1)
        val allowWrite = CountDownLatch(1)
        var clearAllCalls = 0

        override fun writeSharedSession(session: StoredSharedSession) {
            writeStarted.countDown()
            check(allowWrite.await(1, TimeUnit.SECONDS)) { "session write was not released" }
            this.session = session
        }

        override fun clearSharedSession() {
            session = null
        }

        fun clearAll() {
            clearAllCalls++
            session = null
        }
    }

    private fun assertSyncError(
        result: Result<*>,
        expected: SyncError,
    ) {
        val exception = result.exceptionOrNull() as? SyncException
        assertEquals(expected, exception?.syncError)
    }
}
