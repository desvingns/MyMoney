package com.kshavrin.mymoney.core.domain.model

import java.time.Instant

data class SyncLogEntry(
    val id: Long,
    val target: String,
    val event: String,
    val entityKind: String?,
    val entityId: Long?,
    val performedAt: Instant,
    val status: String,
    val payloadHash: String?,
    val errorMessage: String?,
)
