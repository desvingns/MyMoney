package com.kshavrin.mymoney.feature.transaction.picker

sealed interface CategoryPickerEvent {
    data class CategoryClicked(val id: Long) : CategoryPickerEvent
    data object AddCategoryClicked : CategoryPickerEvent
    data object BackClicked : CategoryPickerEvent
}
