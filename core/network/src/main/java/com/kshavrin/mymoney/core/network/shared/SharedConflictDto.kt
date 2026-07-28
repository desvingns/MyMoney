package com.kshavrin.mymoney.core.network.shared

import com.kshavrin.mymoney.core.domain.sync.ConflictStatus
import com.kshavrin.mymoney.core.domain.sync.SharedConflict
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class SharedConflictDto(
    @SerialName("id") val id: String,
    @SerialName("workspace_id") val workspaceId: String,
    @SerialName("entity_kind") val entityKind: String,
    @SerialName("entity_id") val entityId: String,
    @SerialName("operation_a") val operationA: SharedOperationDto,
    @SerialName("operation_b") val operationB: SharedOperationDto,
    @SerialName("author_a_id") val authorAId: String,
    @SerialName("author_b_id") val authorBId: String,
    @SerialName("status") val status: String,
    @SerialName("resolver_id") val resolverId: String? = null,
    @SerialName("resolved_into_id") val resolvedIntoId: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("resolved_at") val resolvedAt: String? = null,
) {
    fun toDomain(): SharedConflict =
        SharedConflict(
            id = id,
            workspaceId = workspaceId,
            entityKind = entityKind.toEntityKind(),
            entityId = entityId,
            operationA = operationA.toDomain(),
            operationB = operationB.toDomain(),
            authorAId = authorAId,
            authorBId = authorBId,
            status = status.toConflictStatus(),
            resolverId = resolverId,
            resolvedIntoId = resolvedIntoId,
            createdAt = Instant.parse(createdAt),
            resolvedAt = resolvedAt?.let { Instant.parse(it) },
        )
}

private fun String.toConflictStatus(): ConflictStatus =
    when (this) {
        "pending" -> ConflictStatus.Pending
        "resolved" -> ConflictStatus.Resolved
        else -> throw IllegalArgumentException("Unknown conflict_status: $this")
    }
