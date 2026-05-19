package com.kshavrin.mymoney.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_log",
    indices = [Index("performed_at"), Index("target")],
)
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "target") val target: String,
    @ColumnInfo(name = "event") val event: String,
    @ColumnInfo(name = "entity_kind") val entityKind: String?,
    @ColumnInfo(name = "entity_id") val entityId: Long?,
    @ColumnInfo(name = "performed_at") val performedAt: Long,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "payload_hash") val payloadHash: String?,
    @ColumnInfo(name = "error_message") val errorMessage: String?,
)
