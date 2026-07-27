package com.kshavrin.mymoney.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kshavrin.mymoney.core.database.entity.CategoryEntity
import com.kshavrin.mymoney.core.database.journal.OperationPayloadCodec
import com.kshavrin.mymoney.core.database.repository.CategoryRepositoryImpl
import com.kshavrin.mymoney.core.database.transaction.RoomTransactionRunner
import com.kshavrin.mymoney.core.domain.sync.DeviceIdProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class CategoryRepositoryImplDualWriteTest {
    private lateinit var db: MoneyDatabase
    private lateinit var repository: CategoryRepositoryImpl

    private val fixedInstant = Instant.parse("2026-06-26T10:15:30Z")
    private val deviceIdProvider =
        object : DeviceIdProvider {
            override suspend fun deviceId(): String = DEVICE_ID
        }

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    MoneyDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        repository =
            CategoryRepositoryImpl(
                dao = db.categoryDao(),
                operationDao = db.operationDao(),
                payloadCodec = OperationPayloadCodec(db.currencyDao()),
                deviceIdProvider = deviceIdProvider,
                clock = Clock.fixed(fixedInstant, ZoneOffset.UTC),
                transactionRunner = RoomTransactionRunner(db),
                ioDispatcher = Dispatchers.IO,
            )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `archive marks category archived and emits delete operation`() =
        runTest {
            val id =
                db.categoryDao().upsert(
                    CategoryEntity(
                        uuid = CATEGORY_UUID,
                        deviceId = "seed-device",
                        name = "Food",
                        kind = "expense",
                        iconKey = "ic_cat_food",
                        colorHex = "#E07AAE",
                        textColor = "#FFFFFF",
                        sortOrder = 2,
                        isDefault = false,
                        isArchived = false,
                        createdAt = 1_700_000_000_000L,
                        updatedAt = 1_700_000_000_100L,
                    ),
                )

            repository.archive(id)

            val stored = db.categoryDao().findById(id)
            assertNotNull(stored)
            assertTrue(stored!!.isArchived)
            assertEquals(DEVICE_ID, stored.deviceId)
            assertEquals(fixedInstant.toEpochMilli(), stored.updatedAt)

            val op = db.operationDao().opsForEntity(CATEGORY_UUID).single()
            assertEquals(DEVICE_ID, op.deviceId)
            assertEquals("Category", op.entityKind)
            assertEquals("Delete", op.opType)
            assertEquals(CATEGORY_UUID, op.entityUuid)
            assertEquals(fixedInstant.toEpochMilli(), op.updatedAt)
            assertNull(op.payload)
        }

    private companion object {
        const val DEVICE_ID = "device-123"
        const val CATEGORY_UUID = "category-uuid"
    }
}
