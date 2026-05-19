package com.kshavrin.mymoney.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recurring_template",
    foreignKeys = [
        ForeignKey(
            entity = CurrencyEntity::class,
            parentColumns = ["id"],
            childColumns = ["currency_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["to_account_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("next_run_at"), Index("is_active"), Index("account_id")],
)
data class RecurringTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "base_kind") val baseKind: String,
    @ColumnInfo(name = "amount") val amount: Double,
    @ColumnInfo(name = "currency_id") val currencyId: Long,
    @ColumnInfo(name = "account_id") val accountId: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long?,
    @ColumnInfo(name = "to_account_id") val toAccountId: Long?,
    @ColumnInfo(name = "note") val note: String?,
    @ColumnInfo(name = "recurrence_kind") val recurrenceKind: String,
    @ColumnInfo(name = "interval") val interval: Int,
    @ColumnInfo(name = "by_day") val byDay: String?,
    @ColumnInfo(name = "starts_at") val startsAt: Long,
    @ColumnInfo(name = "ends_at") val endsAt: Long?,
    @ColumnInfo(name = "next_run_at") val nextRunAt: Long,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
)
