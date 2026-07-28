package com.kshavrin.mymoney.core.database.repository

import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.database.dao.AccountDao
import com.kshavrin.mymoney.core.database.dao.OperationDao
import com.kshavrin.mymoney.core.database.entity.OperationEntity
import com.kshavrin.mymoney.core.database.journal.OperationPayloadCodec
import com.kshavrin.mymoney.core.database.mapper.toDomain
import com.kshavrin.mymoney.core.database.mapper.toEntity
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.sync.DeviceIdProvider
import com.kshavrin.mymoney.core.domain.sync.EntityKind
import com.kshavrin.mymoney.core.domain.sync.OpType
import com.kshavrin.mymoney.core.domain.transaction.TransactionRunner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepositoryImpl
    @Inject
    constructor(
        private val dao: AccountDao,
        private val operationDao: OperationDao,
        private val payloadCodec: OperationPayloadCodec,
        private val deviceIdProvider: DeviceIdProvider,
        private val clock: Clock,
        private val transactionRunner: TransactionRunner,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : AccountRepository {
        override fun observeActive(): Flow<List<Account>> = dao.observeActive().map { list -> list.map { it.toDomain() } }

        override suspend fun findById(id: Long): Account? =
            withContext(ioDispatcher) {
                dao.findById(id)?.toDomain()
            }

        override suspend fun findDefault(): Account? =
            withContext(ioDispatcher) {
                dao.findDefault()?.toDomain()
            }

        override suspend fun computeBalance(accountId: Long): BigDecimal =
            withContext(ioDispatcher) {
                BigDecimal.valueOf(dao.computeBalance(accountId))
            }

        override suspend fun upsert(account: Account): Long =
            withContext(ioDispatcher) {
                require(account.name.isNotBlank() && account.name.length <= 32) { "name must be non-blank and <= 32 chars" }
                require(account.colorHex.matches(COLOR_HEX_REGEX)) { "colorHex must match ${COLOR_HEX_REGEX.pattern}; got: ${account.colorHex}" }
                val now = clock.instant()
                val deviceId = deviceIdProvider.deviceId()
                transactionRunner.runInTransaction {
                    val existing = account.id.takeIf { it != 0L }?.let { dao.findById(it) }
                    val entity =
                        account
                            .toEntity()
                            .copy(
                                uuid = existing?.uuid?.takeIf(String::isNotBlank) ?: UUID.randomUUID().toString(),
                                deviceId = deviceId,
                                updatedAt = now.toEpochMilli(),
                            )
                    require(entity.uuid.isNotBlank()) { "account uuid must not be blank" }
                    val id = dao.upsert(entity).takeIf { entity.id == 0L } ?: entity.id
                    val persisted = entity.copy(id = id)
                    operationDao.insert(
                        operation(
                            deviceId = deviceId,
                            entityUuid = persisted.uuid,
                            opType = OpType.Upsert,
                            payload = payloadCodec.encodeAccount(persisted),
                            updatedAt = now,
                        ),
                    )
                    id
                }
            }

        override suspend fun uuidForId(id: Long): String? =
            withContext(ioDispatcher) {
                dao.findById(id)?.uuid?.takeIf(String::isNotBlank)
            }

        override suspend fun applySharedUpsert(
            account: Account,
            uuid: String,
            deviceId: String,
        ) = withContext(ioDispatcher) {
            require(uuid.isNotBlank()) { "shared account uuid must not be blank" }
            val existing = dao.findByUuid(uuid)
            val entity =
                account
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
                require(uuid.isNotBlank()) { "shared account uuid must not be blank" }
                dao.archiveByUuid(uuid)
            }

        override suspend fun archive(id: Long) =
            withContext(ioDispatcher) {
                val now = clock.instant()
                val deviceId = deviceIdProvider.deviceId()
                transactionRunner.runInTransaction {
                    val existing = requireNotNull(dao.findById(id)) { "account not found: $id" }
                    require(existing.uuid.isNotBlank()) { "account uuid must not be blank" }
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

        override suspend fun setDefault(id: Long) =
            withContext(ioDispatcher) {
                val now = clock.instant()
                val deviceId = deviceIdProvider.deviceId()
                transactionRunner.runInTransaction {
                    val previousDefaults = dao.listDefaults()
                    val target = requireNotNull(dao.findById(id)) { "account not found: $id" }
                    val updatedAt = now.toEpochMilli()
                    val changed =
                        (previousDefaults.filter { it.id != id }.map { it.copy(isDefault = false) } + target.copy(isDefault = true))
                            .map { account ->
                                account.copy(deviceId = deviceId, updatedAt = updatedAt)
                            }.distinctBy { it.id }
                    changed.forEach { account ->
                        require(account.uuid.isNotBlank()) { "account uuid must not be blank" }
                        dao.upsert(account)
                        operationDao.insert(
                            operation(
                                deviceId = deviceId,
                                entityUuid = account.uuid,
                                opType = OpType.Upsert,
                                payload = payloadCodec.encodeAccount(account),
                                updatedAt = now,
                            ),
                        )
                    }
                }
            }

        override suspend fun countByCurrency(currencyId: Long): Int =
            withContext(ioDispatcher) {
                dao.countByCurrency(currencyId)
            }

        private companion object {
            val COLOR_HEX_REGEX = Regex("^#[0-9A-Fa-f]{6}$")
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
                entityKind = EntityKind.Account.name,
                entityUuid = entityUuid,
                opType = opType.name,
                payload = payload,
                updatedAt = updatedAt.toEpochMilli(),
            )
    }
