package com.kshavrin.mymoney.core.database.journal

import com.kshavrin.mymoney.core.database.dao.CurrencyDao
import com.kshavrin.mymoney.core.database.entity.AccountEntity
import com.kshavrin.mymoney.core.database.entity.CategoryEntity
import com.kshavrin.mymoney.core.database.entity.TransactionEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OperationPayloadCodec
    @Inject
    constructor(
        private val currencyDao: CurrencyDao,
    ) {
        private val json =
            Json {
                encodeDefaults = true
                ignoreUnknownKeys = true
            }

        // Currency rows are local autoincrement ids that are NOT stable across devices/installs;
        // the wire format carries the portable ISO code instead, resolved back to a local id on apply.
        private suspend fun currencyCodeOf(currencyId: Long): String =
            requireNotNull(currencyDao.findById(currencyId)?.code) {
                "currency $currencyId referenced by a local entity must exist locally"
            }

        suspend fun encodeTransaction(
            entity: TransactionEntity,
            accountUuid: String,
            categoryUuid: String?,
            toAccountUuid: String?,
        ): String =
            json.encodeToString(
                TransactionSnapshot(
                    uuid = entity.uuid,
                    deviceId = entity.deviceId,
                    kind = entity.kind,
                    amount = decimal(entity.amount),
                    currencyCode = currencyCodeOf(entity.currencyId),
                    accountUuid = accountUuid,
                    categoryUuid = categoryUuid,
                    note = entity.note,
                    occurredAt = entity.occurredAt,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                    isDeleted = entity.isDeleted,
                    toAccountUuid = toAccountUuid,
                    toAmount = entity.toAmount?.let(::decimal),
                    exchangeRate = entity.exchangeRate,
                ),
            )

        fun decodeTransaction(payload: String): TransactionSnapshot = json.decodeFromString(payload)

        fun encodeCategory(entity: CategoryEntity): String =
            json.encodeToString(
                CategorySnapshot(
                    uuid = entity.uuid,
                    deviceId = entity.deviceId,
                    name = entity.name,
                    kind = entity.kind,
                    iconKey = entity.iconKey,
                    colorHex = entity.colorHex,
                    textColor = entity.textColor,
                    sortOrder = entity.sortOrder,
                    isDefault = entity.isDefault,
                    isArchived = entity.isArchived,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                ),
            )

        fun decodeCategory(payload: String): CategorySnapshot = json.decodeFromString(payload)

        suspend fun encodeAccount(entity: AccountEntity): String =
            json.encodeToString(
                AccountSnapshot(
                    uuid = entity.uuid,
                    deviceId = entity.deviceId,
                    name = entity.name,
                    currencyCode = currencyCodeOf(entity.currencyId),
                    initialBalance = decimal(entity.initialBalance),
                    type = entity.type,
                    colorHex = entity.colorHex,
                    iconKey = entity.iconKey,
                    isDefault = entity.isDefault,
                    sortOrder = entity.sortOrder,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                    isArchived = entity.isArchived,
                ),
            )

        fun decodeAccount(payload: String): AccountSnapshot = json.decodeFromString(payload)

        private fun decimal(value: Double): String = BigDecimal.valueOf(value).toPlainString()
    }

@Serializable
data class TransactionSnapshot(
    val uuid: String,
    val deviceId: String,
    val kind: String,
    val amount: String,
    // Blank/absent when decoding a pre-migration peer file that still carries the old raw
    // currencyId; findByCode("") never matches, so the op is skipped (retried) rather than crashed.
    val currencyCode: String = "",
    val accountUuid: String,
    val categoryUuid: String?,
    val note: String?,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean,
    val toAccountUuid: String?,
    val toAmount: String?,
    val exchangeRate: Double?,
)

@Serializable
data class CategorySnapshot(
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

@Serializable
data class AccountSnapshot(
    val uuid: String,
    val deviceId: String,
    val name: String,
    // Blank/absent when decoding a pre-migration peer file that still carries the old raw
    // currencyId; findByCode("") never matches, so the op is skipped (retried) rather than crashed.
    val currencyCode: String = "",
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
