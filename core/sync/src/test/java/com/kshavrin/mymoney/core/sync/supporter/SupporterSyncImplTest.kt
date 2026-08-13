package com.kshavrin.mymoney.core.sync.supporter

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.datastore.AppSettingsRepositoryImpl
import com.kshavrin.mymoney.core.datastore.supporter.SupporterPurchaseStore
import com.kshavrin.mymoney.core.datastore.supporter.SupporterPurchaseStoreImpl
import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import com.kshavrin.mymoney.core.domain.supporter.SupporterRepository
import com.kshavrin.mymoney.core.domain.supporter.SupporterState
import com.kshavrin.mymoney.core.network.shared.SharedAuth
import com.kshavrin.mymoney.core.network.shared.SharedSession
import com.kshavrin.mymoney.core.network.shared.SharedUser
import com.kshavrin.mymoney.core.network.shared.SupabaseConfig
import com.kshavrin.mymoney.core.network.shared.SupabaseHttpTransport
import com.kshavrin.mymoney.core.network.shared.SupabaseSupporterApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

class SupporterSyncImplTest {
    private lateinit var file: java.io.File
    private lateinit var dataStoreJob: Job
    private lateinit var settings: AppSettingsRepositoryImpl
    private lateinit var purchaseStore: SupporterPurchaseStore
    private lateinit var auth: FakeSharedAuth
    private lateinit var repository: RecordingSupporterRepository
    private lateinit var server: RecordingHttpServer
    private lateinit var sync: SupporterSyncImpl

    @Before
    fun setUp() {
        file = Files.createTempFile("supporter-sync", ".preferences_pb").toFile()
        file.delete()
        dataStoreJob = Job()
        val dataStore = createDataStore(dataStoreJob)
        settings = AppSettingsRepositoryImpl(dataStore)
        purchaseStore = SupporterPurchaseStoreImpl(dataStore)
        auth = FakeSharedAuth()
        repository = RecordingSupporterRepository()
        server = RecordingHttpServer()
        val config = SupabaseConfig(url = "http://127.0.0.1:${server.port}", anonKey = "anon-key")
        val http = createHttpTransport(config)
        sync =
            SupporterSyncImpl(
                auth = auth,
                api = SupabaseSupporterApi(http),
                supporterRepository = repository,
                supporterPurchaseStore = purchaseStore,
                dispatcher = UnconfinedTestDispatcher(),
            )
    }

    @After
    fun tearDown() {
        server.close()
        dataStoreJob.cancel()
        file.delete()
    }

    @Test
    fun `authenticated purchase persists local state and delivers the owner outbox`() =
        runTest {
            server.enqueue(HttpResponse(201, "{}"))
            val outcome = purchasedOutcome()

            sync.syncPurchase(outcome).getOrThrow()

            val persisted = settings.settings.first()
            assertTrue(persisted.supporterBadgeEarned)
            assertEquals(1, persisted.supportPurchaseCount)
            assertEquals(setOf("purchase-token"), persisted.supporterPurchaseTokens)
            assertTrue(purchaseStore.pendingPurchases("user-1").getOrThrow().isEmpty())
            assertEquals(1, server.requests.size)
            val request = server.requests.single()
            assertEquals("POST", request.method)
            assertEquals("/rest/v1/supporter_purchases", request.path)
            assertEquals("Bearer access-token", request.headers["authorization"])
            assertEquals("purchase-token", Json.parseToJsonElement(request.body).jsonObject["purchase_token"]?.jsonPrimitive?.content)
        }

    @Test
    fun `anonymous purchase stays local and never creates a network request`() =
        runTest {
            auth.session = null

            sync.syncPurchase(purchasedOutcome()).getOrThrow()

            val persisted = settings.settings.first()
            assertTrue(persisted.supporterBadgeEarned)
            assertEquals(1, persisted.supportPurchaseCount)
            assertTrue(purchaseStore.pendingPurchases("user-1").getOrThrow().isEmpty())
            assertTrue(server.requests.isEmpty())
        }

    @Test
    fun `network failure leaves local support and authenticated retry restores and merges remote state`() =
        runTest {
            val outcome = purchasedOutcome()
            server.enqueue(HttpResponse(500, "{\"message\":\"temporary outage\"}"))

            val firstAttempt = sync.syncPurchase(outcome)

            assertTrue(firstAttempt.isFailure)
            assertEquals(listOf(outcome), purchaseStore.pendingPurchases("user-1").getOrThrow())
            assertEquals(1, settings.settings.first().supportPurchaseCount)

            server.enqueue(HttpResponse(201, "{}"))
            server.enqueue(HttpResponse(200, "[{\"id\":\"purchase-1\"}]", "0-0/1"))

            sync.restore().getOrThrow()

            assertTrue(purchaseStore.pendingPurchases("user-1").getOrThrow().isEmpty())
            assertEquals(1 to true, repository.lastMerge)
            assertEquals(listOf("POST", "POST", "GET"), server.requests.map { it.method })
            assertEquals("/rest/v1/supporter_purchases?select=id&user_id=eq.user-1", server.requests.last().path)
        }

