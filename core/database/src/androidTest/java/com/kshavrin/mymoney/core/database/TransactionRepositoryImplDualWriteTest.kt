package com.kshavrin.mymoney.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kshavrin.mymoney.core.database.entity.AccountEntity
import com.kshavrin.mymoney.core.database.entity.CurrencyEntity
import com.kshavrin.mymoney.core.database.entity.TransactionEntity
import com.kshavrin.mymoney.core.database.journal.OperationPayloadCodec
import com.kshavrin.mymoney.core.database.repository.TransactionRepositoryImpl
import com.kshavrin.mymoney.core.database.transaction.RoomTransactionRunner
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.sync.DeviceIdProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class TransactionRepositoryImplDualWriteTest {
    private lateinit var db: MoneyDatabase
    private lateinit var repository: TransactionRepositoryImpl

    private val codec = OperationPayloadCodec()
    private val fixedInstant = Instant.parse("2026-06-26T10:15:30Z")
    private val fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val deviceIdProvider =
        object : DeviceIdProvider {
            override suspend fun deviceId(): String = DEVICE_ID
        }

    private var currencyId = 0L
    private var sourceAccountId = 0L
    private var destinationAccountId = 0L

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
            repository =
                TransactionRepositoryImpl(
                    dao = db.transactionDao(),
                    accountDao = db.accountDao(),
                    categoryDao = db.categoryDao(),
                    operationDao = db.operationDao(),
                    payloadCodec = codec,
                    deviceIdProvider = deviceIdProvider,
                    clock = fixedClock,
                    transactionRunner = RoomTransactionRunner(db),
                    ioDispatcher = Dispatchers.IO,
                )

            currencyId =
                db.currencyDao().upsert(
                    CurrencyEntity(
                        code = "USD",
                        symbol = "$",
                        name = "US Dollar",
                        decimalDigits = 2,
                        isActive = true,
                        sortOrder = 0,
                    ),
                )
            sourceAccountId =
                db.accountDao().upsert(
                    accountEntity(name = "Cash", uuid = SOURCE_ACCOUNT_UUID),
                )
            destinationAccountId =
                db.accountDao().upsert(
                    accountEntity(name = "Card", uuid = DESTINATION_ACCOUNT_UUID),
                )
        }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `upsert generates journal upsert with transfer snapshot and stable identifiers`() =
        runTest {
            val id =
                repository.upsert(
                    Transaction(
                        id = 0L,
                        kind = TransactionKind.Transfer,
                        amount = BigDecimal("123.45"),
                        currencyId = currencyId,
                        accountId = sourceAccountId,
                        categoryId = null,
                        note = "move",
                        occurredAt = Instant.parse("2026-06-25T10:00:00Z"),
                        createdAt = Instant.parse("2026-06-25T09:00:00Z"),
                        updatedAt = Instant.parse("2026-06-25T09:30:00Z"),
                        isDeleted = false,
                        toAccountId = destinationAccountId,
                        toAmount = BigDecimal("120.00"),
                        exchangeRate = 0.972,
                    ),
                )

            val stored = db.transactionDao().findById(id)
            assertNotNull(stored)
            assertTrue(stored!!.uuid.isNotBlank())
            assertEquals(DEVICE_ID, stored.deviceId)
            assertEquals(fixedInstant.toEpochMilli(), stored.updatedAt)

            val op = db.operationDao().opsForEntity(stored.uuid).single()
            assertEquals(DEVICE_ID, op.deviceId)
            assertEquals("Transaction", op.entityKind)
            assertEquals("Upsert", op.opType)
            assertEquals(stored.uuid, op.entityUuid)
            assertEquals(fixedInstant.toEpochMilli(), op.updatedAt)

            val snapshot = codec.decodeTransaction(requireNotNull(op.payload))
            assertEquals(stored.uuid, snapshot.uuid)
            assertEquals(DEVICE_ID, snapshot.deviceId)
            assertEquals("transfer", snapshot.kind)
            assertEquals("123.45", snapshot.amount)
            assertEquals(SOURCE_ACCOUNT_UUID, snapshot.accountUuid)
            assertEquals(DESTINATION_ACCOUNT_UUID, snapshot.toAccountUuid)
            assertNull(snapshot.categoryUuid)
            assertEquals("120.0", snapshot.toAmount)
            assertEquals(0.972, snapshot.exchangeRate)
        }

    @Test
    fun `softDelete marks row deleted and emits delete operation`() =
        runTest {
            val id =
                db.transactionDao().upsert(
                    transactionEntity(
                        uuid = "tx-delete",
                        isDeleted = false,
                    ),
                )

            repository.softDelete(id, Instant.parse("2026-06-27T00:00:00Z"))

            val stored = db.transactionDao().findById(id)
            assertNotNull(stored)
            assertTrue(stored!!.isDeleted)
            assertEquals(DEVICE_ID, stored.deviceId)
            assertEquals(fixedInstant.toEpochMilli(), stored.updatedAt)

            val op = db.operationDao().opsForEntity("tx-delete").single()
            assertEquals("Delete", op.opType)
            assertEquals("Transaction", op.entityKind)
            assertEquals("tx-delete", op.entityUuid)
            assertEquals(DEVICE_ID, op.deviceId)
            assertEquals(fixedInstant.toEpochMilli(), op.updatedAt)
            assertNull(op.payload)
        }

    @Test
    fun `restore emits upsert snapshot for deleted transfer`() =
        runTest {
            val id =
                db.transactionDao().upsert(
                    transactionEntity(
                        uuid = "tx-restore",
                        isDeleted = true,
                    ),
                )

            repository.restore(id, Instant.parse("2026-06-28T00:00:00Z"))

            val stored = db.transactionDao().findById(id)
            assertNotNull(stored)
            assertFalse(stored!!.isDeleted)
            assertEquals(DEVICE_ID, stored.deviceId)
            assertEquals(fixedInstant.toEpochMilli(), stored.updatedAt)

            val op = db.operationDao().opsForEntity("tx-restore").single()
            assertEquals("Upsert", op.opType)
            assertEquals("Transaction", op.entityKind)
            assertEquals("tx-restore", op.entityUuid)
            assertEquals(DEVICE_ID, op.deviceId)
            assertEquals(fixedInstant.toEpochMilli(), op.updatedAt)

            val snapshot = codec.decodeTransaction(requireNotNull(op.payload))
            assertEquals("tx-restore", snapshot.uuid)
            assertEquals(DEVICE_ID, snapshot.deviceId)
            assertEquals("transfer", snapshot.kind)
            assertEquals(SOURCE_ACCOUNT_UUID, snapshot.accountUuid)
            assertEquals(DESTINATION_ACCOUNT_UUID, snapshot.toAccountUuid)
            assertNull(snapshot.categoryUuid)
            assertEquals("120.0", snapshot.toAmount)
            assertEquals(false, snapshot.isDeleted)
        }

    private fun accountEntity(
        name: String,
        uuid: String,
        isDefault: Boolean = false,
    ) = AccountEntity(
        name = name,
        uuid = uuid,
        deviceId = "seed-device",
        currencyId = currencyId,
        initialBalance = 0.0,
        type = "cash",
        colorHex = "#7AC794",
        iconKey = "ic_account_cash",
        isDefault = isDefault,
        sortOrder = 0,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
        isArchived = false,
    )

    private fun transactionEntity(
        uuid: String,
        isDeleted: Boolean,
    ) = TransactionEntity(
        uuid = uuid,
        deviceId = "seed-device",
        kind = "transfer",
        amount = 123.45,
        currencyId = currencyId,
        accountId = sourceAccountId,
        categoryId = null,
        note = "move",
        occurredAt = 1_700_000_000_000L,
        createdAt = 1_700_000_000_100L,
        updatedAt = 1_700_000_000_200L,
        isDeleted = isDeleted,
        toAccountId = destinationAccountId,
        toAmount = 120.0,
        exchangeRate = 0.972,
    )

    private companion object {
        const val DEVICE_ID = "device-123"
        const val SOURCE_ACCOUNT_UUID = "account-source-uuid"
        const val DESTINATION_ACCOUNT_UUID = "account-destination-uuid"
    }
}
