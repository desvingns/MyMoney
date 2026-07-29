package com.kshavrin.mymoney.core.sync.shared

import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
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
            currency: Currency,
            accountUuid: String,
            categoryUuid: String?,
            toAccountUuid: String?,
        ): String =
            encodeTransaction(
                transaction = transaction,
                uuid = uuid,
                currencyCode = currency.code,
                currency = currency,
                accountUuid = accountUuid,
                categoryUuid = categoryUuid,
                toAccountUuid = toAccountUuid,
            )

        fun encodeTransaction(
            transaction: Transaction,
            uuid: String,
            currencyCode: String,
            accountUuid: String,
            categoryUuid: String?,
            toAccountUuid: String?,
        ): String =
            encodeTransaction(
                transaction = transaction,
                uuid = uuid,
                currencyCode = currencyCode,
                currency = null,
                accountUuid = accountUuid,
                categoryUuid = categoryUuid,
                toAccountUuid = toAccountUuid,
            )

        private fun encodeTransaction(
            transaction: Transaction,
            uuid: String,
            currencyCode: String,
            currency: Currency?,
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
                    currency?.let { put("currency", it.toPayload()) }
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
            val currency: Currency?,
            val accountUuid: String,
            val categoryUuid: String?,
            val toAccountUuid: String?,
        )

        data class CurrencyReference(
            val code: String,
            val currency: Currency?,
        )

        fun decodeTransactionRefs(payload: String): TransactionRefs {
            val obj = json.parseToJsonElement(payload) as JsonObject
            val currency = obj.currencyReference()
            return TransactionRefs(
                currencyCode = currency.code,
                currency = currency.currency,
                accountUuid = obj.string("accountUuid"),
                categoryUuid = obj.stringOrNull("categoryUuid"),
                toAccountUuid = obj.stringOrNull("toAccountUuid"),
            )
        }

        fun decodeTransactionCurrencyReference(payload: String): CurrencyReference =
            (json.parseToJsonElement(payload) as JsonObject).currencyReference()

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
            currency: Currency,
        ): String =
            encodeAccount(
                account = account,
                uuid = uuid,
                currencyCode = currency.code,
                currency = currency,
            )

        fun encodeAccount(
            account: Account,
            uuid: String,
            currencyCode: String,
        ): String =
            encodeAccount(
                account = account,
                uuid = uuid,
                currencyCode = currencyCode,
                currency = null,
            )

        private fun encodeAccount(
            account: Account,
            uuid: String,
            currencyCode: String,
            currency: Currency?,
        ): String =
            json.encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    put("uuid", uuid)
                    put("id", account.id)
                    put("name", account.name)
                    // Currency ids are not stable across installs; carry the ISO code and resolve on apply.
                    put("currencyCode", currencyCode)
                    currency?.let { put("currency", it.toPayload()) }
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
            decodeAccountCurrencyReference(payload).code

        fun decodeAccountCurrency(payload: String): Currency? =
            decodeAccountCurrencyReference(payload).currency

        fun decodeAccountCurrencyReference(payload: String): CurrencyReference =
            (json.parseToJsonElement(payload) as JsonObject).currencyReference()

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
                canonicalizeObject(fields),
            )
        }

        private fun canonicalizeObject(fields: JsonObject): JsonObject =
            buildJsonObject {
                fields.entries
                    .asSequence()
                    .filter { (key) -> key !in LOCAL_ID_FIELDS }
                    .sortedBy { (key) -> key }
                    .forEach { (key, value) -> put(key, canonicalize(value)) }
            }

        private fun canonicalize(element: JsonElement): JsonElement =
            when (element) {
                is JsonObject -> canonicalizeObject(element)
                is JsonArray -> JsonArray(element.map(::canonicalize))
                else -> element
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

        private fun Currency.toPayload(): JsonObject =
            buildJsonObject {
                put("code", code)
                put("symbol", symbol)
                put("name", name)
                put("decimalDigits", decimalDigits)
                put("isActive", isActive)
                put("sortOrder", sortOrder)
            }

        private fun JsonObject.currencyOrNull(): Currency? =
            this["currency"]?.jsonObject?.let { currency ->
                Currency(
                    id = 0L,
                    code = currency.string("code"),
                    symbol = currency.string("symbol"),
                    name = currency.string("name"),
                    decimalDigits = currency.int("decimalDigits"),
                    isActive = currency.boolOrNull("isActive") ?: error("shared currency is missing isActive"),
                    sortOrder = currency.int("sortOrder"),
                )
            }

        private fun JsonObject.currencyReference(): CurrencyReference {
            val code = string("currencyCode")
            val currency = currencyOrNull()
            require(currency == null || currency.code == code) {
                "shared currency code does not match its canonical currency"
            }
            return CurrencyReference(code, currency)
        }

        private companion object {
            val LOCAL_ID_FIELDS = setOf("id", "currencyId", "accountId", "categoryId", "toAccountId")
        }
    }
