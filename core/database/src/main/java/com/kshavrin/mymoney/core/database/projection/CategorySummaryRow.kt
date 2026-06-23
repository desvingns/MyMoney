package com.kshavrin.mymoney.core.database.projection

import androidx.room.ColumnInfo

data class CategorySummaryRow(
    @ColumnInfo(name = "categoryId") val categoryId: Long,
    @ColumnInfo(name = "categoryName") val categoryName: String,
    @ColumnInfo(name = "colorHex") val colorHex: String,
    @ColumnInfo(name = "textColorHex") val textColorHex: String,
    @ColumnInfo(name = "iconKey") val iconKey: String,
    @ColumnInfo(name = "total") val total: Double,
)
