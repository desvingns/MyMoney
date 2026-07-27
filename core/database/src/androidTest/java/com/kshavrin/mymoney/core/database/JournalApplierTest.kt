package com.kshavrin.mymoney.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kshavrin.mymoney.core.database.entity.AccountEntity
import com.kshavrin.mymoney.core.database.entity.CategoryEntity
import com.kshavrin.mymoney.core.database.entity.CurrencyEntity
import com.kshavrin.mymoney.core.database.entity.TransactionEntity
import com.kshavrin.mymoney.core.database.journal.JournalApplier
import com.kshavrin.mymoney.core.database.journal.OperationPayloadCodec
import com.kshavrin.mymoney.core.database.transaction.RoomTransactionRunner
import com.kshavrin.mymoney.core.domain.sync.EntityKind
import com.kshavrin.mymoney.core.domain.sync.OpType
import com.kshavrin.mymoney.core.domain.sync.Operation
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
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class JournalApplierTest {
    private lateinit var db: MoneyDatabase
    private lateinit var applier: JournalApplier
    private lateinit var codec: OperationPayloadCodec

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
            codec = OperationPayloadCodec(db.currencyDao())
            applier =
                JournalApplier(
                    transactionDao = db.transactionDao(),
                    categoryDao = db.categoryDao(),
                    accountDao = db.accountDao(),
                    currencyDao = db.currencyDao(),
                    operationDao = db.operationDao(),
                    payloadCodec = codec,
                    transactionRunner = RoomTransactionRunner(db),
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

    // ─── remote Upsert create ──────────────────────────────────────────────

    @Test
    fun `remote upsert account with no local row creates account by uuid`() =
        runTest {
            val accountUuid = "account-new-uuid"
            val op = accountUpsertOp(opId = "op-acc-1", uuid = accountUuid, name = "Remote Cash", updatedAtMs = T2)

            applier.apply(listOf(op))

            val stored = db.accountDao().findByUuid(accountUuid)
            assertNotNull("account must be created when no local row existed", stored)
            assertEquals("Remote Cash", stored!!.name)
            assertFalse("newly applied account must not be archived", stored.isArchived)
        }

    @Test
    fun `remote upsert category with no local row creates category by uuid`() =
        runTest {
            val categoryUuid = "category-new-uuid"
            val op = categoryUpsertOp(opId = "op-cat-1", uuid = categoryUuid, name = "Remote Food", updatedAtMs = T2)

            applier.apply(listOf(op))

            val stored = db.categoryDao().findByUuid(categoryUuid)
            assertNotNull("category must be created when no local row existed", stored)
            assertEquals("Remote Food", stored!!.name)
        }

    // ─── LWW remote wins ───────────────────────────────────────────────────

    @Test
    fun `remote edit wins by LWW when remote updatedAt is greater than local`() =
        runTest {
            val accountUuid = "account-lww-uuid"
            db.accountDao().upsert(
                accountEntity(uuid = accountUuid, name = "Local Name", updatedAtMs = T1),
            )

            val op = accountUpsertOp(opId = "op-lww-1", uuid = accountUuid, name = "Remote Winner", updatedAtMs = T3)
            applier.apply(listOf(op))

            val stored = db.accountDao().findByUuid(accountUuid)
            assertNotNull(stored)
            assertEquals("Remote Winner", stored!!.name)
        }

    @Test
    fun `remote category edit wins by LWW when remote updatedAt is greater than local`() =
        runTest {
            val categoryUuid = "cat-lww-uuid"
            db.categoryDao().upsert(
                categoryEntity(uuid = categoryUuid, name = "Local Category", updatedAtMs = T1),
            )

            val op = categoryUpsertOp(opId = "op-cat-lww", uuid = categoryUuid, name = "Remote Category", updatedAtMs = T3)
            applier.apply(listOf(op))

            val stored = db.categoryDao().findByUuid(categoryUuid)
            assertNotNull(stored)
            assertEquals("Remote Category", stored!!.name)
        }

    // ─── stale remote does NOT clobber newer local ─────────────────────────

    @Test
    fun `stale remote upsert does not overwrite newer local account`() =
        runTest {
            val accountUuid = "account-stale-uuid"
            db.accountDao().upsert(
                accountEntity(uuid = accountUuid, name = "Local Newer", updatedAtMs = T3),
            )

            val op = accountUpsertOp(opId = "op-stale-1", uuid = accountUuid, name = "Stale Remote", updatedAtMs = T1)
            applier.apply(listOf(op))

            val stored = db.accountDao().findByUuid(accountUuid)
            assertNotNull(stored)
            assertEquals("Local Newer must remain when remote is older", "Local Newer", stored!!.name)
        }

    @Test
    fun `stale remote upsert does not overwrite newer local category`() =
        runTest {
            val categoryUuid = "cat-stale-uuid"
            db.categoryDao().upsert(
                categoryEntity(uuid = categoryUuid, name = "Local Newer Cat", updatedAtMs = T3),
            )

            val op = categoryUpsertOp(opId = "op-cat-stale", uuid = categoryUuid, name = "Stale Remote Cat", updatedAtMs = T1)
            applier.apply(listOf(op))

            val stored = db.categoryDao().findByUuid(categoryUuid)
            assertNotNull(stored)
            assertEquals("Local Newer Cat must remain when remote is older", "Local Newer Cat", stored!!.name)
        }

    // ─── idempotent re-apply ───────────────────────────────────────────────

    @Test
    fun `applying same account batch twice yields no duplicate ops in journal`() =
        runTest {
            val accountUuid = "account-idem-uuid"
            val op = accountUpsertOp(opId = "op-idem-1", uuid = accountUuid, name = "Idempotent Account", updatedAtMs = T2)

            applier.apply(listOf(op))
            applier.apply(listOf(op))

            val ops = db.operationDao().opsForEntity(accountUuid)
            assertEquals("idempotent re-apply must not duplicate ops in journal", 1, ops.size)

            val stored = db.accountDao().findByUuid(accountUuid)
            assertNotNull(stored)
            assertEquals("Idempotent Account", stored!!.name)
        }

    @Test
    fun `applying same category batch twice yields no duplicate ops in journal`() =
        runTest {
            val categoryUuid = "cat-idem-uuid"
            val op = categoryUpsertOp(opId = "op-cat-idem", uuid = categoryUuid, name = "Idempotent Cat", updatedAtMs = T2)

            applier.apply(listOf(op))
            applier.apply(listOf(op))

            val ops = db.operationDao().opsForEntity(categoryUuid)
            assertEquals("idempotent re-apply must not duplicate ops in journal", 1, ops.size)
        }

    @Test
    fun `applying empty batch is a no-op`() =
        runTest {
            applier.apply(emptyList())

            val ids = db.operationDao().knownOpIds()
            assertTrue("empty batch must not write any ops", ids.isEmpty())
        }

    // ─── loop-guard ────────────────────────────────────────────────────────

    @Test
    fun `applied remote ops are marked appliedFromRemote and not returned by unsyncedLocal`() =
        runTest {
            val accountUuid = "account-loop-uuid"
            val op = accountUpsertOp(opId = "op-loop-1", uuid = accountUuid, name = "Loop Guard Account", updatedAtMs = T2)

            applier.apply(listOf(op))

            val unsynced = db.operationDao().unsyncedLocal()
            assertTrue(
                "ops applied from remote must not appear in unsyncedLocal (loop-guard D12)",
                unsynced.isEmpty(),
            )

            val known = db.operationDao().knownOpIds()
            assertTrue("applied op must be recorded in op_journal", known.contains("op-loop-1"))

            val journaledOp = db.operationDao().opsForEntity(accountUuid).single()
            assertTrue(
                "applied op must have appliedFromRemote=true",
                journaledOp.appliedFromRemote,
            )
        }

    @Test
    fun `multiple remote accounts applied — none appear in unsyncedLocal`() =
        runTest {
            val ops =
                listOf(
                    accountUpsertOp("op-lg-1", "acc-lg-1", "Account A", T2),
                    accountUpsertOp("op-lg-2", "acc-lg-2", "Account B", T2),
                )

            applier.apply(ops)

            val unsynced = db.operationDao().unsyncedLocal()
            assertTrue("no remote-applied ops must leak into the push set", unsynced.isEmpty())
        }

    // ─── remote Delete as tombstone ─────────────────────────────────────────

    @Test
    fun `remote delete account archives local row by uuid when delete timestamp wins LWW`() =
        runTest {
            val accountUuid = "account-delete-uuid"
            db.accountDao().upsert(
                accountEntity(uuid = accountUuid, name = "To Be Deleted", updatedAtMs = T1),
            )

            val deleteOp = accountDeleteOp(opId = "op-del-1", uuid = accountUuid, updatedAtMs = T3)
            applier.apply(listOf(deleteOp))

            val stored = db.accountDao().findByUuid(accountUuid)
            assertNotNull("row must still exist after soft-archive", stored)
            assertTrue(
                "account must be archived (soft-deleted) by uuid after remote delete wins LWW",
                stored!!.isArchived,
            )
        }

    @Test
    fun `remote delete category archives local row by uuid when delete timestamp wins LWW`() =
        runTest {
            val categoryUuid = "cat-delete-uuid"
            db.categoryDao().upsert(
                categoryEntity(uuid = categoryUuid, name = "To Be Archived Cat", updatedAtMs = T1),
            )

            val deleteOp = categoryDeleteOp(opId = "op-cat-del-1", uuid = categoryUuid, updatedAtMs = T3)
            applier.apply(listOf(deleteOp))

            val stored = db.categoryDao().findByUuid(categoryUuid)
            assertNotNull("category row must still exist after soft-archive", stored)
            assertTrue(
                "category must be archived by uuid after remote delete wins LWW",
                stored!!.isArchived,
            )
        }

    @Test
    fun `stale remote delete does not archive account when local is newer`() =
        runTest {
            val accountUuid = "account-stale-del-uuid"
            db.accountDao().upsert(
                accountEntity(uuid = accountUuid, name = "Should Survive", updatedAtMs = T3),
            )

            val deleteOp = accountDeleteOp(opId = "op-stale-del-1", uuid = accountUuid, updatedAtMs = T1)
            applier.apply(listOf(deleteOp))

            val stored = db.accountDao().findByUuid(accountUuid)
            assertNotNull(stored)
            assertFalse(
                "account must NOT be archived when local upsert timestamp is newer than remote delete",
                stored!!.isArchived,
            )
        }

    @Test
    fun `remote delete transaction soft-deletes local row by uuid`() =
        runTest {
            val accountUuid = "acc-tx-del-uuid"
            val txUuid = "tx-delete-uuid"
            db.accountDao().upsert(
                accountEntity(uuid = accountUuid, name = "Cash", updatedAtMs = T1),
            )
            val accountId = db.accountDao().findByUuid(accountUuid)!!.id
            db.transactionDao().upsert(
                transactionEntity(uuid = txUuid, accountId = accountId, updatedAtMs = T1),
            )

            val deleteOp = transactionDeleteOp(opId = "op-tx-del-1", uuid = txUuid, updatedAtMs = T3)
            applier.apply(listOf(deleteOp))

            val stored = db.transactionDao().findByUuid(txUuid)
            assertNotNull("transaction row must still exist after soft-delete", stored)
            assertTrue(
                "transaction must be soft-deleted by uuid after remote delete wins LWW",
                stored!!.isDeleted,
            )
        }

    // ─── FK-by-uuid ordering ────────────────────────────────────────────────

    @Test
    fun `account and category ops applied before transaction in same batch`() =
        runTest {
            val accountUuid = "acc-fk-uuid"
            val categoryUuid = "cat-fk-uuid"
            val txUuid = "tx-fk-uuid"

            val accountOp = accountUpsertOp("op-fk-acc", accountUuid, "FK Account", T2)
            val categoryOp = categoryUpsertOp("op-fk-cat", categoryUuid, "FK Category", T2)
            val txOp = transactionUpsertOp("op-fk-tx", txUuid, accountUuid, categoryUuid, T2)

            applier.apply(listOf(txOp, categoryOp, accountOp))

            val account = db.accountDao().findByUuid(accountUuid)
            val category = db.categoryDao().findByUuid(categoryUuid)
            val tx = db.transactionDao().findByUuid(txUuid)

            assertNotNull("account must be present after batch apply", account)
            assertNotNull("category must be present after batch apply", category)
            assertNotNull("transaction must be created with FK resolved from account/category uuids", tx)
            assertEquals(account!!.id, tx!!.accountId)
            assertEquals(category!!.id, tx.categoryId)
        }

    @Test
    fun `transaction is skipped when referenced account uuid is absent`() =
        runTest {
            val txUuid = "tx-no-acc-uuid"
            val missingAccountUuid = "account-does-not-exist"

            val txOp = transactionUpsertOp("op-no-acc-tx", txUuid, missingAccountUuid, null, T2)
            applier.apply(listOf(txOp))

            val tx = db.transactionDao().findByUuid(txUuid)
            assertNull(
                "transaction must be skipped when its account uuid cannot be resolved",
                tx,
            )
        }

    @Test
    fun `transaction with missing category uuid is still applied when category is optional`() =
        runTest {
            val accountUuid = "acc-opt-cat-uuid"
            val txUuid = "tx-no-cat-uuid"

            val accountOp = accountUpsertOp("op-opt-acc", accountUuid, "Account NoCat", T2)
            val txOp = transactionUpsertOp("op-no-cat-tx", txUuid, accountUuid, null, T2)

            applier.apply(listOf(accountOp, txOp))

            val tx = db.transactionDao().findByUuid(txUuid)
            assertNotNull("transaction without a category must still be applied", tx)
            assertNull("categoryId must be null when no category was provided", tx!!.categoryId)
        }

    @Test
    fun `transaction with present non-null category uuid that is missing is skipped`() =
        runTest {
            val accountUuid = "acc-present-uuid"
            val txUuid = "tx-missing-cat-uuid"
            val missingCategoryUuid = "category-ghost"

            val accountOp = accountUpsertOp("op-present-acc", accountUuid, "Present Account", T2)
            val txOp = transactionUpsertOp("op-missing-cat-tx", txUuid, accountUuid, missingCategoryUuid, T2)

            applier.apply(listOf(accountOp, txOp))

            val tx = db.transactionDao().findByUuid(txUuid)
            assertNull(
                "transaction must be skipped when a non-null category uuid cannot be resolved",
                tx,
            )
        }

    // ─── factory helpers ───────────────────────────────────────────────────

    private fun accountEntity(
        uuid: String,
        name: String,
        updatedAtMs: Long,
    ) = AccountEntity(
        uuid = uuid,
        deviceId = "device-seed",
        name = name,
        currencyId = currencyId,
        initialBalance = 0.0,
        type = "cash",
        colorHex = "#7AC794",
        iconKey = "ic_account_cash",
        isDefault = false,
        sortOrder = 0,
        createdAt = T1,
        updatedAt = updatedAtMs,
        isArchived = false,
    )

    private fun categoryEntity(
        uuid: String,
        name: String,
        updatedAtMs: Long,
    ) = CategoryEntity(
        uuid = uuid,
        deviceId = "device-seed",
        name = name,
        kind = "expense",
        iconKey = "ic_cat_food",
        colorHex = "#E07AAE",
        textColor = "#FFFFFF",
        sortOrder = 0,
        isDefault = false,
        isArchived = false,
        createdAt = T1,
        updatedAt = updatedAtMs,
    )

    private fun transactionEntity(
        uuid: String,
        accountId: Long,
        updatedAtMs: Long,
    ) = TransactionEntity(
        uuid = uuid,
        deviceId = "device-seed",
        kind = "expense",
        amount = 100.0,
        currencyId = currencyId,
        accountId = accountId,
        categoryId = null,
        note = null,
        occurredAt = T1,
        createdAt = T1,
        updatedAt = updatedAtMs,
        isDeleted = false,
        toAccountId = null,
        toAmount = null,
        exchangeRate = null,
    )

    private suspend fun accountUpsertOp(
        opId: String,
        uuid: String,
        name: String,
        updatedAtMs: Long,
    ): Operation {
        val entity =
            AccountEntity(
                uuid = uuid,
                deviceId = "device-remote",
                name = name,
                currencyId = currencyId,
                initialBalance = 0.0,
                type = "cash",
                colorHex = "#AABBCC",
                iconKey = "ic_account_cash",
                isDefault = false,
                sortOrder = 0,
                createdAt = T1,
                updatedAt = updatedAtMs,
                isArchived = false,
            )
        return Operation(
            opId = opId,
            deviceId = "device-remote",
            entityKind = EntityKind.Account,
            entityUuid = uuid,
            opType = OpType.Upsert,
            payload = codec.encodeAccount(entity),
            updatedAt = Instant.ofEpochMilli(updatedAtMs),
        )
    }

    private fun accountDeleteOp(
        opId: String,
        uuid: String,
        updatedAtMs: Long,
    ) = Operation(
        opId = opId,
        deviceId = "device-remote",
        entityKind = EntityKind.Account,
        entityUuid = uuid,
        opType = OpType.Delete,
        payload = null,
        updatedAt = Instant.ofEpochMilli(updatedAtMs),
    )

    private fun categoryUpsertOp(
        opId: String,
        uuid: String,
        name: String,
        updatedAtMs: Long,
    ): Operation {
        val entity =
            CategoryEntity(
                uuid = uuid,
                deviceId = "device-remote",
                name = name,
                kind = "expense",
                iconKey = "ic_cat_food",
                colorHex = "#E07AAE",
                textColor = "#FFFFFF",
                sortOrder = 0,
                isDefault = false,
                isArchived = false,
                createdAt = T1,
                updatedAt = updatedAtMs,
            )
        return Operation(
            opId = opId,
            deviceId = "device-remote",
            entityKind = EntityKind.Category,
            entityUuid = uuid,
            opType = OpType.Upsert,
            payload = codec.encodeCategory(entity),
            updatedAt = Instant.ofEpochMilli(updatedAtMs),
        )
    }

    private fun categoryDeleteOp(
        opId: String,
        uuid: String,
        updatedAtMs: Long,
    ) = Operation(
        opId = opId,
        deviceId = "device-remote",
        entityKind = EntityKind.Category,
        entityUuid = uuid,
        opType = OpType.Delete,
        payload = null,
        updatedAt = Instant.ofEpochMilli(updatedAtMs),
    )

    private suspend fun transactionUpsertOp(
        opId: String,
        txUuid: String,
        accountUuid: String,
        categoryUuid: String?,
        updatedAtMs: Long,
    ): Operation {
        val entity =
            TransactionEntity(
                uuid = txUuid,
                deviceId = "device-remote",
                kind = "expense",
                amount = 50.0,
                currencyId = currencyId,
                accountId = 0L,
                categoryId = null,
                note = null,
                occurredAt = T1,
                createdAt = T1,
                updatedAt = updatedAtMs,
                isDeleted = false,
                toAccountId = null,
                toAmount = null,
                exchangeRate = null,
            )
        return Operation(
            opId = opId,
            deviceId = "device-remote",
            entityKind = EntityKind.Transaction,
            entityUuid = txUuid,
            opType = OpType.Upsert,
            payload =
                codec.encodeTransaction(
                    entity = entity,
                    accountUuid = accountUuid,
                    categoryUuid = categoryUuid,
                    toAccountUuid = null,
                ),
            updatedAt = Instant.ofEpochMilli(updatedAtMs),
        )
    }

    private fun transactionDeleteOp(
        opId: String,
        uuid: String,
        updatedAtMs: Long,
    ) = Operation(
        opId = opId,
        deviceId = "device-remote",
        entityKind = EntityKind.Transaction,
        entityUuid = uuid,
        opType = OpType.Delete,
        payload = null,
        updatedAt = Instant.ofEpochMilli(updatedAtMs),
    )

    private companion object {
        const val T1 = 1_700_000_000_000L
        const val T2 = 1_700_000_100_000L
        const val T3 = 1_700_000_200_000L
    }
}
