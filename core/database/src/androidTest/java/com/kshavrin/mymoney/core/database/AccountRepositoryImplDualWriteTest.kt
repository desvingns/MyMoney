package com.kshavrin.mymoney.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kshavrin.mymoney.core.database.entity.AccountEntity
import com.kshavrin.mymoney.core.database.entity.CurrencyEntity
import com.kshavrin.mymoney.core.database.journal.OperationPayloadCodec
import com.kshavrin.mymoney.core.database.repository.AccountRepositoryImpl
import com.kshavrin.mymoney.core.database.transaction.RoomTransactionRunner
import com.kshavrin.mymoney.core.domain.sync.DeviceIdProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class AccountRepositoryImplDualWriteTest {
    private lateinit var db: MoneyDatabase
    private lateinit var repository: AccountRepositoryImpl
    private val codec = OperationPayloadCodec()
    private val fixedInstant = Instant.parse("2026-06-26T10:15:30Z")
    private val deviceIdProvider =
        object : DeviceIdProvider {
            override suspend fun deviceId(): String = DEVICE_ID
        }

    private var currencyId = 0L

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
                AccountRepositoryImpl(
                    dao = db.accountDao(),
                    operationDao = db.operationDao(),
                    payloadCodec = codec,
                    deviceIdProvider = deviceIdProvider,
                    clock = Clock.fixed(fixedInstant, ZoneOffset.UTC),
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
        }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `setDefault journals each changed account row`() =
        runTest {
            val oldDefaultId =
                db.accountDao().upsert(
                    accountEntity(uuid = OLD_DEFAULT_UUID, name = "Cash", isDefault = true, sortOrder = 0),
                )
            val targetId =
                db.accountDao().upsert(
                    accountEntity(uuid = TARGET_UUID, name = "Card", isDefault = false, sortOrder = 1),
                )
            val untouchedId =
                db.accountDao().upsert(
                    accountEntity(uuid = UNTOUCHED_UUID, name = "Savings", isDefault = false, sortOrder = 2),
                )

            repository.setDefault(targetId)

            val oldDefault = db.accountDao().findById(oldDefaultId)
            val target = db.accountDao().findById(targetId)
            val untouched = db.accountDao().findById(untouchedId)

            assertNotNull(oldDefault)
            assertNotNull(target)
            assertNotNull(untouched)
            assertFalse(oldDefault!!.isDefault)
            assertTrue(target!!.isDefault)
            assertFalse(untouched!!.isDefault)
            assertEquals(DEVICE_ID, oldDefault.deviceId)
            assertEquals(DEVICE_ID, target.deviceId)
            assertEquals("seed-device", untouched.deviceId)
            assertEquals(fixedInstant.toEpochMilli(), oldDefault.updatedAt)
            assertEquals(fixedInstant.toEpochMilli(), target.updatedAt)
            assertEquals(1_700_000_000_100L, untouched.updatedAt)

            val ops = db.operationDao().unsyncedLocal()
            assertEquals(2, ops.size)
            assertEquals(setOf(OLD_DEFAULT_UUID, TARGET_UUID), ops.map { it.entityUuid }.toSet())
            assertEquals(setOf("Upsert"), ops.map { it.opType }.toSet())
            assertEquals(setOf("Account"), ops.map { it.entityKind }.toSet())
            assertEquals(setOf(DEVICE_ID), ops.map { it.deviceId }.toSet())

            val payloads =
                ops.associate { op ->
                    op.entityUuid to codec.decodeAccount(requireNotNull(op.payload))
                }
            assertFalse(payloads.getValue(OLD_DEFAULT_UUID).isDefault)
            assertTrue(payloads.getValue(TARGET_UUID).isDefault)
        }

    private fun accountEntity(
        uuid: String,
        name: String,
        isDefault: Boolean,
        sortOrder: Int,
    ) = AccountEntity(
        uuid = uuid,
        deviceId = "seed-device",
        name = name,
        currencyId = currencyId,
        initialBalance = 100.0,
        type = "cash",
        colorHex = "#7AC794",
        iconKey = "ic_account_cash",
        isDefault = isDefault,
        sortOrder = sortOrder,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_100L,
        isArchived = false,
    )

    private companion object {
        const val DEVICE_ID = "device-123"
        const val OLD_DEFAULT_UUID = "account-old-default"
        const val TARGET_UUID = "account-target"
        const val UNTOUCHED_UUID = "account-untouched"
    }
}
