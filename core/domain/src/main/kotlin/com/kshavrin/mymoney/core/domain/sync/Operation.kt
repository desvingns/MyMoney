package com.kshavrin.mymoney.core.domain.sync

import java.time.Instant

data class Operation(
    val opId: String,
    val deviceId: String,
    val entityKind: EntityKind,
    val entityUuid: String,
    val opType: OpType,
    val payload: String?,
    val updatedAt: Instant,
)

enum class EntityKind {
    Transaction,
    Category,
    Account,
}

enum class OpType {
    Upsert,
    Delete,
}
