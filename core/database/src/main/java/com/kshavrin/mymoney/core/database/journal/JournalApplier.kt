package com.kshavrin.mymoney.core.database.journal

import com.kshavrin.mymoney.core.database.dao.AccountDao
import com.kshavrin.mymoney.core.database.dao.CategoryDao
import com.kshavrin.mymoney.core.database.dao.OperationDao
import com.kshavrin.mymoney.core.database.dao.TransactionDao
import com.kshavrin.mymoney.core.database.entity.AccountEntity
import com.kshavrin.mymoney.core.database.entity.CategoryEntity
import com.kshavrin.mymoney.core.database.entity.OperationEntity
import com.kshavrin.mymoney.core.database.entity.TransactionEntity
import com.kshavrin.mymoney.core.domain.sync.EntityKind
import com.kshavrin.mymoney.core.domain.sync.MergeResult
import com.kshavrin.mymoney.core.domain.sync.OpType
import com.kshavrin.mymoney.core.domain.sync.Operation
import com.kshavrin.mymoney.core.domain.sync.OperationMerger
import com.kshavrin.mymoney.core.domain.transaction.TransactionRunner
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JournalApplier
    @Inject
    constructor(
        private val transactionDao: TransactionDao,
        private val categoryDao: CategoryDao,
        private val accountDao: AccountDao,
        private val operationDao: OperationDao,
        private val payloadCodec: OperationPayloadCodec,
        private val transactionRunner: TransactionRunner,
    ) {
        suspend fun apply(remoteOps: List<Operation>) {
            if (remoteOps.isEmpty()) return
            transactionRunner.runInTransaction {
                val known = operationDao.knownOpIds().toHashSet()
                val fresh = remoteOps.filterNot { known.contains(it.opId) }.distinctBy { it.opId }
                if (fresh.isEmpty()) return@runInTransaction

                val byEntity = fresh.groupBy { it.entityUuid }
                // Targets of transaction FKs must exist first: accounts and categories before transactions.
                val accountGroups = byEntity.filterValues { ops -> ops.first().entityKind == EntityKind.Account }
                val categoryGroups = byEntity.filterValues { ops -> ops.first().entityKind == EntityKind.Category }
                val transactionGroups = byEntity.filterValues { ops -> ops.first().entityKind == EntityKind.Transaction }

                accountGroups.forEach { (uuid, ops) -> applyAccount(uuid, ops) }
                categoryGroups.forEach { (uuid, ops) -> applyCategory(uuid, ops) }
                transactionGroups.forEach { (uuid, ops) -> applyTransaction(uuid, ops) }

                operationDao.insertApplied(fresh.map { it.toAppliedEntity() })
            }
        }

        private suspend fun applyAccount(
            uuid: String,
            remoteOps: List<Operation>,
        ) {
            val local = accountDao.findByUuid(uuid)
            when (val result = OperationMerger.resolve(remoteOps + listOfNotNull(local?.toLocalOp()))) {
                is MergeResult.Resolved -> {
                    val payload = result.op.payload ?: return
                    val snapshot = payloadCodec.decodeAccount(payload)
                    accountDao.upsert(snapshot.toEntity(local?.id ?: 0L))
                }
                is MergeResult.Tombstone -> accountDao.archiveByUuid(uuid)
                MergeResult.None -> Unit
            }
        }

        private suspend fun applyCategory(
            uuid: String,
            remoteOps: List<Operation>,
        ) {
            val local = categoryDao.findByUuid(uuid)
            when (val result = OperationMerger.resolve(remoteOps + listOfNotNull(local?.toLocalOp()))) {
                is MergeResult.Resolved -> {
                    val payload = result.op.payload ?: return
                    val snapshot = payloadCodec.decodeCategory(payload)
                    categoryDao.upsert(snapshot.toEntity(local?.id ?: 0L))
                }
                is MergeResult.Tombstone -> categoryDao.archiveByUuid(uuid)
                MergeResult.None -> Unit
            }
        }

        private suspend fun applyTransaction(
            uuid: String,
            remoteOps: List<Operation>,
        ) {
            val local = transactionDao.findByUuid(uuid)
            when (val result = OperationMerger.resolve(remoteOps + listOfNotNull(local?.toLocalOp()))) {
                is MergeResult.Resolved -> {
                    val payload = result.op.payload ?: return
                    val snapshot = payloadCodec.decodeTransaction(payload)
                    val accountId = accountDao.findByUuid(snapshot.accountUuid)?.id ?: return
                    val categoryId = snapshot.categoryUuid?.let { categoryDao.findByUuid(it)?.id ?: return }
                    val toAccountId = snapshot.toAccountUuid?.let { accountDao.findByUuid(it)?.id ?: return }
                    transactionDao.upsert(snapshot.toEntity(local?.id ?: 0L, accountId, categoryId, toAccountId))
                }
                is MergeResult.Tombstone -> transactionDao.softDeleteByUuid(uuid, remoteOps.maxOf { it.updatedAt }.toEpochMilli())
                MergeResult.None -> Unit
            }
        }

        private fun Operation.toAppliedEntity(): OperationEntity =
            OperationEntity(
                opId = opId,
                deviceId = deviceId,
                entityKind = entityKind.name,
                entityUuid = entityUuid,
                opType = opType.name,
                payload = payload,
                updatedAt = updatedAt.toEpochMilli(),
                syncedToRemote = true,
                appliedFromRemote = true,
            )

        private fun AccountEntity.toLocalOp(): Operation =
            Operation(
                opId = LOCAL_OP_PREFIX + uuid,
                deviceId = deviceId,
                entityKind = EntityKind.Account,
                entityUuid = uuid,
                opType = if (isArchived) OpType.Delete else OpType.Upsert,
                payload = payloadCodec.encodeAccount(this),
                updatedAt = Instant.ofEpochMilli(updatedAt),
            )

        private fun CategoryEntity.toLocalOp(): Operation =
            Operation(
                opId = LOCAL_OP_PREFIX + uuid,
                deviceId = deviceId,
                entityKind = EntityKind.Category,
                entityUuid = uuid,
                opType = if (isArchived) OpType.Delete else OpType.Upsert,
                payload = payloadCodec.encodeCategory(this),
                updatedAt = Instant.ofEpochMilli(updatedAt),
            )

        private fun TransactionEntity.toLocalOp(): Operation =
            Operation(
                opId = LOCAL_OP_PREFIX + uuid,
                deviceId = deviceId,
                entityKind = EntityKind.Transaction,
                entityUuid = uuid,
                opType = if (isDeleted) OpType.Delete else OpType.Upsert,
                payload = null,
                updatedAt = Instant.ofEpochMilli(updatedAt),
            )

        private fun AccountSnapshot.toEntity(localId: Long): AccountEntity =
            AccountEntity(
                id = localId,
                uuid = uuid,
                deviceId = deviceId,
                name = name,
                currencyId = currencyId,
                initialBalance = BigDecimal(initialBalance).toDouble(),
                type = type,
                colorHex = colorHex,
                iconKey = iconKey,
                isDefault = isDefault,
                sortOrder = sortOrder,
                createdAt = createdAt,
                updatedAt = updatedAt,
                isArchived = isArchived,
            )

        private fun CategorySnapshot.toEntity(localId: Long): CategoryEntity =
            CategoryEntity(
                id = localId,
                uuid = uuid,
                deviceId = deviceId,
                name = name,
                kind = kind,
                iconKey = iconKey,
                colorHex = colorHex,
                textColor = textColor,
                sortOrder = sortOrder,
                isDefault = isDefault,
                isArchived = isArchived,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )

        private fun TransactionSnapshot.toEntity(
            localId: Long,
            accountId: Long,
            categoryId: Long?,
            toAccountId: Long?,
        ): TransactionEntity =
            TransactionEntity(
                id = localId,
                uuid = uuid,
                deviceId = deviceId,
                kind = kind,
                amount = BigDecimal(amount).toDouble(),
                currencyId = currencyId,
                accountId = accountId,
                categoryId = categoryId,
                note = note,
                occurredAt = occurredAt,
                createdAt = createdAt,
                updatedAt = updatedAt,
                isDeleted = isDeleted,
                toAccountId = toAccountId,
                toAmount = toAmount?.let { BigDecimal(it).toDouble() },
                exchangeRate = exchangeRate,
            )

        private companion object {
            const val LOCAL_OP_PREFIX = "local:"
        }
    }
