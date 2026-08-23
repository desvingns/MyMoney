package com.kshavrin.mymoney.core.datastore.supporter

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.kshavrin.mymoney.core.datastore.AppSettingsRepositoryImpl
import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SupporterPurchaseStoreTest {
    private lateinit var file: File
    private val storeJobs = mutableListOf<Job>()

    @Before
    fun setUp() {
        file = Files.createTempFile("supporter-purchases", ".preferences_pb").toFile()
        file.delete()
    }

    @After
    fun tearDown() {
        storeJobs.forEach { it.cancel() }
        file.delete()
    }

    @Test
    fun `recordPurchase atomically persists local state and an owner-scoped outbox row`() =
        runTest(UnconfinedTestDispatcher()) {
            val dataStore = createDataStore()
            val settings = AppSettingsRepositoryImpl(dataStore)
            val store = SupporterPurchaseStoreImpl(dataStore)
            val outcome = purchasedOutcome("token-a")

            store.recordPurchase(outcome, ownerUserId = "user-a").getOrThrow()

            val persisted = settings.settings.first()
            assertTrue(persisted.supporterBadgeEarned)
            assertTrue(persisted.supporterActivityRecorded)
            assertEquals(1, persisted.supportPurchaseCount)
            assertEquals(setOf("token-a"), persisted.supporterPurchaseTokens)
            assertEquals(listOf(outcome), store.pendingPurchases("user-a").getOrThrow())
        }

    @Test
    fun `pending purchases survive a fresh datastore instance and remain filtered by owner`() =
        runTest(UnconfinedTestDispatcher()) {
            val writeJob = Job()
            val writeStore = SupporterPurchaseStoreImpl(createDataStore(writeJob))
            val userA = purchasedOutcome("token-a")
            val userB = purchasedOutcome("token-b")
            writeStore.recordPurchase(userA, ownerUserId = "user-a").getOrThrow()
            writeStore.recordPurchase(userB, ownerUserId = "user-b").getOrThrow()
            writeJob.cancelAndJoin()

            val readJob = Job()
            try {
                val readDataStore = createDataStore(readJob)
                val readStore = SupporterPurchaseStoreImpl(readDataStore)
                val readSettings = AppSettingsRepositoryImpl(readDataStore)

                assertEquals(listOf(userA), readStore.pendingPurchases("user-a").getOrThrow())
                assertEquals(listOf(userB), readStore.pendingPurchases("user-b").getOrThrow())
                assertEquals(emptyList<PurchaseOutcome.Purchased>(), readStore.pendingPurchases("user-c").getOrThrow())
                assertEquals(2, readSettings.settings.first().supportPurchaseCount)
                assertTrue(readSettings.settings.first().supporterActivityRecorded)
                assertEquals(setOf("token-a", "token-b"), readSettings.settings.first().supporterPurchaseTokens)
            } finally {
                readJob.cancelAndJoin()
            }
        }

    @Test
    fun `duplicate purchase token is idempotent for count tokens and outbox`() =
        runTest(UnconfinedTestDispatcher()) {
            val dataStore = createDataStore()
            val settings = AppSettingsRepositoryImpl(dataStore)
            val store = SupporterPurchaseStoreImpl(dataStore)
            val outcome = purchasedOutcome("token-a")

            store.recordPurchase(outcome, ownerUserId = "user-a").getOrThrow()
            store.recordPurchase(outcome, ownerUserId = "user-a").getOrThrow()

            val persisted = settings.settings.first()
            assertEquals(1, persisted.supportPurchaseCount)
            assertEquals(setOf("token-a"), persisted.supporterPurchaseTokens)
            assertEquals(listOf(outcome), store.pendingPurchases("user-a").getOrThrow())
        }

    @Test
    fun `removing one owner purchase does not remove another owner purchase`() =
        runTest(UnconfinedTestDispatcher()) {
            val store = SupporterPurchaseStoreImpl(createDataStore())
            val userA = purchasedOutcome("token-a")
            val userB = purchasedOutcome("token-b")
            store.recordPurchase(userA, ownerUserId = "user-a").getOrThrow()
            store.recordPurchase(userB, ownerUserId = "user-b").getOrThrow()

            store.remove(ownerUserId = "user-a", purchaseToken = "token-a").getOrThrow()

            assertEquals(emptyList<PurchaseOutcome.Purchased>(), store.pendingPurchases("user-a").getOrThrow())
            assertEquals(listOf(userB), store.pendingPurchases("user-b").getOrThrow())
        }

    @Test
    fun `anonymous purchase changes local supporter state without creating an outbox row`() =
        runTest(UnconfinedTestDispatcher()) {
            val dataStore = createDataStore()
            val settings = AppSettingsRepositoryImpl(dataStore)
            val store = SupporterPurchaseStoreImpl(dataStore)

            store.recordPurchase(purchasedOutcome("anonymous-token"), ownerUserId = null).getOrThrow()

            val persisted = settings.settings.first()
            assertTrue(persisted.supporterBadgeEarned)
            assertTrue(persisted.supporterActivityRecorded)
            assertEquals(1, persisted.supportPurchaseCount)
            assertEquals(emptyList<PurchaseOutcome.Purchased>(), store.pendingPurchases("any-user").getOrThrow())
        }

    private fun createDataStore(job: Job = Job()): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(job + Dispatchers.IO),
            produceFile = { file },
        ).also { storeJobs += job }

    private fun purchasedOutcome(token: String) =
        PurchaseOutcome.Purchased(
            productId = "coffee_small",
            purchaseToken = token,
            purchasedAtMillis = 1_723_456_789_000L,
        )
}
