package com.kshavrin.mymoney.core.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kshavrin.mymoney.core.database.MoneyDatabase
import com.kshavrin.mymoney.core.database.entity.AccountEntity
import com.kshavrin.mymoney.core.database.entity.CategoryEntity
import com.kshavrin.mymoney.core.database.entity.CurrencyEntity
import com.kshavrin.mymoney.core.database.entity.OperationEntity
import com.kshavrin.mymoney.core.database.entity.TransactionEntity
import com.kshavrin.mymoney.core.database.journal.JournalApplier
import com.kshavrin.mymoney.core.database.journal.JournalBootstrap
import com.kshavrin.mymoney.core.database.journal.OperationPayloadCodec
import com.kshavrin.mymoney.core.database.transaction.RoomTransactionRunner
import com.kshavrin.mymoney.core.datastore.CloudBinding
import com.kshavrin.mymoney.core.datastore.CloudProvider
import com.kshavrin.mymoney.core.datastore.JournalSyncConfigStore
import com.kshavrin.mymoney.core.domain.sync.DeviceIdProvider
import com.kshavrin.mymoney.core.domain.sync.EntityKind
import com.kshavrin.mymoney.core.domain.sync.OpType
import com.kshavrin.mymoney.core.testing.fake.FakeAppSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TwoDeviceJournalSyncIntegrationTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `offline conflicts converge to the operation merger winner on both devices`() =
        runTest {
            val folder = SharedFolder()
            val deviceA = createDevice(deviceId = DEVICE_A, folder = folder)
            val deviceB = createDevice(deviceId = DEVICE_B, folder = folder)

            try {
                deviceB.seedUsd()
                deviceA.seedBaseline()
                deviceA.sync.syncNow()
                deviceB.sync.syncNow()

                deviceA.updateTransaction(
                    uuid = TRANSACTION_TIE_UUID,
                    note = "edited by device A",
                    amount = 41.0,
                    updatedAt = T_TIE,
                    opId = "op-a-transaction-tie",
                )
                deviceB.updateTransaction(
                    uuid = TRANSACTION_TIE_UUID,
                    note = "edited by device B",
                    amount = 42.0,
                    updatedAt = T_TIE,
                    opId = "op-b-transaction-tie",
                )
                deviceA.deleteTransaction(
                    uuid = TRANSACTION_DELETE_UUID,
                    updatedAt = T_DELETE,
                    opId = "op-a-transaction-delete",
                )
                deviceB.updateTransaction(
                    uuid = TRANSACTION_DELETE_UUID,
                    note = "edited before deletion",
                    amount = 99.0,
                    updatedAt = T_EDIT_BEFORE_DELETE,
                    opId = "op-b-transaction-delete-race",
                )
                deviceA.renameCategory(
                    name = "Groceries A",
                    updatedAt = T_TIE,
                    opId = "op-a-category-rename",
                )
                deviceB.renameCategory(
                    name = "Groceries B",
                    updatedAt = T_TIE,
                    opId = "op-b-category-rename",
                )

                deviceA.sync.push()
                deviceB.sync.pull()
                deviceB.sync.push()
                deviceA.sync.pull()

                val stateA = deviceA.fullState()
                val stateB = deviceB.fullState()

                assertEquals(stateA, stateB)
                assertEquals("edited by device B", stateA.transactions.single { it.uuid == TRANSACTION_TIE_UUID }.note)
                assertEquals(42.0, stateA.transactions.single { it.uuid == TRANSACTION_TIE_UUID }.amount, 0.0)
                assertTrue(stateA.deletedTransactions.any { it.uuid == TRANSACTION_DELETE_UUID })
                assertEquals(T_DELETE, stateA.deletedTransactions.single { it.uuid == TRANSACTION_DELETE_UUID }.updatedAt)
                assertEquals("Groceries B", stateA.categories.single { it.uuid == CATEGORY_UUID }.name)
                assertEquals(
                    folder.revisionFor(DEVICE_B),
                    deviceA.configStore.peerHighWaterMs("$FILE_PREFIX$DEVICE_B"),
                )
                assertEquals(
                    folder.revisionFor(DEVICE_A),
                    deviceB.configStore.peerHighWaterMs("$FILE_PREFIX$DEVICE_A"),
                )
            } finally {
                deviceA.close()
                deviceB.close()
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun createDevice(
        deviceId: String,
        folder: SharedFolder,
    ): TestDevice {
        val database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    MoneyDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        val configStore = InMemoryJournalSyncConfigStore()
        val payloadCodec = OperationPayloadCodec(database.currencyDao())
        val transactionRunner = RoomTransactionRunner(database)
        val deviceIdProvider = FixedDeviceIdProvider(deviceId)
        val applier =
            JournalApplier(
                transactionDao = database.transactionDao(),
                categoryDao = database.categoryDao(),
                accountDao = database.accountDao(),
                currencyDao = database.currencyDao(),
                operationDao = database.operationDao(),
                payloadCodec = payloadCodec,
                transactionRunner = transactionRunner,
            )
        val bootstrap =
            JournalBootstrap(
                accountDao = database.accountDao(),
                categoryDao = database.categoryDao(),
                transactionDao = database.transactionDao(),
                operationDao = database.operationDao(),
                payloadCodec = payloadCodec,
                deviceIdProvider = deviceIdProvider,
                transactionRunner = transactionRunner,
                configStore = configStore,
            )
        val sync =
            JournalSyncImpl(
                operationDao = database.operationDao(),
                serializer = JournalSerializer(),
                backend = SharedFolderJournalBackend(folder),
                applier = applier,
                bootstrap = bootstrap,
                configStore = configStore,
                deviceIdProvider = deviceIdProvider,
                appSettings = FakeAppSettingsRepository(),
                clock = Clock.fixed(Instant.ofEpochMilli(T_SYNC_CLOCK), ZoneOffset.UTC),
                ioDispatcher = UnconfinedTestDispatcher(),
            )

        return TestDevice(deviceId, database, payloadCodec, configStore, sync)
    }

    private suspend fun TestDevice.seedBaseline() {
        val currencyId = seedUsd()
        val account =
            AccountEntity(
                uuid = ACCOUNT_UUID,
                deviceId = deviceId,
                name = "Cash",
                currencyId = currencyId,
                initialBalance = 10.0,
                type = "cash",
                colorHex = "#7AC794",
                iconKey = "ic_account_cash",
                isDefault = true,
                sortOrder = 0,
                createdAt = T_INITIAL,
                updatedAt = T_INITIAL,
                isArchived = false,
            )
        database.accountDao().upsert(account)
        val storedAccount = requireNotNull(database.accountDao().findByUuid(ACCOUNT_UUID))
        val category =
            CategoryEntity(
                uuid = CATEGORY_UUID,
                deviceId = deviceId,
                name = "Groceries",
                kind = "expense",
                iconKey = "ic_cat_food",
                colorHex = "#E07AAE",
                textColor = "#FFFFFF",
                sortOrder = 0,
                isDefault = false,
                isArchived = false,
                createdAt = T_INITIAL,
                updatedAt = T_INITIAL,
            )
        database.categoryDao().upsert(category)
        val storedCategory = requireNotNull(database.categoryDao().findByUuid(CATEGORY_UUID))
        database.transactionDao().upsert(
            transaction(
                uuid = TRANSACTION_TIE_UUID,
                accountId = storedAccount.id,
                categoryId = storedCategory.id,
                currencyId = currencyId,
                note = "baseline tie",
                amount = 40.0,
                deviceId = deviceId,
            ),
        )
        database.transactionDao().upsert(
            transaction(
                uuid = TRANSACTION_DELETE_UUID,
                accountId = storedAccount.id,
                categoryId = storedCategory.id,
                currencyId = currencyId,
                note = "baseline delete",
                amount = 50.0,
                deviceId = deviceId,
            ),
        )

        recordAccountUpsert("op-initial-account")
        recordCategoryUpsert("op-initial-category")
        recordTransactionUpsert(TRANSACTION_TIE_UUID, "op-initial-transaction-tie")
        recordTransactionUpsert(TRANSACTION_DELETE_UUID, "op-initial-transaction-delete")
    }

    private suspend fun TestDevice.seedUsd(): Long =
        database.currencyDao().upsert(
            CurrencyEntity(
                code = "USD",
                symbol = "$",
                name = "US Dollar",
                decimalDigits = 2,
                isActive = true,
                sortOrder = 0,
            ),
        )

    private suspend fun TestDevice.updateTransaction(
        uuid: String,
        note: String,
        amount: Double,
        updatedAt: Long,
        opId: String,
    ) {
        val current = requireNotNull(database.transactionDao().findByUuid(uuid))
        database.transactionDao().upsert(
            current.copy(
                deviceId = deviceId,
                note = note,
                amount = amount,
                updatedAt = updatedAt,
                isDeleted = false,
            ),
        )
        recordTransactionUpsert(uuid, opId)
    }

    private suspend fun TestDevice.deleteTransaction(
        uuid: String,
        updatedAt: Long,
        opId: String,
    ) {
        val current = requireNotNull(database.transactionDao().findByUuid(uuid))
        database.transactionDao().upsert(
            current.copy(
                deviceId = deviceId,
                updatedAt = updatedAt,
                isDeleted = true,
            ),
        )
        database.operationDao().insert(
            OperationEntity(
                opId = opId,
                deviceId = deviceId,
                entityKind = EntityKind.Transaction.name,
                entityUuid = uuid,
                opType = OpType.Delete.name,
                payload = null,
                updatedAt = updatedAt,
            ),
        )
    }

    private suspend fun TestDevice.renameCategory(
        name: String,
        updatedAt: Long,
        opId: String,
    ) {
        val current = requireNotNull(database.categoryDao().findByUuid(CATEGORY_UUID))
        database.categoryDao().upsert(
            current.copy(
                deviceId = deviceId,
                name = name,
                updatedAt = updatedAt,
            ),
        )
        recordCategoryUpsert(opId)
    }

    private suspend fun TestDevice.recordAccountUpsert(opId: String) {
        val account = requireNotNull(database.accountDao().findByUuid(ACCOUNT_UUID))
        database.operationDao().insert(
            OperationEntity(
                opId = opId,
                deviceId = deviceId,
                entityKind = EntityKind.Account.name,
                entityUuid = account.uuid,
                opType = OpType.Upsert.name,
                payload = payloadCodec.encodeAccount(account),
                updatedAt = account.updatedAt,
            ),
        )
    }

    private suspend fun TestDevice.recordCategoryUpsert(opId: String) {
        val category = requireNotNull(database.categoryDao().findByUuid(CATEGORY_UUID))
        database.operationDao().insert(
            OperationEntity(
                opId = opId,
                deviceId = deviceId,
                entityKind = EntityKind.Category.name,
                entityUuid = category.uuid,
                opType = OpType.Upsert.name,
                payload = payloadCodec.encodeCategory(category),
                updatedAt = category.updatedAt,
            ),
        )
    }

    private suspend fun TestDevice.recordTransactionUpsert(
        uuid: String,
        opId: String,
    ) {
        val transaction = requireNotNull(database.transactionDao().findByUuid(uuid))
        val account = requireNotNull(database.accountDao().findById(transaction.accountId))
        val category =
            transaction.categoryId?.let { categoryId ->
                database.categoryDao().findById(categoryId)
            }
        val toAccount =
            transaction.toAccountId?.let { accountId ->
                database.accountDao().findById(accountId)
            }
        database.operationDao().insert(
            OperationEntity(
                opId = opId,
                deviceId = deviceId,
                entityKind = EntityKind.Transaction.name,
                entityUuid = transaction.uuid,
                opType = OpType.Upsert.name,
                payload =
                    payloadCodec.encodeTransaction(
                        entity = transaction,
                        accountUuid = account.uuid,
                        categoryUuid = category?.uuid,
                        toAccountUuid = toAccount?.uuid,
                    ),
                updatedAt = transaction.updatedAt,
            ),
        )
    }

    private suspend fun TestDevice.fullState(): FullEntityState {
        val currencies =
            database
                .currencyDao()
                .observeAll()
                .first()
                .sortedBy(CurrencyEntity::code)
        val accounts = database.accountDao().listAll().sortedBy(AccountEntity::uuid)
        val categories = database.categoryDao().listAll().sortedBy(CategoryEntity::uuid)
        val transactions = database.transactionDao().listAll().sortedBy(TransactionEntity::uuid)
        val currenciesById = currencies.associateBy(CurrencyEntity::id)
        val accountsById = accounts.associateBy(AccountEntity::id)
        val categoriesById = categories.associateBy(CategoryEntity::id)

        val liveTransactions = transactions.filterNot(TransactionEntity::isDeleted)
        val deletedTransactions = transactions.filter(TransactionEntity::isDeleted)

        return FullEntityState(
            currencies =
                currencies.map {
                    CurrencyState(it.code, it.symbol, it.name, it.decimalDigits, it.isActive, it.sortOrder)
                },
            accounts =
                accounts.map {
                    AccountState(
                        uuid = it.uuid,
                        deviceId = it.deviceId,
                        name = it.name,
                        currencyCode = currenciesById.getValue(it.currencyId).code,
                        initialBalance = decimal(it.initialBalance),
                        type = it.type,
                        colorHex = it.colorHex,
                        iconKey = it.iconKey,
                        isDefault = it.isDefault,
                        sortOrder = it.sortOrder,
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt,
                        isArchived = it.isArchived,
                    )
                },
            categories =
                categories.map {
                    CategoryState(
                        uuid = it.uuid,
                        deviceId = it.deviceId,
                        name = it.name,
                        kind = it.kind,
                        iconKey = it.iconKey,
                        colorHex = it.colorHex,
                        textColor = it.textColor,
                        sortOrder = it.sortOrder,
                        isDefault = it.isDefault,
                        isArchived = it.isArchived,
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt,
                    )
                },
            transactions =
                liveTransactions.map {
                    TransactionState(
                        uuid = it.uuid,
                        deviceId = it.deviceId,
                        kind = it.kind,
                        amount = it.amount,
                        currencyCode = currenciesById.getValue(it.currencyId).code,
                        accountUuid = accountsById.getValue(it.accountId).uuid,
                        categoryUuid = it.categoryId?.let(categoriesById::get)?.uuid,
                        note = it.note,
                        occurredAt = it.occurredAt,
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt,
                        toAccountUuid = it.toAccountId?.let(accountsById::get)?.uuid,
                        toAmount = it.toAmount?.let(::decimal),
                        exchangeRate = it.exchangeRate,
                    )
                },
            deletedTransactions =
                deletedTransactions.map {
                    DeletedTransactionState(it.uuid, it.updatedAt)
                },
        )
    }

    private fun transaction(
        uuid: String,
        accountId: Long,
        categoryId: Long,
        currencyId: Long,
        note: String,
        amount: Double,
        deviceId: String,
    ) =
        TransactionEntity(
            uuid = uuid,
            deviceId = deviceId,
            kind = "expense",
            amount = amount,
            currencyId = currencyId,
            accountId = accountId,
            categoryId = categoryId,
            note = note,
            occurredAt = T_INITIAL,
            createdAt = T_INITIAL,
            updatedAt = T_INITIAL,
            isDeleted = false,
            toAccountId = null,
            toAmount = null,
            exchangeRate = null,
        )

    private fun decimal(value: Double): String = BigDecimal.valueOf(value).toPlainString()

    private class TestDevice(
        val deviceId: String,
        val database: MoneyDatabase,
        val payloadCodec: OperationPayloadCodec,
        val configStore: InMemoryJournalSyncConfigStore,
        val sync: JournalSync,
    ) : AutoCloseable {
        override fun close() {
            database.close()
        }
    }

    private class FixedDeviceIdProvider(
        private val value: String,
    ) : DeviceIdProvider {
        override suspend fun deviceId(): String = value
    }

    private class InMemoryJournalSyncConfigStore : JournalSyncConfigStore {
        private var currentBinding: CloudBinding? =
            CloudBinding(CloudProvider.Dropbox, "account", "Shared folder")
        private var bootstrapDone = true
        private val peerHighWaterMs = mutableMapOf<String, Long>()

        override suspend fun binding(): CloudBinding? = currentBinding

        override suspend fun setBinding(binding: CloudBinding) {
            currentBinding = binding
        }

        override suspend fun clearBinding() {
            currentBinding = null
        }

        override suspend fun peerHighWaterMs(fileId: String): Long = peerHighWaterMs[fileId] ?: 0L

        override suspend fun setPeerHighWaterMs(
            fileId: String,
            modifiedAtMs: Long,
        ) {
            peerHighWaterMs[fileId] = modifiedAtMs
        }

        override suspend fun isBootstrapDone(): Boolean = bootstrapDone

        override suspend fun markBootstrapDone() {
            bootstrapDone = true
        }

        override suspend fun clear() {
            currentBinding = null
            bootstrapDone = false
            peerHighWaterMs.clear()
        }
    }

    private class SharedFolder {
        private val files = linkedMapOf<String, StoredJournal>()
        private var revision = 0L

        fun upload(
            deviceId: String,
            bytes: ByteArray,
        ) {
            revision += 1
            files[deviceId] = StoredJournal(bytes.copyOf(), revision)
        }

        fun list(): List<RemoteJournalFile> =
            files.map { (deviceId, journal) ->
                RemoteJournalFile(
                    fileId = fileId(deviceId),
                    deviceId = deviceId,
                    modifiedAtEpochMs = journal.revision,
                )
            }

        fun download(fileId: String): ByteArray? =
            files[fileId.removePrefix(FILE_PREFIX)]?.bytes?.copyOf()

        fun revisionFor(deviceId: String): Long = requireNotNull(files[deviceId]).revision

        private fun fileId(deviceId: String): String = "$FILE_PREFIX$deviceId"

        private data class StoredJournal(
            val bytes: ByteArray,
            val revision: Long,
        )
    }

    private class SharedFolderJournalBackend(
        private val folder: SharedFolder,
    ) : JournalBackend {
        override val target: SyncTarget = SyncTarget.Dropbox

        override suspend fun uploadJournal(
            deviceId: String,
            bytes: ByteArray,
        ): Result<Unit> {
            folder.upload(deviceId, bytes)
            return Result.success(Unit)
        }

        override suspend fun listPeerJournals(): Result<List<RemoteJournalFile>> = Result.success(folder.list())

        override suspend fun downloadJournal(fileId: String): Result<ByteArray> =
            folder.download(fileId)?.let(Result.Companion::success)
                ?: Result.failure(IllegalArgumentException("Unknown journal file: $fileId"))
    }

    private data class FullEntityState(
        val currencies: List<CurrencyState>,
        val accounts: List<AccountState>,
        val categories: List<CategoryState>,
        val transactions: List<TransactionState>,
        val deletedTransactions: List<DeletedTransactionState>,
    )

    private data class CurrencyState(
        val code: String,
        val symbol: String,
        val name: String,
        val decimalDigits: Int,
        val isActive: Boolean,
        val sortOrder: Int,
    )

    private data class AccountState(
        val uuid: String,
        val deviceId: String,
        val name: String,
        val currencyCode: String,
        val initialBalance: String,
        val type: String,
        val colorHex: String,
        val iconKey: String,
        val isDefault: Boolean,
        val sortOrder: Int,
        val createdAt: Long,
        val updatedAt: Long,
        val isArchived: Boolean,
    )

    private data class CategoryState(
        val uuid: String,
        val deviceId: String,
        val name: String,
        val kind: String,
        val iconKey: String,
        val colorHex: String,
        val textColor: String,
        val sortOrder: Int,
        val isDefault: Boolean,
        val isArchived: Boolean,
        val createdAt: Long,
        val updatedAt: Long,
    )

    private data class TransactionState(
        val uuid: String,
        val deviceId: String,
        val kind: String,
        val amount: Double,
        val currencyCode: String,
        val accountUuid: String,
        val categoryUuid: String?,
        val note: String?,
        val occurredAt: Long,
        val createdAt: Long,
        val updatedAt: Long,
        val toAccountUuid: String?,
        val toAmount: String?,
        val exchangeRate: Double?,
    )

    private data class DeletedTransactionState(
        val uuid: String,
        val updatedAt: Long,
    )

    private companion object {
        const val DEVICE_A = "device-a"
        const val DEVICE_B = "device-b"
        const val ACCOUNT_UUID = "account-cash"
        const val CATEGORY_UUID = "category-groceries"
        const val TRANSACTION_TIE_UUID = "transaction-tie"
        const val TRANSACTION_DELETE_UUID = "transaction-delete"
        const val FILE_PREFIX = "journal-"
        const val T_INITIAL = 1_700_000_000_000L
        const val T_TIE = 1_700_000_100_000L
        const val T_EDIT_BEFORE_DELETE = 1_700_000_200_000L
        const val T_DELETE = 1_700_000_300_000L
        const val T_SYNC_CLOCK = 1_700_000_400_000L
    }
}
