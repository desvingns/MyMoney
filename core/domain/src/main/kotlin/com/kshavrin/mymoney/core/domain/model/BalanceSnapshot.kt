package com.kshavrin.mymoney.core.domain.model

data class BalanceSnapshot(
    val income: Money,
    val expense: Money,
    val net: Money,
    val byCategory: List<CategoryBalance>,
)

data class CategoryBalance(
    val categoryId: Long,
    val categoryName: String,
    val colorHex: String,
    val total: Money,
    val fraction: Float,
    val iconKey: String = "",
    val isExpense: Boolean = true,
)
