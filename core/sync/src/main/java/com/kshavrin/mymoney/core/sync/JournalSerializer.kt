package com.kshavrin.mymoney.core.sync

import com.kshavrin.mymoney.core.domain.sync.EntityKind
import com.kshavrin.mymoney.core.domain.sync.OpType
import com.kshavrin.mymoney.core.domain.sync.Operation
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JournalSerializer
    @Inject
    constructor() {
        private val json = Json { ignoreUnknownKeys = true }

        fun encode(operations: List<Operation>): ByteArray =
            operations
                .joinToString(separator = "\n") { encodeLine(it) }
                .toByteArray(Charsets.UTF_8)

        fun decode(bytes: ByteArray): List<Operation> =
            bytes
                .toString(Charsets.UTF_8)
                .lineSequence()
                .filter { it.isNotBlank() }
                .map { decodeLine(it) }
                .toList()

        private fun encodeLine(operation: Operation): String {
            val line =
                OperationLine(
                    opId = operation.opId,
                    deviceId = operation.deviceId,
                    entityKind = operation.entityKind.name,
                    entityUuid = operation.entityUuid,
                    opType = operation.opType.name,
                    updatedAt = operation.updatedAt.toEpochMilli(),
                    payload = operation.payload?.let { json.parseToJsonElement(it) } ?: JsonNull,
                )
            return json.encodeToString(OperationLine.serializer(), line)
        }

        private fun decodeLine(line: String): Operation {
            val parsed = json.decodeFromString(OperationLine.serializer(), line)
            return Operation(
                opId = parsed.opId,
                deviceId = parsed.deviceId,
                entityKind = EntityKind.valueOf(parsed.entityKind),
                entityUuid = parsed.entityUuid,
                opType = OpType.valueOf(parsed.opType),
                payload = parsed.payload.asPayloadString(json),
                updatedAt = Instant.ofEpochMilli(parsed.updatedAt),
            )
        }

        private fun JsonElement.asPayloadString(json: Json): String? =
            when (this) {
                is JsonNull -> null
                is JsonPrimitive -> if (isString) content else json.encodeToString(JsonElement.serializer(), this)
                else -> json.encodeToString(JsonElement.serializer(), this)
            }

        @Serializable
        private data class OperationLine(
            val opId: String,
            val deviceId: String,
            val entityKind: String,
            val entityUuid: String,
            val opType: String,
            val updatedAt: Long,
            val payload: JsonElement,
        )
    }
