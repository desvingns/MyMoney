package com.kshavrin.mymoney.core.database.journal

import com.kshavrin.mymoney.core.database.dao.AccountDao
import com.kshavrin.mymoney.core.database.dao.CategoryDao
import com.kshavrin.mymoney.core.database.dao.OperationDao
import com.kshavrin.mymoney.core.database.dao.TransactionDao
import com.kshavrin.mymoney.core.database.entity.AccountEntity
import com.kshavrin.mymoney.core.database.entity.CategoryEntity
import com.kshavrin.mymoney.core.database.entity.OperationEntity
import com.kshavrin.mymoney.core.database.entity.TransactionEntity
import com.kshavrin.mymoney.core.datastore.JournalSyncConfigStore
import com.kshavrin.mymoney.core.domain.sync.DeviceIdProvider
import com.kshavrin.mymoney.core.domain.sync.EntityKind
import com.kshavrin.mymoney.core.domain.sync.OpType
import com.kshavrin.mymoney.core.domain.transaction.TransactionRunner
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot, idempotent bootstrap that seeds the operation journal from the pre-existing local
 * registry the first time the app runs after the journal migration (D11).
 *
 * It stamps [DeviceIdProvider.deviceId] onto rows whose `device_id` is still blank, then emits an
 * `Upsert` (or `Delete` for archived/deleted rows) for every entity that does not yet have any
 * operation in the journal, so peers receive the existing data. The "done" flag lives in
 * [JournalSyncConfigStore] so the bootstrap runs exactly once.
 */
@Singleton
class JournalBootstrap
    @Inject
    constructor(
        private val accountDao: AccountDao,
        private val categoryDao: CategoryDao,
        private val transactionDao: TransactionDao,
        private val operationDao: OperationDao,
        private val payloadCodec: OperationPayloadCodec,
        private val deviceIdProvider: DeviceIdProvider,
        private val transactionRunner: TransactionRunner,
        private val configStore: JournalSyncConfigStore,
    ) {
        suspend fun runIfNeeded() {
            if (configStore.isBootstrapDone()) return
            val deviceId = deviceIdProvider.deviceId()
            transactionRunner.runInTransaction {
                accountDao.stampMissingDeviceId(deviceId)
                categoryDao.stampMissingDeviceId(deviceId)
                transactionDao.stampMissingDeviceId(deviceId)

                val knownEntities = operationDao.knownEntityUuids().toHashSet()
                val ops = mutableListOf<OperationEntity>()

                accountDao.listAll().forEach { account ->
                    if (account.uuid.isNotBlank() && account.uuid !in knownEntities) {
                        ops += account.toBootstrapOp(deviceId)
                    }
                }
                categoryDao.listAll().forEach { category ->
                    if (category.uuid.isNotBlank() && category.uuid !in knownEntities) {
                        ops += category.toBootstrapOp(deviceId)
                    }
                }
                transactionDao.listAll().forEach { transaction ->
                    if (transaction.uuid.isNotBlank() && transaction.uuid !in knownEntities) {
                        ops += transaction.toBootstrapOp(deviceId)
                    }
                }

                if (ops.isNotEmpty()) operationDao.insertAll(ops)
            }
            configStore.markBootstrapDone()
        }

        private suspend fun AccountEntity.toBootstrapOp(deviceId: String): OperationEntity =
            OperationEntity(
                opId = UUID.randomUUID().toString(),
                deviceId = deviceId,
                entityKind = EntityKind.Account.name,
                entityUuid = uuid,
                opType = if (isArchived) OpType.Delete.name else OpType.Upsert.name,
                payload = if (isArchived) null else payloadCodec.encodeAccount(copy(deviceId = deviceId)),
                updatedAt = updatedAt,
            )

        private fun CategoryEntity.toBootstrapOp(deviceId: String): OperationEntity =
            OperationEntity(
                opId = UUID.randomUUID().toString(),
                deviceId = deviceId,
                entityKind = EntityKind.Category.name,
                entityUuid = uuid,
                opType = if (isArchived) OpType.Delete.name else OpType.Upsert.name,
                payload = if (isArchived) null else payloadCodec.encodeCategory(copy(deviceId = deviceId)),
                updatedAt = updatedAt,
            )

        private suspend fun TransactionEntity.toBootstrapOp(deviceId: String): OperationEntity {
            val deleted = isDeleted
            val payload =
                if (deleted) {
                    null
                } else {
                    val accountUuid = accountDao.findById(accountId)?.uuid
                    val categoryUuid = categoryId?.let { categoryDao.findById(it)?.uuid }
                    val toAccountUuid = toAccountId?.let { accountDao.findById(it)?.uuid }
                    if (accountUuid.isNullOrBlank()) {
                        null
                    } else {
                        payloadCodec.encodeTransaction(
                            entity = copy(deviceId = deviceId),
                            accountUuid = accountUuid,
                            categoryUuid = categoryUuid,
                            toAccountUuid = toAccountUuid,
                        )
                    }
                }
            return OperationEntity(
                opId = UUID.randomUUID().toString(),
                deviceId = deviceId,
                entityKind = EntityKind.Transaction.name,
                entityUuid = uuid,
                opType = if (deleted || payload == null) OpType.Delete.name else OpType.Upsert.name,
                payload = payload,
                updatedAt = updatedAt,
            )
        }
    }
