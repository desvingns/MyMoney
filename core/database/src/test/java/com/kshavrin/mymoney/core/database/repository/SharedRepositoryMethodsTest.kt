package com.kshavrin.mymoney.core.database.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kshavrin.mymoney.core.database.MoneyDatabase
import com.kshavrin.mymoney.core.database.entity.CurrencyEntity
import com.kshavrin.mymoney.core.database.journal.OperationPayloadCodec
import com.kshavrin.mymoney.core.database.transaction.RoomTransactionRunner
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.sync.DeviceIdProvider
import kotlinx.coroutines.test.StandardTestDispatcher
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Verifies the non-journaling Shared-workspace repository methods against a real in-memory Room DB.
 *
 * Key invariants under test:
 * 1. applySharedUpsert inserts a new row when no row with that uuid exists.
 * 2. applySharedUpsert updates an EXISTING row by uuid, preserving its local Room id (no duplicate).
 * 3. applySharedUpsert does NOT write an OperationEntity row (non-journaling).
 * 4. Regular upsert() DOES write an OperationEntity row (contrasting baseline).
 * 5. applySharedArchive / applySharedDelete operate by uuid without writing journal rows.
 * 6. listAllIncludingArchived returns active AND archived rows.
 * 7. uuidForId / idForUuid are symmetric (round-trip).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SharedRepositoryMethodsTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC)
    private val deviceId = "test-device"
    private val deviceIdProvider =
        object : DeviceIdProvider {
            override suspend fun deviceId() = "test-device"
        }

    private lateinit var db: MoneyDatabase
    private lateinit var accountRepo: AccountRepositoryImpl
    private lateinit var categoryRepo: CategoryRepositoryImpl
    private lateinit var transactionRepo: TransactionRepositoryImpl

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    MoneyDatabase::class.java,
                ).allowMainThreadQueries()
                .build()

        val payloadCodec = OperationPayloadCodec(db.currencyDao())
        val runner = RoomTransactionRunner(db)

        accountRepo =
            AccountRepositoryImpl(
                dao = db.accountDao(),
                operationDao = db.operationDao(),
                payloadCodec = payloadCodec,
                deviceIdProvider = deviceIdProvider,
                clock = clock,
                transactionRunner = runner,
                ioDispatcher = dispatcher,
            )
        categoryRepo =
            CategoryRepositoryImpl(
                dao = db.categoryDao(),
                operationDao = db.operationDao(),
                payloadCodec = payloadCodec,
                deviceIdProvider = deviceIdProvider,
                clock = clock,
                transactionRunner = runner,
                ioDispatcher = dispatcher,
            )
        transactionRepo =
            TransactionRepositoryImpl(
                dao = db.transactionDao(),
                accountDao = db.accountDao(),
                categoryDao = db.categoryDao(),
                operationDao = db.operationDao(),
                payloadCodec = payloadCodec,
                deviceIdProvider = deviceIdProvider,
                clock = clock,
                transactionRunner = runner,
                ioDispatcher = dispatcher,
            )

        // Seed a currency so FK constraints on AccountEntity are satisfied.
        runTest(dispatcher) {
            db.currencyDao().upsert(
                CurrencyEntity(
                    id = 0L,
                    code = "USD",
                    symbol = "$",
                    name = "US Dollar",
                    decimalDigits = 2,
                    isActive = true,
                    sortOrder = 1,
                ),
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── AccountRepositoryImpl ──────────────────────────────────────────────

    @Test
    fun `applySharedUpsert account inserts new row when uuid is unknown`() =
        runTest(dispatcher) {
            val currencyId = db.currencyDao().findByCode("USD")!!.id
            accountRepo.applySharedUpsert(sampleAccount(currencyId = currencyId), ACCOUNT_UUID, deviceId)

            val found = db.accountDao().findByUuid(ACCOUNT_UUID)
            assertNotNull("Row must exist after applySharedUpsert", found)
            assertEquals(ACCOUNT_UUID, found!!.uuid)
            assertEquals("Cash", found.name)
        }

    @Test
    fun `applySharedUpsert account updates existing row by uuid preserving local id`() =
        runTest(dispatcher) {
            val currencyId = db.currencyDao().findByCode("USD")!!.id
            // First call inserts
            accountRepo.applySharedUpsert(sampleAccount(name = "Original", currencyId = currencyId), ACCOUNT_UUID, deviceId)
            val insertedId = db.accountDao().findByUuid(ACCOUNT_UUID)!!.id

            // Second call with same uuid updates in place
            accountRepo.applySharedUpsert(sampleAccount(name = "Updated", currencyId = currencyId), ACCOUNT_UUID, deviceId)

            val updated = db.accountDao().findByUuid(ACCOUNT_UUID)!!
            assertEquals("Updated", updated.name)
            assertEquals("Local Room id must be preserved, not duplicated", insertedId, updated.id)
            assertEquals(1, db.accountDao().listAll().size)
        }

    @Test
    fun `applySharedUpsert account does NOT write an OperationEntity row`() =
        runTest(dispatcher) {
            val currencyId = db.currencyDao().findByCode("USD")!!.id
            accountRepo.applySharedUpsert(sampleAccount(currencyId = currencyId), ACCOUNT_UUID, deviceId)
            val ops = db.operationDao().knownOpIds()
            assertTrue(
                "applySharedUpsert must not write to the op_journal (non-journaling)",
                ops.isEmpty(),
            )
        }

    @Test
    fun `regular account upsert DOES write an OperationEntity row as the baseline contrast`() =
        runTest(dispatcher) {
            val currencyId = db.currencyDao().findByCode("USD")!!.id
            accountRepo.upsert(sampleAccount(currencyId = currencyId))
            val ops = db.operationDao().knownOpIds()
            assertFalse(
                "Regular upsert() must write to op_journal (journaling)",
                ops.isEmpty(),
            )
        }

    @Test
    fun `applySharedArchive account sets is_archived without a journal entry`() =
        runTest(dispatcher) {
            val currencyId = db.currencyDao().findByCode("USD")!!.id
            accountRepo.applySharedUpsert(sampleAccount(currencyId = currencyId), ACCOUNT_UUID, deviceId)
            db.operationDao().deleteAll()

            accountRepo.applySharedArchive(ACCOUNT_UUID)

            val entity = db.accountDao().findByUuid(ACCOUNT_UUID)!!
            assertTrue("Account must be archived after applySharedArchive", entity.isArchived)
            assertTrue("applySharedArchive must not write journal rows", db.operationDao().knownOpIds().isEmpty())
        }

    @Test
    fun `listAllIncludingArchived returns both active and archived accounts`() =
        runTest(dispatcher) {
            val currencyId = db.currencyDao().findByCode("USD")!!.id
            accountRepo.applySharedUpsert(sampleAccount(name = "Active", currencyId = currencyId), "uuid-active", deviceId)
            accountRepo.applySharedUpsert(sampleAccount(name = "Archived", currencyId = currencyId), "uuid-archived", deviceId)
            accountRepo.applySharedArchive("uuid-archived")

            val all = accountRepo.listAllIncludingArchived()
            assertEquals("Both active and archived rows must be returned", 2, all.size)
            assertTrue(all.any { it.name == "Active" })
            assertTrue(all.any { it.name == "Archived" })
        }

    @Test
    fun `uuidForId and idForUuid are symmetric for accounts`() =
        runTest(dispatcher) {
            val currencyId = db.currencyDao().findByCode("USD")!!.id
            accountRepo.applySharedUpsert(sampleAccount(currencyId = currencyId), ACCOUNT_UUID, deviceId)
            val localId = db.accountDao().findByUuid(ACCOUNT_UUID)!!.id

            assertEquals(ACCOUNT_UUID, accountRepo.uuidForId(localId))
            assertEquals(localId, accountRepo.idForUuid(ACCOUNT_UUID))
        }

    @Test
    fun `uuidForId returns null for unknown id`() =
        runTest(dispatcher) {
            assertNull(accountRepo.uuidForId(99999L))
        }

    @Test
    fun `idForUuid returns null for unknown uuid`() =
        runTest(dispatcher) {
            assertNull(accountRepo.idForUuid("unknown-uuid"))
        }

    // ── CategoryRepositoryImpl ─────────────────────────────────────────────

    @Test
    fun `applySharedUpsert category inserts new row when uuid is unknown`() =
        runTest(dispatcher) {
            categoryRepo.applySharedUpsert(sampleCategory(), CATEGORY_UUID, deviceId)
            val found = db.categoryDao().findByUuid(CATEGORY_UUID)
            assertNotNull(found)
            assertEquals("Food", found!!.name)
        }

    @Test
    fun `applySharedUpsert category updates existing row preserving local id`() =
        runTest(dispatcher) {
            categoryRepo.applySharedUpsert(sampleCategory(name = "Original"), CATEGORY_UUID, deviceId)
            val firstId = db.categoryDao().findByUuid(CATEGORY_UUID)!!.id

            categoryRepo.applySharedUpsert(sampleCategory(name = "Renamed"), CATEGORY_UUID, deviceId)
            val updated = db.categoryDao().findByUuid(CATEGORY_UUID)!!

            assertEquals("Renamed", updated.name)
            assertEquals(firstId, updated.id)
            assertEquals(1, db.categoryDao().listAll().size)
        }

    @Test
    fun `applySharedUpsert category does NOT write an OperationEntity row`() =
        runTest(dispatcher) {
            categoryRepo.applySharedUpsert(sampleCategory(), CATEGORY_UUID, deviceId)
            assertTrue(db.operationDao().knownOpIds().isEmpty())
        }

    @Test
    fun `applySharedArchive category sets is_archived without a journal entry`() =
        runTest(dispatcher) {
            categoryRepo.applySharedUpsert(sampleCategory(), CATEGORY_UUID, deviceId)
            db.operationDao().deleteAll()

            categoryRepo.applySharedArchive(CATEGORY_UUID)

            val entity = db.categoryDao().findByUuid(CATEGORY_UUID)!!
            assertTrue(entity.isArchived)
            assertTrue(db.operationDao().knownOpIds().isEmpty())
        }

    @Test
    fun `uuidForId and idForUuid are symmetric for categories`() =
        runTest(dispatcher) {
            categoryRepo.applySharedUpsert(sampleCategory(), CATEGORY_UUID, deviceId)
            val localId = db.categoryDao().findByUuid(CATEGORY_UUID)!!.id
            assertEquals(CATEGORY_UUID, categoryRepo.uuidForId(localId))
            assertEquals(localId, categoryRepo.idForUuid(CATEGORY_UUID))
        }

    // ── TransactionRepositoryImpl ──────────────────────────────────────────

    @Test
    fun `applySharedUpsert transaction inserts new row when uuid is unknown`() =
        runTest(dispatcher) {
            val currencyId = db.currencyDao().findByCode("USD")!!.id
            val accountId = insertSeedAccount(currencyId)

            transactionRepo.applySharedUpsert(
                sampleTransaction(currencyId = currencyId, accountId = accountId),
                TX_UUID,
                deviceId,
            )
            val found = db.transactionDao().findByUuid(TX_UUID)
            assertNotNull(found)
        }

    @Test
    fun `applySharedUpsert transaction updates existing row preserving local id`() =
        runTest(dispatcher) {
            val currencyId = db.currencyDao().findByCode("USD")!!.id
            val accountId = insertSeedAccount(currencyId)

            transactionRepo.applySharedUpsert(
                sampleTransaction(currencyId = currencyId, accountId = accountId, amount = BigDecimal("10.00")),
                TX_UUID,
                deviceId,
            )
            val firstId = db.transactionDao().findByUuid(TX_UUID)!!.id

            transactionRepo.applySharedUpsert(
                sampleTransaction(currencyId = currencyId, accountId = accountId, amount = BigDecimal("20.00")),
                TX_UUID,
                deviceId,
            )
            val updated = db.transactionDao().findByUuid(TX_UUID)!!
            assertEquals(firstId, updated.id)
            assertEquals(1, db.transactionDao().listAll().size)
        }

    @Test
    fun `applySharedUpsert transaction does NOT write an OperationEntity row`() =
        runTest(dispatcher) {
            val currencyId = db.currencyDao().findByCode("USD")!!.id
            val accountId = insertSeedAccount(currencyId)
            transactionRepo.applySharedUpsert(
                sampleTransaction(currencyId = currencyId, accountId = accountId),
                TX_UUID,
                deviceId,
            )
            assertTrue(db.operationDao().knownOpIds().isEmpty())
        }

    @Test
    fun `applySharedDelete transaction soft-deletes by uuid without a journal entry`() =
        runTest(dispatcher) {
            val currencyId = db.currencyDao().findByCode("USD")!!.id
            val accountId = insertSeedAccount(currencyId)
            transactionRepo.applySharedUpsert(
                sampleTransaction(currencyId = currencyId, accountId = accountId),
                TX_UUID,
                deviceId,
            )
            db.operationDao().deleteAll()

            transactionRepo.applySharedDelete(TX_UUID, clock.instant())

            val deleted = db.transactionDao().findByUuid(TX_UUID)!!
            assertEquals(1, deleted.isDeleted.compareTo(false)) // is_deleted must be true
            assertTrue(db.operationDao().knownOpIds().isEmpty())
        }

    @Test
    fun `uuidForId returns the uuid that was assigned during applySharedUpsert`() =
        runTest(dispatcher) {
            val currencyId = db.currencyDao().findByCode("USD")!!.id
            val accountId = insertSeedAccount(currencyId)
            transactionRepo.applySharedUpsert(
                sampleTransaction(currencyId = currencyId, accountId = accountId),
                TX_UUID,
                deviceId,
            )
            val localId = db.transactionDao().findByUuid(TX_UUID)!!.id
            assertEquals(TX_UUID, transactionRepo.uuidForId(localId))
        }

    // ── helpers ────────────────────────────────────────────────────────────

    private suspend fun insertSeedAccount(currencyId: Long): Long {
        accountRepo.applySharedUpsert(sampleAccount(currencyId = currencyId), ACCOUNT_UUID, deviceId)
        return db.accountDao().findByUuid(ACCOUNT_UUID)!!.id
    }

    private fun sampleAccount(
        name: String = "Cash",
        currencyId: Long = 1L,
    ) = Account(
        id = 0L,
        name = name,
        currencyId = currencyId,
        initialBalance = BigDecimal.ZERO,
        type = AccountType.Cash,
        colorHex = "#4CAF50",
        iconKey = "ic_cash",
        isDefault = false,
        sortOrder = 0,
        createdAt = clock.instant(),
        updatedAt = clock.instant(),
        isArchived = false,
    )

    private fun sampleCategory(name: String = "Food") =
        Category(
            id = 0L,
            name = name,
            kind = CategoryKind.Expense,
            iconKey = "ic_food",
            colorHex = "#FF5722",
            textColor = "#FFFFFF",
            sortOrder = 0,
            isDefault = false,
            isArchived = false,
            createdAt = clock.instant(),
        )

    private fun sampleTransaction(
        currencyId: Long = 1L,
        accountId: Long = 1L,
        amount: BigDecimal = BigDecimal("10.00"),
    ) = Transaction(
        id = 0L,
        kind = TransactionKind.Expense,
        amount = amount,
        currencyId = currencyId,
        accountId = accountId,
        categoryId = null,
        note = null,
        occurredAt = clock.instant(),
        createdAt = clock.instant(),
        updatedAt = clock.instant(),
        isDeleted = false,
        toAccountId = null,
        toAmount = null,
        exchangeRate = null,
    )

    private companion object {
        const val ACCOUNT_UUID = "account-uuid-test"
        const val CATEGORY_UUID = "category-uuid-test"
        const val TX_UUID = "transaction-uuid-test"
    }
}
