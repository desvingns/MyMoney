package com.kshavrin.mymoney.core.database.journal

import com.kshavrin.mymoney.core.database.dao.AccountDao
import com.kshavrin.mymoney.core.database.dao.CategoryDao
import com.kshavrin.mymoney.core.database.dao.CurrencyDao
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

data class ApplyResult(
    val appliedCount: Int,
    val hadSkips: Boolean,
)

@Singleton
class JournalApplier
    @Inject
    constructor(
        private val transactionDao: TransactionDao,
        private val categoryDao: CategoryDao,
        private val accountDao: AccountDao,
        private val currencyDao: CurrencyDao,
        private val operationDao: OperationDao,
        private val payloadCodec: OperationPayloadCodec,
        private val transactionRunner: TransactionRunner,
    ) {
        suspend fun apply(remoteOps: List<Operation>): ApplyResult {
            if (remoteOps.isEmpty()) return ApplyResult(appliedCount = 0, hadSkips = false)
            return transactionRunner.runInTransaction {
                val known = operationDao.knownOpIds().toHashSet()
                val fresh = remoteOps.filterNot { known.contains(it.opId) }.distinctBy { it.opId }
                if (fresh.isEmpty()) return@runInTransaction ApplyResult(appliedCount = 0, hadSkips = false)

                val byEntity = fresh.groupBy { it.entityUuid }
                // Targets of transaction FKs must exist first: accounts and categories before transactions.
                val accountGroups = byEntity.filterValues { ops -> ops.first().entityKind == EntityKind.Account }
                val categoryGroups = byEntity.filterValues { ops -> ops.first().entityKind == EntityKind.Category }
                val transactionGroups = byEntity.filterValues { ops -> ops.first().entityKind == EntityKind.Transaction }

                val appliedUuids = HashSet<String>()
                accountGroups.forEach { (uuid, ops) -> if (applyAccount(uuid, ops)) appliedUuids.add(uuid) }
                categoryGroups.forEach { (uuid, ops) -> if (applyCategory(uuid, ops)) appliedUuids.add(uuid) }
                transactionGroups.forEach { (uuid, ops) -> if (applyTransaction(uuid, ops)) appliedUuids.add(uuid) }

                val appliedOps = fresh.filter { appliedUuids.contains(it.entityUuid) }
                operationDao.insertApplied(appliedOps.map { it.toAppliedEntity() })

                ApplyResult(appliedCount = appliedOps.size, hadSkips = appliedOps.size < fresh.size)
            }
        }

        private suspend fun applyAccount(
            uuid: String,
            remoteOps: List<Operation>,
        ): Boolean {
            val local = accountDao.findByUuid(uuid)
            return when (val result = OperationMerger.resolve(remoteOps + listOfNotNull(local?.toLocalOp()))) {
                is MergeResult.Resolved -> {
                    val payload = result.op.payload ?: return false
                    val snapshot = payloadCodec.decodeAccount(payload)
                    val currencyId = currencyDao.findByCode(snapshot.currencyCode)?.id ?: return false
                    accountDao.upsert(snapshot.toEntity(local?.id ?: 0L, currencyId))
                    true
                }
                is MergeResult.Tombstone -> {
                    accountDao.archiveByUuid(uuid)
                    true
                }
                MergeResult.None -> true
            }
        }

        private suspend fun applyCategory(
            uuid: String,
            remoteOps: List<Operation>,
        ): Boolean {
            val local = categoryDao.findByUuid(uuid)
            return when (val result = OperationMerger.resolve(remoteOps + listOfNotNull(local?.toLocalOp()))) {
                is MergeResult.Resolved -> {
                    val payload = result.op.payload ?: return false
                    val snapshot = payloadCodec.decodeCategory(payload)
                    categoryDao.upsert(snapshot.toEntity(local?.id ?: 0L))
                    true
                }
                is MergeResult.Tombstone -> {
                    categoryDao.archiveByUuid(uuid)
                    true
                }
                MergeResult.None -> true
            }
        }

        private suspend fun applyTransaction(
            uuid: String,
            remoteOps: List<Operation>,
        ): Boolean {
            val local = transactionDao.findByUuid(uuid)
            val candidates = remoteOps + listOfNotNull(local?.toLocalOp())
            return when (val result = OperationMerger.resolve(candidates)) {
                is MergeResult.Resolved -> {
                    val payload = result.op.payload ?: return false
                    val snapshot = payloadCodec.decodeTransaction(payload)
                    val currencyId = currencyDao.findByCode(snapshot.currencyCode)?.id ?: return false
                    val accountId = accountDao.findByUuid(snapshot.accountUuid)?.id ?: return false
                    val categoryId = snapshot.categoryUuid?.let { categoryDao.findByUuid(it)?.id ?: return false }
                    val toAccountId = snapshot.toAccountUuid?.let { accountDao.findByUuid(it)?.id ?: return false }
                    transactionDao.upsert(snapshot.toEntity(local?.id ?: 0L, accountId, categoryId, toAccountId, currencyId))
                    true
                }
                is MergeResult.Tombstone -> {
                    transactionDao.softDeleteByUuid(uuid, candidates.maxOf { it.updatedAt }.toEpochMilli())
                    true
                }
                MergeResult.None -> true
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

        private suspend fun AccountEntity.toLocalOp(): Operation =
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

        private suspend fun TransactionEntity.toLocalOp(): Operation {
            val accountUuid = accountDao.findById(accountId)?.uuid
            val categoryUuid = categoryId?.let { categoryDao.findById(it)?.uuid }
            val toAccountUuid = toAccountId?.let { accountDao.findById(it)?.uuid }
            return Operation(
                opId = LOCAL_OP_PREFIX + uuid,
                deviceId = deviceId,
                entityKind = EntityKind.Transaction,
                entityUuid = uuid,
                opType = if (isDeleted) OpType.Delete else OpType.Upsert,
                payload =
                    if (isDeleted || accountUuid == null) {
                        null
                    } else {
                        payloadCodec.encodeTransaction(this, accountUuid, categoryUuid, toAccountUuid)
                    },
                updatedAt = Instant.ofEpochMilli(updatedAt),
            )
        }

        private fun AccountSnapshot.toEntity(
            localId: Long,
            currencyId: Long,
        ): AccountEntity =
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
            currencyId: Long,
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
