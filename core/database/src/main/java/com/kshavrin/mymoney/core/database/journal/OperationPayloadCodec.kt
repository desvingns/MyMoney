package com.kshavrin.mymoney.core.database.journal

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
    constructor() {
        private val json =
            Json {
                encodeDefaults = true
                ignoreUnknownKeys = true
            }

        fun encodeTransaction(
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
                    currencyId = entity.currencyId,
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

        fun encodeAccount(entity: AccountEntity): String =
            json.encodeToString(
                AccountSnapshot(
                    uuid = entity.uuid,
                    deviceId = entity.deviceId,
                    name = entity.name,
                    currencyId = entity.currencyId,
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
    val currencyId: Long,
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
    val currencyId: Long,
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
