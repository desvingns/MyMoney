package com.kshavrin.mymoney.core.domain.sync

import java.time.Instant

enum class ConflictStatus {
    Pending,
    Resolved,
}

data class SharedConflict(
    val id: String,
    val workspaceId: String,
    val entityKind: EntityKind,
    val entityId: String,
    val operationA: SharedOperation,
    val operationB: SharedOperation,
    val authorAId: String,
    val authorBId: String,
    val status: ConflictStatus,
    val resolverId: String?,
    val resolvedIntoId: String?,
    val createdAt: Instant,
    val resolvedAt: Instant?,
)
