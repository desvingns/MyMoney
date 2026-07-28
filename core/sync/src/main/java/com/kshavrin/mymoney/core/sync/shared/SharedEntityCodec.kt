package com.kshavrin.mymoney.core.sync.shared

import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Translates local finance domain entities to and from the JSON [String] carried by a
 * [com.kshavrin.mymoney.core.domain.sync.SharedOperation]. Money crosses the wire as a plain
 * decimal string (never a Double) and time as epoch-millis, matching the app's domain conventions.
 */
@Singleton
class SharedEntityCodec
    @Inject
    constructor(
        private val json: Json,
    ) {
        fun encodeTransaction(
            transaction: Transaction,
            uuid: String,
        ): String =
            json.encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    put("uuid", uuid)
                    put("id", transaction.id)
                    put("kind", transaction.kind.name)
                    put("amount", transaction.amount.toPlainString())
                    put("currencyId", transaction.currencyId)
                    put("accountId", transaction.accountId)
                    transaction.categoryId?.let { put("categoryId", it) }
                    transaction.note?.let { put("note", it) }
                    put("occurredAt", transaction.occurredAt.toEpochMilli())
                    put("createdAt", transaction.createdAt.toEpochMilli())
                    put("updatedAt", transaction.updatedAt.toEpochMilli())
                    put("isDeleted", transaction.isDeleted)
                    transaction.toAccountId?.let { put("toAccountId", it) }
                    transaction.toAmount?.let { put("toAmount", it.toPlainString()) }
                    transaction.exchangeRate?.let { put("exchangeRate", it) }
                },
            )

        fun decodeTransaction(payload: String): Transaction {
            val obj = json.parseToJsonElement(payload) as JsonObject
            return Transaction(
                id = obj.long("id"),
                kind = TransactionKind.valueOf(obj.string("kind")),
                amount = obj.decimal("amount"),
                currencyId = obj.long("currencyId"),
                accountId = obj.long("accountId"),
                categoryId = obj.longOrNull("categoryId"),
                note = obj.stringOrNull("note"),
                occurredAt = Instant.ofEpochMilli(obj.long("occurredAt")),
                createdAt = Instant.ofEpochMilli(obj.long("createdAt")),
                updatedAt = Instant.ofEpochMilli(obj.long("updatedAt")),
                isDeleted = obj.boolOrNull("isDeleted") ?: false,
                toAccountId = obj.longOrNull("toAccountId"),
                toAmount = obj.decimalOrNull("toAmount"),
                exchangeRate = obj.doubleOrNull("exchangeRate"),
            )
        }

        fun encodeAccount(
            account: Account,
            uuid: String,
        ): String =
            json.encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    put("uuid", uuid)
                    put("id", account.id)
                    put("name", account.name)
                    put("currencyId", account.currencyId)
                    put("initialBalance", account.initialBalance.toPlainString())
                    put("type", account.type.name)
                    put("colorHex", account.colorHex)
                    put("iconKey", account.iconKey)
                    put("isDefault", account.isDefault)
                    put("sortOrder", account.sortOrder)
                    put("createdAt", account.createdAt.toEpochMilli())
                    put("updatedAt", account.updatedAt.toEpochMilli())
                    put("isArchived", account.isArchived)
                },
            )

        fun decodeAccount(payload: String): Account {
            val obj = json.parseToJsonElement(payload) as JsonObject
            return Account(
                id = obj.long("id"),
                name = obj.string("name"),
                currencyId = obj.long("currencyId"),
                initialBalance = obj.decimal("initialBalance"),
                type = AccountType.valueOf(obj.string("type")),
                colorHex = obj.string("colorHex"),
                iconKey = obj.string("iconKey"),
                isDefault = obj.boolOrNull("isDefault") ?: false,
                sortOrder = obj.int("sortOrder"),
                createdAt = Instant.ofEpochMilli(obj.long("createdAt")),
                updatedAt = Instant.ofEpochMilli(obj.long("updatedAt")),
                isArchived = obj.boolOrNull("isArchived") ?: false,
            )
        }

        fun encodeCategory(
            category: Category,
            uuid: String,
        ): String =
            json.encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    put("uuid", uuid)
                    put("id", category.id)
                    put("name", category.name)
                    put("kind", category.kind.name)
                    put("iconKey", category.iconKey)
                    put("colorHex", category.colorHex)
                    put("textColor", category.textColor)
                    put("sortOrder", category.sortOrder)
                    put("isDefault", category.isDefault)
                    put("isArchived", category.isArchived)
                    put("createdAt", category.createdAt.toEpochMilli())
                },
            )

        fun decodeCategory(payload: String): Category {
            val obj = json.parseToJsonElement(payload) as JsonObject
            return Category(
                id = obj.long("id"),
                name = obj.string("name"),
                kind = CategoryKind.valueOf(obj.string("kind")),
                iconKey = obj.string("iconKey"),
                colorHex = obj.string("colorHex"),
                textColor = obj.string("textColor"),
                sortOrder = obj.int("sortOrder"),
                isDefault = obj.boolOrNull("isDefault") ?: false,
                isArchived = obj.boolOrNull("isArchived") ?: false,
                createdAt = Instant.ofEpochMilli(obj.long("createdAt")),
            )
        }

        private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

        private fun JsonObject.stringOrNull(key: String): String? = this[key]?.jsonPrimitive?.content

        private fun JsonObject.long(key: String): Long = getValue(key).jsonPrimitive.long

        private fun JsonObject.longOrNull(key: String): Long? = this[key]?.jsonPrimitive?.long

        private fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.int

        private fun JsonObject.decimal(key: String): BigDecimal = BigDecimal(string(key))

        private fun JsonObject.decimalOrNull(key: String): BigDecimal? = stringOrNull(key)?.let(::BigDecimal)

        private fun JsonObject.doubleOrNull(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull

        private fun JsonObject.boolOrNull(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull
    }
