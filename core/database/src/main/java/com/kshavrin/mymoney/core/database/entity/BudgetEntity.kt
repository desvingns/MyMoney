package com.kshavrin.mymoney.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "budget",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CurrencyEntity::class,
            parentColumns = ["id"],
            childColumns = ["currency_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("category_id"), Index("currency_id")],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "category_id") val categoryId: Long?,
    @ColumnInfo(name = "period_kind") val periodKind: String,
    @ColumnInfo(name = "period_start") val periodStart: Long,
    @ColumnInfo(name = "amount") val amount: Double,
    @ColumnInfo(name = "currency_id") val currencyId: Long,
    @ColumnInfo(name = "alert_threshold_pct") val alertThresholdPct: Int,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
)
