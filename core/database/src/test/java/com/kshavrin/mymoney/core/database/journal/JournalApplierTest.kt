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
    private lateinit var codec: OperationPayloadCodec

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    MoneyDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        codec = OperationPayloadCodec(db.currencyDao())

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
                currencyDao = db.currencyDao(),
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

    // ─── cross-device currency id portability (regression) ──────────────────
    // Reproduces the on-device crash: a peer's local currency row id is NOT stable across
    // installs. Two devices can both have "EUR" but at different autoincrement ids. Before this
    // fix, the wire payload carried the raw id, so applying a peer's account/transaction op threw
    // SQLiteConstraintException (FK on currency_id) instead of resolving by the portable ISO code.

    @Test
    fun `account op from a peer whose EUR has a different local row id still resolves by currency code`() =
        runTest {
            // Local db already seeded with USD=1 in setUp(); add EUR at a DIFFERENT id than the peer will use.
            db.currencyDao().upsert(
                CurrencyEntity(code = "EUR", symbol = "€", name = "Euro", decimalDigits = 2, isActive = true, sortOrder = 2),
            )
            val localEurId = requireNotNull(db.currencyDao().findByCode("EUR")).id

            // Simulate the peer device: its OWN db has EUR at a completely different row id.
            val peerDb =
                androidx.room.Room
                    .inMemoryDatabaseBuilder(
                        androidx.test.core.app.ApplicationProvider.getApplicationContext(),
                        MoneyDatabase::class.java,
                    ).allowMainThreadQueries()
                    .build()
            repeat(10) { peerDb.currencyDao().upsert(CurrencyEntity(code = "PLACEHOLDER-$it", symbol = "?", name = "?", decimalDigits = 2, isActive = false, sortOrder = it)) }
            peerDb.currencyDao().upsert(CurrencyEntity(code = "EUR", symbol = "€", name = "Euro", decimalDigits = 2, isActive = true, sortOrder = 99))
            val peerEurId = requireNotNull(peerDb.currencyDao().findByCode("EUR")).id
            assertTrue("test setup must actually diverge the two ids", localEurId != peerEurId)
            val peerCodec = OperationPayloadCodec(peerDb.currencyDao())

            val accountUuid = "acc-cross-currency"
            val payload =
                peerCodec.encodeAccount(
                    AccountEntity(
                        uuid = accountUuid,
                        deviceId = "device-peer",
                        name = "Peer EUR Account",
                        currencyId = peerEurId,
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
            peerDb.close()

            val result = applier.apply(listOf(accountOp(opId = "op-cross-currency", entityUuid = accountUuid, payload = payload)))

            assertEquals("must apply, not crash or silently skip", 1, result.appliedCount)
            assertFalse(result.hadSkips)
            val stored = db.accountDao().findByUuid(accountUuid)
            assertNotNull(stored)
            assertEquals("account must be linked to THIS device's EUR row, not the peer's numeric id", localEurId, stored?.currencyId)
        }

    @Test
    fun `account op referencing a currency code unknown locally is skipped, not crashed`() =
        runTest {
            val accountUuid = "acc-unknown-currency"
            val currencyDao = db.currencyDao()
            val codecWithForeignCurrency =
                object : Any() {
                    suspend fun encode(): String {
                        // Build the payload directly: no local currency row for "XYZ" exists, so
                        // encoding via the normal path (which requires a valid local id) isn't
                        // representative of the wire format a peer would actually send.
                        val entity =
                            AccountEntity(
                                uuid = accountUuid,
                                deviceId = "device-peer",
                                name = "Foreign Currency Account",
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
                            )
                        val encoded = codec.encodeAccount(entity)
                        // Rewrite the encoded USD code to a code that exists on NO device locally.
                        return encoded.replace("\"currencyCode\":\"USD\"", "\"currencyCode\":\"XYZ\"")
                    }
                }
            assertNull("XYZ must not exist locally for this test to be meaningful", currencyDao.findByCode("XYZ"))
            val payload = codecWithForeignCurrency.encode()

            val result = applier.apply(listOf(accountOp(opId = "op-unknown-currency", entityUuid = accountUuid, payload = payload)))

            assertEquals(0, result.appliedCount)
            assertTrue("hadSkips must be true so this op is retried once the currency is known", result.hadSkips)
            assertNull("account must NOT be upserted with an unresolved currency", db.accountDao().findByUuid(accountUuid))
        }

    @Test
    fun `pre-migration payload without currencyCode is skipped, not a deserialize crash`() =
        runTest {
            // A peer file written by the build BEFORE this fix carries the old raw "currencyId"
            // field; the new AccountSnapshot no longer has that field, so it must decode fine
            // (currencyCode defaults to blank) and then skip cleanly instead of throwing.
            val accountUuid = "acc-pre-migration"
            val legacyPayload =
                """{"uuid":"$accountUuid","deviceId":"device-peer","name":"Legacy Account","currencyId":1,""" +
                    """"initialBalance":"0","type":"cash","colorHex":"#FFFFFF","iconKey":"ic_account_cash",""" +
                    """"isDefault":false,"sortOrder":1,"createdAt":1000,"updatedAt":1000,"isArchived":false}"""

            val result = applier.apply(listOf(accountOp(opId = "op-legacy", entityUuid = accountUuid, payload = legacyPayload)))

            assertEquals(0, result.appliedCount)
            assertTrue("legacy payload must be skipped (retried later), not crash the whole pull", result.hadSkips)
            assertNull(db.accountDao().findByUuid(accountUuid))
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

    private suspend fun encodeAccount(uuid: String): String =
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

    private suspend fun encodeTransaction(
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
