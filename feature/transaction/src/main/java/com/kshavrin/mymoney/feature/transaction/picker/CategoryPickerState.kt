package com.kshavrin.mymoney.feature.transaction.picker

import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind

data class CategoryPickerState(
    val kind: CategoryKind = CategoryKind.Expense,
    val categories: List<Category> = emptyList(),
    val amountPreview: String? = null,
)
