package com.kshavrin.mymoney.core.database.projection

import androidx.room.ColumnInfo

data class CategoryGroupRow(
    @ColumnInfo(name = "categoryId") val categoryId: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "iconKey") val iconKey: String,
    @ColumnInfo(name = "colorHex") val colorHex: String,
    @ColumnInfo(name = "textColorHex") val textColorHex: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "total") val total: Double,
    @ColumnInfo(name = "txCount") val txCount: Int,
)
