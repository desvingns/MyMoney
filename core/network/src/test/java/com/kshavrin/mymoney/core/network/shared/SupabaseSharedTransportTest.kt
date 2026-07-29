package com.kshavrin.mymoney.core.network.shared

import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SupabaseSharedTransportTest {
    private lateinit var server: MockWebServer
    private lateinit var auth: SupabaseSharedAuth
    private lateinit var workspaceRpc: SupabaseSharedWorkspaceRpc
    private lateinit var journalRpc: SupabaseSharedJournalRpc

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
        auth = SupabaseSharedAuth(config, http)
        workspaceRpc = SupabaseSharedWorkspaceRpc(auth, http)
        journalRpc = SupabaseSharedJournalRpc(auth, http)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `Google id token exchange forwards nonce without an authorization header`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"access_token":"shared-access-token","user":{"id":"user-1","email":"member@example.com"}}""",
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
        assertEquals("member@example.com", session.user.email)
    }

    @Test
    fun `workspace rpc uses the Supabase bearer session and RPC parameter names`() = runTest {
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
    fun `journal rpc preserves payload and maps forbidden response to auth failure`() = runTest {
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
        assertEquals("42.50", body["p_payload"]?.jsonObject?.get("amount")?.jsonPrimitive?.content)
        assertTrue(result.exceptionOrNull() is SyncException)
        assertEquals(SyncError.Auth, (result.exceptionOrNull() as SyncException).syncError)
    }

    @Test
    fun `void workspace RPCs succeed on empty 204 responses`() = runTest {
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
                """{"access_token":"shared-access-token","user":{"id":"user-1","email":"member@example.com"}}""",
            ),
        )
        auth.signInWithGoogle("google-id-token", "request-nonce").getOrThrow()
        server.takeRequest()
    }
}
