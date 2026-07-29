package com.kshavrin.mymoney.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "shared_pending_operation",
    primaryKeys = ["idempotency_key"],
    indices = [Index(value = ["workspace_id", "entity_kind", "entity_id"])],
)
data class SharedPendingOperationEntity(
    @ColumnInfo(name = "idempotency_key") val idempotencyKey: String,
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "base_sequence") val baseSequence: Long,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "entity_kind") val entityKind: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "payload") val payload: String?,
    @ColumnInfo(name = "tombstone") val tombstone: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "shared_entity_state",
    primaryKeys = ["workspace_id", "entity_kind", "entity_id"],
)
data class SharedEntityStateEntity(
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "entity_kind") val entityKind: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "payload") val payload: String?,
    @ColumnInfo(name = "tombstone") val tombstone: Boolean,
)
