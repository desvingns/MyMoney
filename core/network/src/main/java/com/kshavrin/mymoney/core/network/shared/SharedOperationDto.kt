package com.kshavrin.mymoney.core.network.shared

import com.kshavrin.mymoney.core.domain.sync.EntityKind
import com.kshavrin.mymoney.core.domain.sync.SharedOperation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.time.Instant

@Serializable
data class SharedOperationDto(
    @SerialName("id") val id: String,
    @SerialName("workspace_id") val workspaceId: String,
    @SerialName("idempotency_key") val idempotencyKey: String,
    @SerialName("server_sequence") val serverSequence: Long,
    @SerialName("base_sequence") val baseSequence: Long,
    @SerialName("author_id") val authorId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("entity_kind") val entityKind: String,
    @SerialName("entity_id") val entityId: String,
    @SerialName("payload") val payload: JsonElement? = null,
    @SerialName("tombstone") val tombstone: Boolean,
    @SerialName("created_at") val createdAt: String,
) {
    fun toDomain(): SharedOperation =
        SharedOperation(
            id = id,
            workspaceId = workspaceId,
            idempotencyKey = idempotencyKey,
            serverSequence = serverSequence,
            baseSequence = baseSequence,
            authorId = authorId,
            deviceId = deviceId,
            entityKind = entityKind.toEntityKind(),
            entityId = entityId,
            payload = payload?.toString(),
            tombstone = tombstone,
            createdAt = Instant.parse(createdAt),
        )
}

internal fun String.toEntityKind(): EntityKind =
    when (this) {
        "transaction" -> EntityKind.Transaction
        "account" -> EntityKind.Account
        "category" -> EntityKind.Category
        else -> throw IllegalArgumentException("Unknown entity_kind: $this")
    }

internal fun EntityKind.toRpcValue(): String =
    when (this) {
        EntityKind.Transaction -> "transaction"
        EntityKind.Account -> "account"
        EntityKind.Category -> "category"
    }
