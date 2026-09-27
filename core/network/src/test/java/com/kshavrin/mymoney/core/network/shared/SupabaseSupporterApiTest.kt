package com.kshavrin.mymoney.core.network.shared

import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SupabaseSupporterApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: SupabaseSupporterApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val config =
            SupabaseConfig(
                url = server.url("/").toString().removeSuffix("/"),
                anonKey = "anon-key",
            )
        api = SupabaseSupporterApi(SupabaseHttpTransport(config, OkHttpClient(), Json))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `postPurchase sends only the receipt to the authenticated verification endpoint`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("{\"verified\":true}"))
            api.postPurchase("user-1", purchasedOutcome("token+/?&=токен"), "access-token").getOrThrow()
            val request = server.takeRequest()
            val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("/functions/v1/verify-supporter-purchase", request.path)
            assertEquals("anon-key", request.getHeader("apikey"))
            assertEquals("Bearer access-token", request.getHeader("Authorization"))
            assertEquals(setOf("purchase_token", "expected_user_id"), body.keys)
            assertEquals("user-1", body["expected_user_id"]?.jsonPrimitive?.content)
            assertEquals("token+/?&=токен", body["purchase_token"]?.jsonPrimitive?.content)
        }

    @Test
    fun `server owner conflict is preserved without a client duplicate bypass`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(409).setBody("{\"error\":\"purchase_already_bound\"}"))
            val failure = api.postPurchase("user-1", purchasedOutcome(), "access-token").exceptionOrNull()
            assertTrue(failure is SyncException)
            assertEquals(SyncError.Conflict, (failure as SyncException).syncError)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `failed Play verification does not become successful delivery`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(422).setBody("{\"error\":\"invalid_supporter_purchase\"}"))
            assertTrue(api.postPurchase("user-1", purchasedOutcome(), "access-token").isFailure)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `getState requests an exact count and parses the Content-Range total`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("{\"refreshed\":true}"))
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Range", "0-2/3")
                    .setBody("[{\"id\":\"purchase-1\"}]"),
            )

            val state = api.getState("user-1", "access-token").getOrThrow()

            assertEquals(3, state.purchaseCount)
            assertTrue(state.badgeEarned)
            val refresh = server.takeRequest()
            assertEquals("/functions/v1/verify-supporter-purchase", refresh.path)
            assertEquals("{\"refresh\":true,\"expected_user_id\":\"user-1\"}", refresh.body.readUtf8())
            val request = server.takeRequest()
            assertEquals("/rest/v1/supporter_purchases?select=id&user_id=eq.user-1&verified_at=not.is.null", request.path)
            assertEquals("count=exact", request.getHeader("Prefer"))
            assertEquals("Bearer access-token", request.getHeader("Authorization"))
        }

    @Test
    fun `getState reports no badge for an exact zero count`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("{\"refreshed\":true}"))
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Range", "*/0")
                    .setBody("[]"),
            )

            val state = api.getState("user-1", "access-token").getOrThrow()

            assertEquals(0, state.purchaseCount)
            assertFalse(state.badgeEarned)
            server.takeRequest()
        }

    @Test
    fun `getState fails when Supabase omits the exact count`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("{\"refreshed\":true}"))
            server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

            val result = api.getState("user-1", "access-token")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalStateException)
            server.takeRequest()
        }

    private fun purchasedOutcome(purchaseToken: String = "purchase-token") =
        PurchaseOutcome.Purchased(
            productId = "coffee_small",
            purchaseToken = purchaseToken,
            purchasedAtMillis = 1_724_256_789_000L,
        )
}
