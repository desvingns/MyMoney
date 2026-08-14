package com.kshavrin.mymoney.core.network.shared

import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class SupabaseSharedRealtimeTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient()
    }

    @After
    fun tearDown() {
        client.dispatcher.executorService.shutdownNow()
        client.connectionPool.evictAll()
        server.shutdown()
    }

    @Test
    fun `unconfigured realtime closes with server error without opening a socket`() =
        runBlocking {
            val realtime =
                SupabaseSharedRealtime(
                    config = SupabaseConfig(url = "", anonKey = ""),
                    client = client,
                    json = Json,
                )

            val failure = runCatching { realtime.events("workspace-1", "access-token").first() }.exceptionOrNull()

            assertEquals(SyncError.Server, (failure as SyncException).syncError)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `join authorizes a private workspace topic and broadcasts only operation hints`() =
        runBlocking {
            val opened = CompletableFuture<WebSocket>()
            val joinMessage = CompletableFuture<String>()
            server.enqueue(
                MockResponse().withWebSocketUpgrade(
                    object : WebSocketListener() {
                        override fun onOpen(
                            webSocket: WebSocket,
                            response: okhttp3.Response,
                        ) {
                            opened.complete(webSocket)
                        }

                        override fun onMessage(
                            webSocket: WebSocket,
                            text: String,
                        ) {
                            joinMessage.complete(text)
                        }
                    },
                ),
            )
            val realtime = configuredRealtime()
            val events = LinkedBlockingQueue<SharedRealtimeEvent>()
            val failure = CompletableFuture<Throwable>()
            val collection =
                launch(Dispatchers.Default) {
                    runCatching { realtime.events("workspace-1", "access-token").collect(events::add) }
                        .onFailure(failure::complete)
                }

            try {
                yield()
                val socket = opened.awaitSocket(failure)
                val request = server.takeRequest(2, TimeUnit.SECONDS)
                assertNotNull(request)
                assertEquals("Bearer access-token", request?.getHeader("Authorization"))
                assertEquals("anon-key", request?.getHeader("apikey"))
                val join = Json.parseToJsonElement(joinMessage.get(2, TimeUnit.SECONDS)).jsonObject
                assertEquals("realtime:workspace:workspace-1:operations", join["topic"]?.jsonPrimitive?.content)
                assertEquals("phx_join", join["event"]?.jsonPrimitive?.content)
                assertEquals("1", join["ref"]?.jsonPrimitive?.content)
                assertEquals("1", join["join_ref"]?.jsonPrimitive?.content)
                val payload = join["payload"]!!.jsonObject
                assertEquals("access-token", payload["access_token"]?.jsonPrimitive?.content)
                assertTrue(payload["config"]!!.jsonObject["private"]!!.jsonPrimitive.boolean)

                socket.send("""{"event":"phx_reply","ref":"1","payload":{"status":"ok"}}""")
                assertEquals(SharedRealtimeEvent.Connected, events.awaitItem())

                socket.send("""{"event":"broadcast","payload":{"event":"unrelated"}}""")
                socket.send("""{"event":"broadcast","payload":{"event":"operation_available"}}""")
                assertEquals(SharedRealtimeEvent.OperationAvailable, events.awaitItem())
                assertNull(events.poll(200, TimeUnit.MILLISECONDS))

                socket.close(1000, "done")
                val error = failure.get(2, TimeUnit.SECONDS)
                assertTrue(error is IllegalStateException)
            } finally {
                collection.cancelAndJoin()
            }
        }

    @Test
    fun `join rejection closes the stream and a wrong reply ref is ignored`() =
        runBlocking {
            val opened = CompletableFuture<WebSocket>()
            server.enqueue(
                MockResponse().withWebSocketUpgrade(
                    object : WebSocketListener() {
                        override fun onOpen(
                            webSocket: WebSocket,
                            response: okhttp3.Response,
                        ) {
                            opened.complete(webSocket)
                        }
                    },
                ),
            )
            val realtime = configuredRealtime()
            val events = LinkedBlockingQueue<SharedRealtimeEvent>()
            val failure = CompletableFuture<Throwable>()
            val collection =
                launch(Dispatchers.Default) {
                    runCatching { realtime.events("workspace-1", "access-token").collect(events::add) }
                        .onFailure(failure::complete)
                }

            try {
                yield()
                val socket = opened.awaitSocket(failure)
                checkNotNull(server.takeRequest(2, TimeUnit.SECONDS))
                socket.send("""{"event":"phx_reply","ref":"99","payload":{"status":"error"}}""")
                assertNull(events.poll(200, TimeUnit.MILLISECONDS))
                socket.send("""{"event":"phx_reply","ref":"1","payload":{"status":"error"}}""")
                val error = failure.get(2, TimeUnit.SECONDS)
                assertEquals(SyncError.Server, (error as SyncException).syncError)
            } finally {
                collection.cancelAndJoin()
            }
        }

    @Test
    fun `join entitlement rejection closes the stream with the typed entitlement error`() =
        runBlocking {
            val opened = CompletableFuture<WebSocket>()
            server.enqueue(
                MockResponse().withWebSocketUpgrade(
                    object : WebSocketListener() {
                        override fun onOpen(
                            webSocket: WebSocket,
                            response: okhttp3.Response,
                        ) {
                            opened.complete(webSocket)
                        }
                    },
                ),
            )
            val realtime = configuredRealtime()
            val failure = CompletableFuture<Throwable>()
            val collection =
                launch(Dispatchers.Default) {
                    runCatching { realtime.events("workspace-1", "access-token").collect() }
                        .onFailure(failure::complete)
                }

            try {
                yield()
                val socket = opened.awaitSocket(failure)
                checkNotNull(server.takeRequest(2, TimeUnit.SECONDS))
                socket.send(
                    """{"event":"phx_reply","ref":"1","payload":{"status":"error","response":{"message":"entitlement_required"}}}""",
                )

                val error = failure.get(2, TimeUnit.SECONDS) as SyncException
                assertEquals(SyncError.EntitlementRequired, error.syncError)
            } finally {
                collection.cancelAndJoin()
            }
        }

    @Test
    fun `realtime entitlement phx error closes the stream with the typed entitlement error`() =
        runBlocking {
            val opened = CompletableFuture<WebSocket>()
            server.enqueue(
                MockResponse().withWebSocketUpgrade(
                    object : WebSocketListener() {
                        override fun onOpen(
                            webSocket: WebSocket,
                            response: okhttp3.Response,
                        ) {
                            opened.complete(webSocket)
                        }
                    },
                ),
            )
            val realtime = configuredRealtime()
            val failure = CompletableFuture<Throwable>()
            val collection =
                launch(Dispatchers.Default) {
                    runCatching { realtime.events("workspace-1", "access-token").collect() }
                        .onFailure(failure::complete)
                }

            try {
                yield()
                val socket = opened.awaitSocket(failure)
                checkNotNull(server.takeRequest(2, TimeUnit.SECONDS))
                socket.send("""{"event":"phx_error","payload":{"message":"entitlement_required"}}""")

                val error = failure.get(2, TimeUnit.SECONDS) as SyncException
                assertEquals(SyncError.EntitlementRequired, error.syncError)
            } finally {
                collection.cancelAndJoin()
            }
        }

    @Test
    fun `malformed messages and unknown events do not create false operation notifications`() =
        runBlocking {
            val opened = CompletableFuture<WebSocket>()
            server.enqueue(
                MockResponse().withWebSocketUpgrade(
                    object : WebSocketListener() {
                        override fun onOpen(
                            webSocket: WebSocket,
                            response: okhttp3.Response,
                        ) {
                            opened.complete(webSocket)
                        }
                    },
                ),
            )
            val realtime = configuredRealtime()
            val events = LinkedBlockingQueue<SharedRealtimeEvent>()
            val failure = CompletableFuture<Throwable>()
            val collection =
                launch(Dispatchers.Default) {
                    runCatching { realtime.events("workspace-1", "access-token").collect(events::add) }
                        .onFailure(failure::complete)
                }

            try {
                yield()
                val socket = opened.awaitSocket(failure)
                checkNotNull(server.takeRequest(2, TimeUnit.SECONDS))
                socket.send("not-json")
                socket.send("""{"event":"system","payload":{"status":"ok"}}""")
                assertNull(events.poll(200, TimeUnit.MILLISECONDS))
                socket.send("""{"event":"phx_reply","ref":"1","payload":{"status":"ok"}}""")
                assertEquals(SharedRealtimeEvent.Connected, events.awaitItem())
                socket.send("""{"event":"broadcast","payload":{}}""")
                assertNull(events.poll(200, TimeUnit.MILLISECONDS))
                socket.close(1000, "done")
                failure.get(2, TimeUnit.SECONDS)
            } finally {
                collection.cancelAndJoin()
            }
            Unit
        }

    private fun LinkedBlockingQueue<SharedRealtimeEvent>.awaitItem(): SharedRealtimeEvent =
        checkNotNull(poll(2, TimeUnit.SECONDS))

    private fun CompletableFuture<WebSocket>.awaitSocket(failure: CompletableFuture<Throwable>): WebSocket =
        runCatching { get(2, TimeUnit.SECONDS) }
            .getOrElse { throw AssertionError("Realtime connection did not open", failure.getNow(it)) }

    private fun configuredRealtime(): SupabaseSharedRealtime =
        SupabaseSharedRealtime(
            config =
                SupabaseConfig(
                    url = server.url("/").toString().removeSuffix("/"),
                    anonKey = "anon-key",
                ),
            client = client,
            json = Json,
        )
}
