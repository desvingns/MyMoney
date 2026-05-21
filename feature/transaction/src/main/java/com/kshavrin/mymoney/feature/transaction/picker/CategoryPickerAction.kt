package com.kshavrin.mymoney.feature.transaction.picker

import com.kshavrin.mymoney.core.domain.model.CategoryKind

sealed interface CategoryPickerAction {
    data class ReturnWithId(val id: Long) : CategoryPickerAction
    data class AddCategory(val kind: CategoryKind) : CategoryPickerAction
    data object NavigateBack : CategoryPickerAction
}
