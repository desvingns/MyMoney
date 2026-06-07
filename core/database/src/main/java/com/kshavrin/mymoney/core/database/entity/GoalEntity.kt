package com.kshavrin.mymoney.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "goal")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "icon_key") val iconKey: String,
    @ColumnInfo(name = "color_hex") val colorHex: String,
    // Plain Long, NO @ForeignKey: a goal is a projection that merely references an account
    // for its read-only balance. Archiving/removing that account must not cascade-delete or
    // block goals — the UI shows 0 / prompts re-select when the account is gone.
    @ColumnInfo(name = "account_id") val accountId: Long,
    @ColumnInfo(name = "variant") val variant: String,
    @ColumnInfo(name = "target_amount") val targetAmount: Double,
    @ColumnInfo(name = "starting_capital") val startingCapital: Double,
    @ColumnInfo(name = "monthly_contribution") val monthlyContribution: Double,
    @ColumnInfo(name = "annual_rate_percent") val annualRatePercent: Double?,
    @ColumnInfo(name = "term_date") val termDate: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    // Plain TEXT (JSON or null). null = disabled/empty breakdown — keeps MIGRATION_2_3 a pure
    // ADD COLUMN with no backfill. (De)serialized at the mapper boundary, so Room sees only TEXT.
    @ColumnInfo(name = "contribution_breakdown") val contributionBreakdown: String? = null,
)
