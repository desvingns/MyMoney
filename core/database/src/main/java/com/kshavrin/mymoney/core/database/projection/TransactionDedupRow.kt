package com.kshavrin.mymoney.core.database.projection

import androidx.room.ColumnInfo

/**
 * Raw components of a transaction's import dedup key (D3), read straight from the DB so the
 * repository can build a [com.kshavrin.mymoney.core.domain.csv.TransactionDedupKey] set without
 * loading whole entities. Account/category names are joined in; a transfer has no category, hence
 * the nullable [categoryName].
 */
data class TransactionDedupRow(
    @ColumnInfo(name = "accountName") val accountName: String,
    @ColumnInfo(name = "categoryName") val categoryName: String?,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "amount") val amount: Double,
    @ColumnInfo(name = "occurredAt") val occurredAt: Long,
    @ColumnInfo(name = "note") val note: String?,
)
