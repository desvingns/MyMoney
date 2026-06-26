package com.kshavrin.mymoney.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "op_journal",
    indices = [
        Index("entity_uuid"),
        Index("synced_to_remote"),
    ],
)
data class OperationEntity(
    @PrimaryKey @ColumnInfo(name = "op_id") val opId: String,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "entity_kind") val entityKind: String,
    @ColumnInfo(name = "entity_uuid") val entityUuid: String,
    @ColumnInfo(name = "op_type") val opType: String,
    @ColumnInfo(name = "payload") val payload: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "synced_to_remote") val syncedToRemote: Boolean = false,
    @ColumnInfo(name = "applied_from_remote") val appliedFromRemote: Boolean = false,
)
