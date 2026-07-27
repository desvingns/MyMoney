package com.kshavrin.mymoney.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kshavrin.mymoney.core.database.entity.AccountEntity
import com.kshavrin.mymoney.core.database.entity.CategoryEntity
import com.kshavrin.mymoney.core.database.entity.CurrencyEntity
import com.kshavrin.mymoney.core.database.journal.JournalBootstrap
import com.kshavrin.mymoney.core.database.journal.OperationPayloadCodec
import com.kshavrin.mymoney.core.database.transaction.RoomTransactionRunner
import com.kshavrin.mymoney.core.datastore.CloudBinding
import com.kshavrin.mymoney.core.datastore.JournalSyncConfigStore
import com.kshavrin.mymoney.core.domain.sync.DeviceIdProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JournalBootstrapTest {
    private lateinit var db: MoneyDatabase
    private lateinit var configStore: FakeBootstrapConfigStore
    private lateinit var bootstrap: JournalBootstrap

    private var currencyId = 0L
    private val deviceId = "device-bootstrap-test"

    @Before
    fun setUp() =
        runTest {
            db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext(),
                        MoneyDatabase::class.java,
                    ).allowMainThreadQueries()
                    .build()
            configStore = FakeBootstrapConfigStore()
            bootstrap =
                JournalBootstrap(
                    accountDao = db.accountDao(),
                    categoryDao = db.categoryDao(),
                    transactionDao = db.transactionDao(),
                    operationDao = db.operationDao(),
                    payloadCodec = OperationPayloadCodec(db.currencyDao()),
                    deviceIdProvider =
                        object : DeviceIdProvider {
                            override suspend fun deviceId(): String = deviceId
                        },
                    transactionRunner = RoomTransactionRunner(db),
                    configStore = configStore,
                )
            currencyId =
                db.currencyDao().upsert(
                    CurrencyEntity(
                        code = "RUB",
                        symbol = "₽",
                        name = "Russian Ruble",
                        decimalDigits = 2,
                        isActive = true,
                        sortOrder = 0,
                    ),
                )
        }

    @After
    fun tearDown() {
        db.close()
    }

    // ─── idempotency ─────────────────────────────────────────────────────────

    @Test
    fun `runIfNeeded is a no-op when bootstrap flag is already set`() =
        runTest {
            val account = seedAccount(uuid = "acc-idem-uuid", name = "Cash")
            configStore.markBootstrapDone()

            bootstrap.runIfNeeded()

            val ops = db.operationDao().knownOpIds()
            assertTrue("no ops must be emitted when bootstrap already done", ops.isEmpty())
        }

    @Test
    fun `runIfNeeded marks bootstrap done after first run`() =
        runTest {
            seedAccount(uuid = "acc-done-uuid", name = "Savings")

            bootstrap.runIfNeeded()

            assertTrue("configStore must report bootstrap done after first run", configStore.isBootstrapDone())
        }

    @Test
    fun `runIfNeeded called twice emits ops only once`() =
        runTest {
            seedAccount(uuid = "acc-twice-uuid", name = "Account A")

            bootstrap.runIfNeeded()
            val opCountAfterFirst = db.operationDao().knownOpIds().size

            bootstrap.runIfNeeded()
            val opCountAfterSecond = db.operationDao().knownOpIds().size

            assertEquals(
                "second runIfNeeded must not emit additional ops",
                opCountAfterFirst,
                opCountAfterSecond,
            )
        }

    // ─── stamp missing device_id ──────────────────────────────────────────────

    @Test
    fun `runIfNeeded stamps blank device_id on accounts with the local deviceId`() =
        runTest {
            db.accountDao().upsert(
                accountEntity(uuid = "acc-stamp-uuid", name = "Wallet", deviceId = ""),
            )

            bootstrap.runIfNeeded()

            val account = db.accountDao().findByUuid("acc-stamp-uuid")
            assertEquals("device_id must be stamped by bootstrap", deviceId, account!!.deviceId)
        }

    @Test
    fun `runIfNeeded stamps blank device_id on categories`() =
        runTest {
            db.categoryDao().upsert(
                categoryEntity(uuid = "cat-stamp-uuid", name = "Food", deviceId = ""),
            )

            bootstrap.runIfNeeded()

            val category = db.categoryDao().findByUuid("cat-stamp-uuid")
            assertEquals("device_id must be stamped on category", deviceId, category!!.deviceId)
        }

    // ─── initial op emission ──────────────────────────────────────────────────

    @Test
    fun `runIfNeeded emits one Upsert op per active account not yet in the journal`() =
        runTest {
            seedAccount(uuid = "acc-op-uuid-1", name = "Cash")
            seedAccount(uuid = "acc-op-uuid-2", name = "Card")

            bootstrap.runIfNeeded()

            val entityUuids = db.operationDao().knownEntityUuids()
            assertTrue("op for acc-op-uuid-1 must be emitted", entityUuids.contains("acc-op-uuid-1"))
            assertTrue("op for acc-op-uuid-2 must be emitted", entityUuids.contains("acc-op-uuid-2"))
        }

    @Test
    fun `runIfNeeded emits one Upsert op per active category not yet in the journal`() =
        runTest {
            db.categoryDao().upsert(categoryEntity(uuid = "cat-boot-uuid", name = "Food"))

            bootstrap.runIfNeeded()

            val entityUuids = db.operationDao().knownEntityUuids()
            assertTrue("op for cat-boot-uuid must be emitted", entityUuids.contains("cat-boot-uuid"))
        }

    @Test
    fun `runIfNeeded does not emit a second op for an entity already in the journal`() =
        runTest {
            val accountUuid = "acc-known-uuid"
            seedAccount(uuid = accountUuid, name = "Pre-Known Account")
            val ops = db.operationDao().knownEntityUuids()
            assertFalse("precondition: entity should not be in journal before bootstrap", ops.contains(accountUuid))

            bootstrap.runIfNeeded()
            val countAfterFirst = db.operationDao().opsForEntity(accountUuid).size

            configStore.resetDone()
            bootstrap.runIfNeeded()
            val countAfterSecond = db.operationDao().opsForEntity(accountUuid).size

            assertEquals(
                "bootstrap must not emit duplicate op for already-known entity",
                countAfterFirst,
                countAfterSecond,
            )
        }

    @Test
    fun `runIfNeeded emits Delete op for an archived account`() =
        runTest {
            db.accountDao().upsert(
                accountEntity(uuid = "acc-archived-uuid", name = "Archived", isArchived = true),
            )

            bootstrap.runIfNeeded()

            val opsForEntity = db.operationDao().opsForEntity("acc-archived-uuid")
            assertEquals(1, opsForEntity.size)
            assertEquals("Delete op must be emitted for archived account", "Delete", opsForEntity.first().opType)
        }

    @Test
    fun `runIfNeeded on empty database emits no ops and marks bootstrap done`() =
        runTest {
            bootstrap.runIfNeeded()

            val ops = db.operationDao().knownOpIds()
            assertTrue("no ops must be emitted for empty database", ops.isEmpty())
            assertTrue("bootstrap must be marked done even for empty database", configStore.isBootstrapDone())
        }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private suspend fun seedAccount(
        uuid: String,
        name: String,
    ): Long =
        db.accountDao().upsert(accountEntity(uuid = uuid, name = name))

    private fun accountEntity(
        uuid: String,
        name: String,
        deviceId: String = "device-seeded",
        isArchived: Boolean = false,
    ) = AccountEntity(
        uuid = uuid,
        deviceId = deviceId,
        name = name,
        currencyId = currencyId,
        initialBalance = 0.0,
        type = "cash",
        colorHex = "#7AC794",
        iconKey = "ic_account_cash",
        isDefault = false,
        sortOrder = 0,
        createdAt = T1,
        updatedAt = T1,
        isArchived = isArchived,
    )

    private fun categoryEntity(
        uuid: String,
        name: String,
        deviceId: String = "device-seeded",
    ) = CategoryEntity(
        uuid = uuid,
        deviceId = deviceId,
        name = name,
        kind = "expense",
        iconKey = "ic_cat_food",
        colorHex = "#E07AAE",
        textColor = "#FFFFFF",
        sortOrder = 0,
        isDefault = false,
        isArchived = false,
        createdAt = T1,
        updatedAt = T1,
    )

    companion object {
        private const val T1 = 1_700_000_000_000L
    }

    // ─── fake config store ────────────────────────────────────────────────────

    private class FakeBootstrapConfigStore : JournalSyncConfigStore {
        private var currentBinding: CloudBinding? = null
        private var done: Boolean = false
        private val peerHighWater: MutableMap<String, Long> = mutableMapOf()

        fun resetDone() {
            done = false
        }

        override suspend fun binding(): CloudBinding? = currentBinding

        override suspend fun setBinding(binding: CloudBinding) {
            currentBinding = binding
        }

        override suspend fun clearBinding() {
            currentBinding = null
            peerHighWater.clear()
            done = false
        }

        override suspend fun peerHighWaterMs(fileId: String): Long = peerHighWater[fileId] ?: 0L

        override suspend fun setPeerHighWaterMs(
            fileId: String,
            modifiedAtMs: Long,
        ) {
            peerHighWater[fileId] = modifiedAtMs
        }

        override suspend fun isBootstrapDone(): Boolean = done

        override suspend fun markBootstrapDone() {
            done = true
        }

        override suspend fun clear() {
            currentBinding = null
            peerHighWater.clear()
            done = false
        }
    }
}
