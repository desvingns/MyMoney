package com.kshavrin.mymoney.core.designsystem.donut

import androidx.compose.ui.graphics.Color

data class CategorySlice(
    val categoryId: Long,
    val color: Color,
    val fraction: Float,
    val label: String,
    val hasBudgetAlert: Boolean = false,
)
