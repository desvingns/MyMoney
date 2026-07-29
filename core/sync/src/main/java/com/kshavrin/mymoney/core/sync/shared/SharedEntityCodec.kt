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
            currencyCode: String,
            accountUuid: String,
            categoryUuid: String?,
            toAccountUuid: String?,
        ): String =
            json.encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    put("uuid", uuid)
                    put("id", transaction.id)
                    put("kind", transaction.kind.name)
                    put("amount", transaction.amount.toPlainString())
                    // Currency ids are not stable across installs; carry the ISO code and resolve on apply.
                    put("currencyCode", currencyCode)
                    put("currencyId", transaction.currencyId)
                    // Portable cross-device references: apply resolves these uuids back to LOCAL ids.
                    // The numeric *Id fields are the sender's local Room ids, kept only for debugging.
                    put("accountUuid", accountUuid)
                    put("accountId", transaction.accountId)
                    categoryUuid?.let { put("categoryUuid", it) }
                    transaction.categoryId?.let { put("categoryId", it) }
                    toAccountUuid?.let { put("toAccountUuid", it) }
                    transaction.toAccountId?.let { put("toAccountId", it) }
                    transaction.note?.let { put("note", it) }
                    put("occurredAt", transaction.occurredAt.toEpochMilli())
                    put("createdAt", transaction.createdAt.toEpochMilli())
                    put("updatedAt", transaction.updatedAt.toEpochMilli())
                    put("isDeleted", transaction.isDeleted)
                    transaction.toAmount?.let { put("toAmount", it.toPlainString()) }
                    transaction.exchangeRate?.let { put("exchangeRate", it) }
                },
            )

        /** Portable references carried by a transaction payload, resolved to LOCAL ids on apply. */
        data class TransactionRefs(
            val currencyCode: String,
            val accountUuid: String,
            val categoryUuid: String?,
            val toAccountUuid: String?,
        )

        fun decodeTransactionRefs(payload: String): TransactionRefs {
            val obj = json.parseToJsonElement(payload) as JsonObject
            return TransactionRefs(
                currencyCode = obj.string("currencyCode"),
                accountUuid = obj.string("accountUuid"),
                categoryUuid = obj.stringOrNull("categoryUuid"),
                toAccountUuid = obj.stringOrNull("toAccountUuid"),
            )
        }

        /**
         * Decode with placeholder currency/account/category ids (0). The caller MUST overwrite
         * currencyId/accountId/categoryId/toAccountId with LOCAL ids resolved from [TransactionRefs].
         */
        fun decodeTransaction(payload: String): Transaction {
            val obj = json.parseToJsonElement(payload) as JsonObject
            return Transaction(
                id = obj.long("id"),
                kind = TransactionKind.valueOf(obj.string("kind")),
                amount = obj.decimal("amount"),
                currencyId = 0L,
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
            currencyCode: String,
        ): String =
            json.encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    put("uuid", uuid)
                    put("id", account.id)
                    put("name", account.name)
                    // Currency ids are not stable across installs; carry the ISO code and resolve on apply.
                    put("currencyCode", currencyCode)
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

        fun decodeAccountCurrencyCode(payload: String): String =
            (json.parseToJsonElement(payload) as JsonObject).string("currencyCode")

        /**
         * Decode with a placeholder currencyId (0). The caller MUST overwrite currencyId with the
         * LOCAL id resolved from [decodeAccountCurrencyCode].
         */
        fun decodeAccount(payload: String): Account {
            val obj = json.parseToJsonElement(payload) as JsonObject
            return Account(
                id = obj.long("id"),
                name = obj.string("name"),
                currencyId = 0L,
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

        fun canonicalPayload(payload: String): String {
            val fields = json.parseToJsonElement(payload) as JsonObject
            return json.encodeToString(
                JsonObject.serializer(),
                JsonObject(fields.filterKeys { it !in LOCAL_ID_FIELDS }),
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

        private companion object {
            val LOCAL_ID_FIELDS = setOf("id", "currencyId", "accountId", "categoryId", "toAccountId")
        }
    }
