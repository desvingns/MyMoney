package com.kshavrin.mymoney.core.database.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.database.dao.AccountDao
import com.kshavrin.mymoney.core.database.dao.CategoryDao
import com.kshavrin.mymoney.core.database.dao.OperationDao
import com.kshavrin.mymoney.core.database.dao.TransactionDao
import com.kshavrin.mymoney.core.database.entity.OperationEntity
import com.kshavrin.mymoney.core.database.entity.TransactionEntity
import com.kshavrin.mymoney.core.database.journal.OperationPayloadCodec
import com.kshavrin.mymoney.core.database.mapper.toDomain
import com.kshavrin.mymoney.core.database.mapper.toEntity
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.CategoryGroup
import com.kshavrin.mymoney.core.domain.repository.CategorySummary
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import com.kshavrin.mymoney.core.domain.repository.TransferRow
import com.kshavrin.mymoney.core.domain.sync.DeviceIdProvider
import com.kshavrin.mymoney.core.domain.sync.EntityKind
import com.kshavrin.mymoney.core.domain.sync.OpType
import com.kshavrin.mymoney.core.domain.time.PeriodArithmetic
import com.kshavrin.mymoney.core.domain.transaction.TransactionRunner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepositoryImpl
    @Inject
    constructor(
        private val dao: TransactionDao,
        private val accountDao: AccountDao,
        private val categoryDao: CategoryDao,
        private val operationDao: OperationDao,
        private val payloadCodec: OperationPayloadCodec,
        private val deviceIdProvider: DeviceIdProvider,
        private val clock: Clock,
        private val transactionRunner: TransactionRunner,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : TransactionRepository {
        override fun observeRecent(limit: Int): Flow<List<Transaction>> =
            dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

        override fun observeAll(): Flow<List<Transaction>> =
            dao.observeAll().map { list -> list.map { it.toDomain() } }

        override fun paged(
            accountId: Long,
            categoryId: Long?,
            from: Instant,
            to: Instant,
        ): Flow<PagingData<Transaction>> =
            Pager(PagingConfig(pageSize = 50, prefetchDistance = 10)) {
                dao.pagedByPeriod(accountId, categoryId, from.toEpochMilli(), to.toEpochMilli())
            }.flow.map { pagingData -> pagingData.map(TransactionEntity::toDomain) }

        override suspend fun findById(id: Long): Transaction? =
            withContext(ioDispatcher) {
                dao.findById(id)?.toDomain()
            }

        override suspend fun findByPeriod(
            accountId: Long,
            period: Period,
        ): List<Transaction> =
            withContext(ioDispatcher) {
                val range = PeriodArithmetic.toEpochMillisRange(period)
                dao.listByPeriod(accountId, range.first, range.last).map { it.toDomain() }
            }

        override suspend fun getCategorySummary(
            accountId: Long,
            period: Period,
            kind: TransactionKind,
        ): List<CategorySummary> =
            withContext(ioDispatcher) {
                val range = PeriodArithmetic.toEpochMillisRange(period)
                dao.getCategorySummary(accountId, range.first, range.last, kind.name.lowercase()).map { it.toDomain() }
            }

        override suspend fun getCategoryGroups(
            accountId: Long,
            period: Period,
        ): List<CategoryGroup> =
            withContext(ioDispatcher) {
                val range = PeriodArithmetic.toEpochMillisRange(period)
                dao.getCategoryGroups(accountId, range.first, range.last).map { it.toDomain() }
            }

        override suspend fun getTransfers(
            accountId: Long?,
            period: Period,
        ): List<TransferRow> =
            withContext(ioDispatcher) {
                val range = PeriodArithmetic.toEpochMillisRange(period)
                dao.getTransfers(accountId, range.first, range.last).map { it.toDomain() }
            }

        override suspend fun searchByNote(
            query: String,
            limit: Int,
        ): List<Transaction> =
            withContext(ioDispatcher) {
                dao.searchByNote(query, limit).map { it.toDomain() }
            }

        override suspend fun listForTimezoneNormalization(): List<Transaction> =
            withContext(ioDispatcher) {
                dao.listForTimezoneNormalization().map { it.toDomain() }
            }

        override suspend fun updateOccurredAts(
            updates: Map<Long, Instant>,
            updatedAt: Instant,
        ) =
            withContext(ioDispatcher) {
                updates.forEach { (id, occurredAt) ->
                    dao.updateOccurredAt(id, occurredAt.toEpochMilli(), updatedAt.toEpochMilli())
                }
            }

        override suspend fun upsert(transaction: Transaction): Long =
            withContext(ioDispatcher) {
                require(transaction.amount.signum() > 0) { "amount must be > 0; got ${transaction.amount}" }
                if (transaction.kind == TransactionKind.Transfer) {
                    require(transaction.toAccountId != null) { "transfer requires toAccountId" }
                    require(transaction.toAccountId != transaction.accountId) { "transfer toAccountId must differ from accountId" }
                } else {
                    require(transaction.categoryId != null) { "non-transfer requires categoryId" }
                }
                require((transaction.note?.length ?: 0) <= 256) { "note must be <= 256 chars" }
                val now = clock.instant()
                val deviceId = deviceIdProvider.deviceId()
                transactionRunner.runInTransaction {
                    val existing = transaction.id.takeIf { it != 0L }?.let { dao.findById(it) }
                    val entity =
                        transaction
                            .toEntity()
                            .copy(
                                uuid = existing?.uuid?.takeIf(String::isNotBlank) ?: UUID.randomUUID().toString(),
                                deviceId = deviceId,
                                updatedAt = now.toEpochMilli(),
                            )
                    require(entity.uuid.isNotBlank()) { "transaction uuid must not be blank" }
                    val id = dao.upsert(entity).takeIf { entity.id == 0L } ?: entity.id
                    val persisted = entity.copy(id = id)
                    val accountUuid = requireAccountUuid(persisted.accountId)
                    val categoryUuid = persisted.categoryId?.let { requireCategoryUuid(it) }
                    val toAccountUuid = persisted.toAccountId?.let { requireAccountUuid(it) }
                    operationDao.insert(
                        operation(
                            deviceId = deviceId,
                            entityUuid = persisted.uuid,
                            opType = OpType.Upsert,
                            payload = payloadCodec.encodeTransaction(persisted, accountUuid, categoryUuid, toAccountUuid),
                            updatedAt = now,
                        ),
                    )
                    id
                }
            }

        override suspend fun softDelete(
            id: Long,
            now: Instant,
        ) =
            withContext(ioDispatcher) {
                val updatedAt = clock.instant()
                val deviceId = deviceIdProvider.deviceId()
                transactionRunner.runInTransaction {
                    val existing = requireNotNull(dao.findById(id)) { "transaction not found: $id" }
                    require(existing.uuid.isNotBlank()) { "transaction uuid must not be blank" }
                    dao.upsert(existing.copy(isDeleted = true, deviceId = deviceId, updatedAt = updatedAt.toEpochMilli()))
                    operationDao.insert(
                        operation(
                            deviceId = deviceId,
                            entityUuid = existing.uuid,
                            opType = OpType.Delete,
                            payload = null,
                            updatedAt = updatedAt,
                        ),
                    )
                }
            }

        override suspend fun restore(
            id: Long,
            now: Instant,
        ) =
            withContext(ioDispatcher) {
                dao.restore(id, now.toEpochMilli())
            }

        override suspend fun pruneDeleted(before: Instant) =
            withContext(ioDispatcher) {
                dao.pruneDeleted(before.toEpochMilli())
            }

        override suspend fun countByAccount(id: Long): Int =
            withContext(ioDispatcher) {
                dao.countByAccount(id)
            }

        override suspend fun countByCategory(id: Long): Int =
            withContext(ioDispatcher) {
                dao.countByCategory(id)
            }

        override suspend fun countByCurrency(id: Long): Int =
            withContext(ioDispatcher) {
                dao.countByCurrency(id)
            }

        private suspend fun requireAccountUuid(id: Long): String {
            val uuid = requireNotNull(accountDao.findById(id)) { "account not found: $id" }.uuid
            require(uuid.isNotBlank()) { "account uuid must not be blank" }
            return uuid
        }

        private suspend fun requireCategoryUuid(id: Long): String {
            val uuid = requireNotNull(categoryDao.findById(id)) { "category not found: $id" }.uuid
            require(uuid.isNotBlank()) { "category uuid must not be blank" }
            return uuid
        }

        private fun operation(
            deviceId: String,
            entityUuid: String,
            opType: OpType,
            payload: String?,
            updatedAt: Instant,
        ): OperationEntity =
            OperationEntity(
                opId = UUID.randomUUID().toString(),
                deviceId = deviceId,
                entityKind = EntityKind.Transaction.name,
                entityUuid = entityUuid,
                opType = opType.name,
                payload = payload,
                updatedAt = updatedAt.toEpochMilli(),
            )
    }
