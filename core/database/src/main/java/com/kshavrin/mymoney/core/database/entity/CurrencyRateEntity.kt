package com.kshavrin.mymoney.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "currency_rate",
    foreignKeys = [
        ForeignKey(
            entity = CurrencyEntity::class,
            parentColumns = ["id"],
            childColumns = ["from_currency_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CurrencyEntity::class,
            parentColumns = ["id"],
            childColumns = ["to_currency_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["from_currency_id", "to_currency_id"], unique = true)],
)
data class CurrencyRateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "from_currency_id") val fromCurrencyId: Long,
    @ColumnInfo(name = "to_currency_id") val toCurrencyId: Long,
    @ColumnInfo(name = "rate") val rate: Double,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