    @Test
    fun `restore propagates an access-token failure without consuming the durable outbox`() =
        runTest {
            purchaseStore.recordPurchase(purchasedOutcome(), ownerUserId = "user-1").getOrThrow()
            val failure = SyncException(SyncError.Auth)
            auth.accessTokenResult = Result.failure(failure)

            val result = sync.restore()

            assertTrue(result.isFailure)
            assertEquals(failure, result.exceptionOrNull())
            assertTrue(purchaseStore.pendingPurchases("user-1").getOrThrow().isNotEmpty())
            assertTrue(server.requests.isEmpty())
        }

    private fun createDataStore(job: Job): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(job + Dispatchers.IO),
            produceFile = { file },
        )

    private fun createHttpTransport(config: SupabaseConfig): SupabaseHttpTransport {
        val clientClass = Class.forName("okhttp3.OkHttpClient")
        val client = clientClass.getDeclaredConstructor().newInstance()
        val constructor =
            SupabaseHttpTransport::class.java.getConstructor(
                SupabaseConfig::class.java,
                clientClass,
                Json::class.java,
            )
        return constructor.newInstance(config, client, Json) as SupabaseHttpTransport
    }

    private fun purchasedOutcome() =
        PurchaseOutcome.Purchased(
            productId = "coffee_small",
            purchaseToken = "purchase-token",
            purchasedAtMillis = 1_724_256_789_000L,
        )

    private class FakeSharedAuth : SharedAuth {
        var session: SharedSession? =
            SharedSession(
                user = SharedUser(id = "user-1", email = "user@example.com"),
                accessToken = "access-token",
            )
        var accessTokenResult: Result<String>? = null

        override fun currentSession(): SharedSession? = session

        override suspend fun accessToken(): Result<String> =
            accessTokenResult ?: super<SharedAuth>.accessToken()

        override suspend fun signInWithGoogle(
            googleIdToken: String,
            nonce: String,
        ): Result<SharedSession> = Result.failure(UnsupportedOperationException())

        override suspend fun signOut(): Result<Unit> = Result.success(Unit)
    }

    private class RecordingSupporterRepository : SupporterRepository {
        var lastMerge: Pair<Int, Boolean>? = null

        override fun state(): Flow<SupporterState> = flowOf(SupporterState(false, 0))

        override suspend fun recordPurchase(outcome: PurchaseOutcome.Purchased): Result<Unit> = Result.success(Unit)

        override suspend fun mergeRemote(remoteCount: Int, remoteBadge: Boolean): Result<Unit> {
            lastMerge = remoteCount to remoteBadge
            return Result.success(Unit)
        }
    }

    private data class HttpResponse(
        val code: Int,
        val body: String,
        val contentRange: String? = null,
    )

    private data class CapturedRequest(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: String,
    )

    private class RecordingHttpServer : Closeable {
        private val socket = ServerSocket(0)
        private val responses = LinkedBlockingQueue<HttpResponse>()
        private val acceptExecutor = Executors.newSingleThreadExecutor()
        private val connectionExecutor = Executors.newCachedThreadPool()
        val requests = CopyOnWriteArrayList<CapturedRequest>()
        val port: Int get() = socket.localPort

        init {
            acceptExecutor.execute {
                try {
                    while (!socket.isClosed) {
                        val connection = socket.accept()
                        connectionExecutor.execute { handle(connection) }
                    }
                } catch (_: Exception) {
                }
            }
        }

        fun enqueue(response: HttpResponse) {
            responses.add(response)
        }

        override fun close() {
            socket.close()
            acceptExecutor.shutdownNow()
            connectionExecutor.shutdownNow()
        }

        private fun handle(connection: java.net.Socket) {
            connection.use { socket ->
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                val requestLine = reader.readLine() ?: return
                val headers = buildMap {
                    while (true) {
                        val line = reader.readLine()
                        if (line.isNullOrEmpty()) break
                        val separator = line.indexOf(':')
                        if (separator > 0) {
                            put(line.substring(0, separator).lowercase(), line.substring(separator + 1).trim())
                        }
                    }
                }
                val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                val body = CharArray(contentLength)
                var offset = 0
                while (offset < contentLength) {
                    val read = reader.read(body, offset, contentLength - offset)
                    if (read < 0) break
                    offset += read
                }
                val requestParts = requestLine.split(' ', limit = 3)
                requests +=
                    CapturedRequest(
                        method = requestParts[0],
                        path = requestParts[1],
                        headers = headers,
                        body = String(body, 0, offset),
                    )
                val response = responses.take()
                val responseBody = response.body.toByteArray(StandardCharsets.UTF_8)
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
                writer.write("HTTP/1.1 ${response.code} ${statusText(response.code)}\r\n")
                writer.write("Content-Type: application/json\r\n")
                writer.write("Content-Length: ${responseBody.size}\r\n")
                response.contentRange?.let { writer.write("Content-Range: $it\r\n") }
                writer.write("Connection: close\r\n\r\n")
                writer.flush()
                socket.getOutputStream().write(responseBody)
                socket.getOutputStream().flush()
            }
        }

        private fun statusText(code: Int): String =
            when (code) {
                200 -> "OK"
                201 -> "Created"
                500 -> "Internal Server Error"
                else -> "Error"
            }
    }
}
