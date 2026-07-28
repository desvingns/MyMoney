package com.kshavrin.mymoney.core.database.repository

import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.database.dao.CategoryDao
import com.kshavrin.mymoney.core.database.dao.OperationDao
import com.kshavrin.mymoney.core.database.entity.OperationEntity
import com.kshavrin.mymoney.core.database.journal.OperationPayloadCodec
import com.kshavrin.mymoney.core.database.mapper.toDomain
import com.kshavrin.mymoney.core.database.mapper.toEntity
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import com.kshavrin.mymoney.core.domain.sync.DeviceIdProvider
import com.kshavrin.mymoney.core.domain.sync.EntityKind
import com.kshavrin.mymoney.core.domain.sync.OpType
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
class CategoryRepositoryImpl
    @Inject
    constructor(
        private val dao: CategoryDao,
        private val operationDao: OperationDao,
        private val payloadCodec: OperationPayloadCodec,
        private val deviceIdProvider: DeviceIdProvider,
        private val clock: Clock,
        private val transactionRunner: TransactionRunner,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : CategoryRepository {
        override fun observeByKind(kind: CategoryKind): Flow<List<Category>> =
            dao.observeByKind(kind.name.lowercase()).map { list -> list.map { it.toDomain() } }

        override fun observeAll(): Flow<List<Category>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

        override suspend fun findById(id: Long): Category? =
            withContext(ioDispatcher) {
                dao.findById(id)?.toDomain()
            }

        override suspend fun upsert(category: Category): Long =
            withContext(ioDispatcher) {
                require(category.name.isNotBlank() && category.name.length <= 32) { "name must be non-blank and <= 32 chars" }
                val now = clock.instant()
                val deviceId = deviceIdProvider.deviceId()
                transactionRunner.runInTransaction {
                    val existing = category.id.takeIf { it != 0L }?.let { dao.findById(it) }
                    val entity =
                        category
                            .toEntity()
                            .copy(
                                uuid = existing?.uuid?.takeIf(String::isNotBlank) ?: UUID.randomUUID().toString(),
                                deviceId = deviceId,
                                updatedAt = now.toEpochMilli(),
                            )
                    require(entity.uuid.isNotBlank()) { "category uuid must not be blank" }
                    val id = dao.upsert(entity).takeIf { entity.id == 0L } ?: entity.id
                    val persisted = entity.copy(id = id)
                    operationDao.insert(
                        operation(
                            deviceId = deviceId,
                            entityUuid = persisted.uuid,
                            opType = OpType.Upsert,
                            payload = payloadCodec.encodeCategory(persisted),
                            updatedAt = now,
                        ),
                    )
                    id
                }
            }

        override suspend fun upsertAll(categories: List<Category>) =
            withContext(ioDispatcher) {
                if (categories.isEmpty()) {
                    return@withContext
                }
                val now = clock.instant()
                val deviceId = deviceIdProvider.deviceId()
                transactionRunner.runInTransaction {
                    categories.forEach { category ->
                        require(category.name.isNotBlank() && category.name.length <= 32) { "name must be non-blank and <= 32 chars" }
                        val existing = category.id.takeIf { it != 0L }?.let { dao.findById(it) }
                        val entity =
                            category
                                .toEntity()
                                .copy(
                                    uuid = existing?.uuid?.takeIf(String::isNotBlank) ?: UUID.randomUUID().toString(),
                                    deviceId = deviceId,
                                    updatedAt = now.toEpochMilli(),
                                )
                        require(entity.uuid.isNotBlank()) { "category uuid must not be blank" }
                        val id = dao.upsert(entity).takeIf { entity.id == 0L } ?: entity.id
                        val persisted = entity.copy(id = id)
                        operationDao.insert(
                            operation(
                                deviceId = deviceId,
                                entityUuid = persisted.uuid,
                                opType = OpType.Upsert,
                                payload = payloadCodec.encodeCategory(persisted),
                                updatedAt = now,
                            ),
                        )
                    }
                }
            }

        override suspend fun uuidForId(id: Long): String? =
            withContext(ioDispatcher) {
                dao.findById(id)?.uuid?.takeIf(String::isNotBlank)
            }

        override suspend fun applySharedUpsert(
            category: Category,
            uuid: String,
            deviceId: String,
        ) = withContext(ioDispatcher) {
            require(uuid.isNotBlank()) { "shared category uuid must not be blank" }
            val existing = dao.findByUuid(uuid)
            val entity =
                category
                    .toEntity()
                    .copy(
                        id = existing?.id ?: 0L,
                        uuid = uuid,
                        deviceId = deviceId,
                    )
            dao.upsert(entity)
            Unit
        }

        override suspend fun applySharedArchive(uuid: String) =
            withContext(ioDispatcher) {
                require(uuid.isNotBlank()) { "shared category uuid must not be blank" }
                dao.archiveByUuid(uuid)
            }

        override suspend fun archive(id: Long) =
            withContext(ioDispatcher) {
                val now = clock.instant()
                val deviceId = deviceIdProvider.deviceId()
                transactionRunner.runInTransaction {
                    val existing = requireNotNull(dao.findById(id)) { "category not found: $id" }
                    require(existing.uuid.isNotBlank()) { "category uuid must not be blank" }
                    dao.upsert(existing.copy(isArchived = true, deviceId = deviceId, updatedAt = now.toEpochMilli()))
                    operationDao.insert(
                        operation(
                            deviceId = deviceId,
                            entityUuid = existing.uuid,
                            opType = OpType.Delete,
                            payload = null,
                            updatedAt = now,
                        ),
                    )
                }
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
                entityKind = EntityKind.Category.name,
                entityUuid = entityUuid,
                opType = opType.name,
                payload = payload,
                updatedAt = updatedAt.toEpochMilli(),
            )
    }
