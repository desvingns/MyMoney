package com.kshavrin.mymoney.core.database.journal

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kshavrin.mymoney.core.database.MoneyDatabase
import com.kshavrin.mymoney.core.database.entity.AccountEntity
import com.kshavrin.mymoney.core.database.entity.CurrencyEntity
import com.kshavrin.mymoney.core.database.entity.TransactionEntity
import com.kshavrin.mymoney.core.domain.sync.EntityKind
import com.kshavrin.mymoney.core.domain.sync.OpType
import com.kshavrin.mymoney.core.domain.sync.Operation
import com.kshavrin.mymoney.core.domain.transaction.TransactionRunner
import kotlinx.coroutines.runBlocking
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
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class JournalApplierTest {

    private lateinit var db: MoneyDatabase
    private lateinit var applier: JournalApplier
    private val codec = OperationPayloadCodec()

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    MoneyDatabase::class.java,
                ).allowMainThreadQueries()
                .build()

        // Insert a minimal currency row so AccountEntity/TransactionEntity have a resolvable
        // currencyId (id=1) even if FK enforcement is active in the test SQLite driver.
        runBlocking {
            db.currencyDao().upsert(
                CurrencyEntity(
                    code = "USD",
                    symbol = "$",
                    name = "US Dollar",
                    decimalDigits = 2,
                    isActive = true,
                    sortOrder = 1,
                ),
            )
        }

        val noOpRunner =
            object : TransactionRunner {
                override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
            }

        applier =
            JournalApplier(
                transactionDao = db.transactionDao(),
                categoryDao = db.categoryDao(),
                accountDao = db.accountDao(),
                operationDao = db.operationDao(),
                payloadCodec = codec,
                transactionRunner = noOpRunner,
            )
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ─── resolved op ──────────────────────────────────────────────────────────

    @Test
    fun `resolved account op is applied and recorded in op_journal, hadSkips false`() =
        runTest {
            val accountUuid = "acc-apply-1"
            val op = accountOp(opId = "op-acc-1", entityUuid = accountUuid, payload = encodeAccount(accountUuid))

            val result = applier.apply(listOf(op))

            assertEquals(1, result.appliedCount)
            assertFalse(result.hadSkips)
            assertNotNull("account must be upserted into the DB", db.accountDao().findByUuid(accountUuid))
            assertTrue(
                "op must be recorded in op_journal via insertApplied",
                db.operationDao().knownOpIds().contains("op-acc-1"),
            )
        }

    // ─── FK skip ──────────────────────────────────────────────────────────────

    @Test
    fun `transaction op with missing account FK is skipped and not recorded in op_journal`() =
        runTest {
            val txUuid = "tx-skip-1"
            val op =
                transactionOp(
                    opId = "op-tx-skip-1",
                    entityUuid = txUuid,
                    payload = encodeTransaction(txUuid, accountUuid = "missing-account-uuid"),
                )

            val result = applier.apply(listOf(op))

            assertEquals(0, result.appliedCount)
            assertTrue("hadSkips must be true when account FK is missing", result.hadSkips)
            assertNull(
                "transaction must NOT be upserted when FK is unresolvable",
                db.transactionDao().findByUuid(txUuid),
            )
            assertFalse(
                "skipped op must NOT appear in op_journal",
                db.operationDao().knownOpIds().contains("op-tx-skip-1"),
            )
        }

    // ─── mixed batch ──────────────────────────────────────────────────────────

    @Test
    fun `mixed batch applies resolved account and skips transaction with unresolved FK`() =
        runTest {
            val accountUuid = "acc-good"
            val txUuid = "tx-needs-missing-acc"

            val batchAccountOp =
                accountOp(opId = "op-acc-good", entityUuid = accountUuid, payload = encodeAccount(accountUuid))
            val batchTxOp =
                transactionOp(
                    opId = "op-tx-missing-fk",
                    entityUuid = txUuid,
                    payload = encodeTransaction(txUuid, accountUuid = "acc-missing-in-mixed"),
                )

            val result = applier.apply(listOf(batchAccountOp, batchTxOp))

            assertEquals("only the account op must be counted as applied", 1, result.appliedCount)
            assertTrue("hadSkips must be true because the transaction was skipped", result.hadSkips)
            assertNotNull(db.accountDao().findByUuid(accountUuid))
            assertNull(db.transactionDao().findByUuid(txUuid))
            assertTrue(db.operationDao().knownOpIds().contains("op-acc-good"))
            assertFalse(
                "skipped tx op must NOT be in op_journal",
                db.operationDao().knownOpIds().contains("op-tx-missing-fk"),
            )
        }

    // ─── retry semantics (FIX 2 core pin) ────────────────────────────────────

    @Test
    fun `retry previously-skipped tx resolves on second apply once its account dependency is present`() =
        runTest {
            val accountInBatchUuid = "acc-in-batch"
            val missingAccountUuid = "acc-needed-by-tx"
            val txUuid = "tx-retry-1"

            val batchAccountOp =
                accountOp(
                    opId = "op-acc-batch",
                    entityUuid = accountInBatchUuid,
                    payload = encodeAccount(accountInBatchUuid),
                )
            val batchTxOp =
                transactionOp(
                    opId = "op-tx-retry",
                    entityUuid = txUuid,
                    payload = encodeTransaction(txUuid, accountUuid = missingAccountUuid),
                )

            // First apply: accountOp resolves (acc-in-batch inserted); txOp skipped (acc-needed-by-tx absent)
            val firstResult = applier.apply(listOf(batchAccountOp, batchTxOp))
            assertEquals(1, firstResult.appliedCount)
            assertTrue("first apply must report hadSkips=true for the unresolved tx", firstResult.hadSkips)
            assertFalse(
                "tx op must NOT be in op_journal after first apply",
                db.operationDao().knownOpIds().contains("op-tx-retry"),
            )

            // Dependency arrives: insert the missing account directly into the DB.
            db.accountDao().upsert(
                AccountEntity(
                    uuid = missingAccountUuid,
                    deviceId = "device-peer",
                    name = "Dependency Account",
                    currencyId = 1L,
                    initialBalance = 0.0,
                    type = "cash",
                    colorHex = "#FFFFFF",
                    iconKey = "ic_account_cash",
                    isDefault = false,
                    sortOrder = 99,
                    createdAt = 1_000L,
                    updatedAt = 1_000L,
                    isArchived = false,
                ),
            )

            // Second apply of THE SAME BATCH:
            // - accountOp is in knownOpIds → filtered as already-known (not double-applied)
            // - txOp is fresh; its FK now resolves
            val secondResult = applier.apply(listOf(batchAccountOp, batchTxOp))

            assertEquals("tx op must be applied on retry", 1, secondResult.appliedCount)
            assertFalse("no more skips after dependency is present", secondResult.hadSkips)
            assertNotNull(
                "transaction must be present in DB after successful retry",
                db.transactionDao().findByUuid(txUuid),
            )
            assertTrue(
                "tx op must now be in op_journal",
                db.operationDao().knownOpIds().contains("op-tx-retry"),
            )
            // accountOp must still appear exactly once (not double-inserted)
            assertEquals(
                "acc-in-batch must appear exactly once after two applies",
                1,
                db.accountDao().listAll().count { it.uuid == accountInBatchUuid },
            )
        }

    // ─── tombstone and None ────────────────────────────────────────────────────

    @Test
    fun `tombstone Delete op archives entity and is counted as applied, hadSkips false`() =
        runTest {
            val deleteOp =
                Operation(
                    opId = "op-delete-acc",
                    deviceId = "device-peer",
                    entityKind = EntityKind.Account,
                    entityUuid = "acc-to-archive",
                    opType = OpType.Delete,
                    payload = null,
                    updatedAt = Instant.ofEpochMilli(2_000L),
                )

            val result = applier.apply(listOf(deleteOp))

            assertEquals("tombstone op must be counted as applied", 1, result.appliedCount)
            assertFalse("hadSkips must be false for a tombstone-only batch", result.hadSkips)
            assertTrue(
                "tombstone op must be recorded in op_journal",
                db.operationDao().knownOpIds().contains("op-delete-acc"),
            )
        }

    // ─── empty and all-known edge cases ──────────────────────────────────────

    @Test
    fun `empty remoteOps returns ApplyResult with zero count and no skips`() =
        runTest {
            val result = applier.apply(emptyList())

            assertEquals(0, result.appliedCount)
            assertFalse(result.hadSkips)
        }

    @Test
    fun `all-known ops returns ApplyResult zero false without double-applying`() =
        runTest {
            val accountUuid = "acc-idempotent"
            val op = accountOp(opId = "op-idem-1", entityUuid = accountUuid, payload = encodeAccount(accountUuid))

            val first = applier.apply(listOf(op))
            assertEquals("first apply must succeed", 1, first.appliedCount)

            // Second call: op is now in knownOpIds → fresh list is empty → immediate ApplyResult(0, false)
            val second = applier.apply(listOf(op))

            assertEquals("second apply must report zero appliedCount", 0, second.appliedCount)
            assertFalse("second apply must not report hadSkips", second.hadSkips)
            assertEquals(
                "account must appear exactly once in the DB (not double-inserted)",
                1,
                db.accountDao().listAll().count { it.uuid == accountUuid },
            )
        }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private fun accountOp(
        opId: String,
        entityUuid: String,
        payload: String?,
    ) = Operation(
        opId = opId,
        deviceId = "device-peer",
        entityKind = EntityKind.Account,
        entityUuid = entityUuid,
        opType = OpType.Upsert,
        payload = payload,
        updatedAt = Instant.ofEpochMilli(1_000L),
    )

    private fun transactionOp(
        opId: String,
        entityUuid: String,
        payload: String,
    ) = Operation(
        opId = opId,
        deviceId = "device-peer",
        entityKind = EntityKind.Transaction,
        entityUuid = entityUuid,
        opType = OpType.Upsert,
        payload = payload,
        updatedAt = Instant.ofEpochMilli(1_000L),
    )

    private fun encodeAccount(uuid: String): String =
        codec.encodeAccount(
            AccountEntity(
                uuid = uuid,
                deviceId = "device-peer",
                name = "Test Account",
                currencyId = 1L,
                initialBalance = 0.0,
                type = "cash",
                colorHex = "#FFFFFF",
                iconKey = "ic_account_cash",
                isDefault = false,
                sortOrder = 1,
                createdAt = 1_000L,
                updatedAt = 1_000L,
                isArchived = false,
            ),
        )

    private fun encodeTransaction(
        uuid: String,
        accountUuid: String,
    ): String =
        codec.encodeTransaction(
            entity =
                TransactionEntity(
                    uuid = uuid,
                    deviceId = "device-peer",
                    kind = "expense",
                    amount = 10.0,
                    currencyId = 1L,
                    accountId = 0L,
                    categoryId = null,
                    note = null,
                    occurredAt = 1_000L,
                    createdAt = 1_000L,
                    updatedAt = 1_000L,
                    isDeleted = false,
                    toAccountId = null,
                    toAmount = null,
                    exchangeRate = null,
                ),
            accountUuid = accountUuid,
            categoryUuid = null,
            toAccountUuid = null,
        )
}
