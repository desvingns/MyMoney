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
    fun `postPurchase sends the exact user product token and purchase timestamp`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))
            val outcome = purchasedOutcome()

            api.postPurchase("user-1", outcome, "access-token").getOrThrow()

            val request = server.takeRequest()
            val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("/rest/v1/supporter_purchases", request.path)
            assertEquals("anon-key", request.getHeader("apikey"))
            assertEquals("Bearer access-token", request.getHeader("Authorization"))
            assertEquals("user-1", body["user_id"]?.jsonPrimitive?.content)
            assertEquals("coffee_small", body["product_id"]?.jsonPrimitive?.content)
            assertEquals("purchase-token", body["purchase_token"]?.jsonPrimitive?.content)
            assertEquals("2024-08-21T16:13:09Z", body["purchased_at"]?.jsonPrimitive?.content)
        }

    @Test
    fun `duplicate purchase token conflict is treated as successful idempotent delivery`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(409)
                    .setBody(
                        """{"code":"23505","message":"duplicate key value violates unique constraint \"supporter_purchases_purchase_token_key\"","details":"Key (purchase_token)=(purchase-token) already exists."}""",
                    ),
            )
            server.enqueue(MockResponse().setResponseCode(200).setBody("[{\"id\":\"purchase-1\"}]"))

            assertTrue(api.postPurchase("user-1", purchasedOutcome(), "access-token").isSuccess)
            server.takeRequest()
            val verification = server.takeRequest()
            assertEquals(
                "/rest/v1/supporter_purchases?select=id&user_id=eq.user-1&purchase_token=eq.purchase-token",
                verification.path,
            )
            assertEquals("Bearer access-token", verification.getHeader("Authorization"))
        }

    @Test
    fun `duplicate token verification encodes reserved owner and token values`() =
        runTest {
            val userId = "user+1/тест"
            val purchaseToken = "token+/?&=токен"
            server.enqueue(
                MockResponse()
                    .setResponseCode(409)
                    .setBody(
                        """{"code":"23505","message":"duplicate key value violates unique constraint \"supporter_purchases_purchase_token_key\"","details":"Key (purchase_token)=(purchase-token) already exists."}""",
                    ),
            )
            server.enqueue(MockResponse().setResponseCode(200).setBody("[{\"id\":\"purchase-1\"}]"))

            assertTrue(
                api
                    .postPurchase(
                        userId = userId,
                        outcome = purchasedOutcome(purchaseToken),
                        accessToken = "access-token",
                    ).isSuccess,
            )

            server.takeRequest()
            val verification = server.takeRequest()
            assertEquals(
                "/rest/v1/supporter_purchases?select=id&user_id=eq.user%2B1%2F%D1%82%D0%B5%D1%81%D1%82&purchase_token=eq.token%2B%2F%3F%26%3D%D1%82%D0%BE%D0%BA%D0%B5%D0%BD",
                verification.path,
            )
        }

    @Test
    fun `duplicate token hidden by owner filter remains a conflict`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(409)
                    .setBody(
                        """{"code":"23505","message":"duplicate key value violates unique constraint \"supporter_purchases_purchase_token_key\"","details":"Key (purchase_token)=(purchase-token) already exists."}""",
                    ),
            )
            server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

            val result = api.postPurchase("user-2", purchasedOutcome(), "access-token")

            val failure = result.exceptionOrNull()
            assertTrue(failure is SyncException)
            assertEquals(SyncError.Conflict, (failure as SyncException).syncError)
            assertTrue(failure is SupabasePostgrestConflictException)
            server.takeRequest()
            val verification = server.takeRequest()
            assertEquals(
                "/rest/v1/supporter_purchases?select=id&user_id=eq.user-2&purchase_token=eq.purchase-token",
                verification.path,
            )
        }

    @Test
    fun `duplicate token verification failure preserves the original conflict`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(409)
                    .setBody(
                        """{"code":"23505","message":"duplicate key value violates unique constraint \"supporter_purchases_purchase_token_key\"","details":"Key (purchase_token)=(purchase-token) already exists."}""",
                    ),
            )
            server.enqueue(MockResponse().setResponseCode(500).setBody("server failure"))

            val result = api.postPurchase("user-1", purchasedOutcome(), "access-token")

            val failure = result.exceptionOrNull()
            assertTrue(failure is SyncException)
            assertEquals(SyncError.Conflict, (failure as SyncException).syncError)
            assertTrue(failure is SupabasePostgrestConflictException)
            server.takeRequest()
            server.takeRequest()
        }

    @Test
    fun `non-token unique conflict is not swallowed as a duplicate purchase`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(409)
                    .setBody(
                        """{"code":"23505","message":"duplicate key value violates unique constraint \"supporters_pkey\"","details":"Key (user_id)=(user-1) already exists."}""",
                    ),
            )

            val result = api.postPurchase("user-1", purchasedOutcome(), "access-token")

            val failure = result.exceptionOrNull()
            assertTrue(failure is SyncException)
            assertEquals(SyncError.Conflict, (failure as SyncException).syncError)
            server.takeRequest()
        }

    @Test
    fun `getState requests an exact count and parses the Content-Range total`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Range", "0-2/3")
                    .setBody("[{\"id\":\"purchase-1\"}]"),
            )

            val state = api.getState("user-1", "access-token").getOrThrow()

            assertEquals(3, state.purchaseCount)
            assertTrue(state.badgeEarned)
            val request = server.takeRequest()
            assertEquals("/rest/v1/supporter_purchases?select=id&user_id=eq.user-1", request.path)
            assertEquals("count=exact", request.getHeader("Prefer"))
            assertEquals("Bearer access-token", request.getHeader("Authorization"))
        }

    @Test
    fun `getState reports no badge for an exact zero count`() =
        runTest {
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
