package com.kshavrin.mymoney.core.domain.sync

import java.time.Instant

data class SharedOperation(
    val id: String,
    val workspaceId: String,
    val idempotencyKey: String,
    val serverSequence: Long,
    val baseSequence: Long,
    val deviceId: String,
    val entityKind: EntityKind,
    val entityId: String,
    val payload: String?,
    val tombstone: Boolean,
    val createdAt: Instant,
)
