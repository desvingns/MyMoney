package com.kshavrin.mymoney.core.designsystem.form

import com.kshavrin.mymoney.core.designsystem.keypad.KeypadEvent
import java.time.LocalDate

data class TransactionFormState(
    val amountInput: String,
    val expression: String,
    val currencyCode: String?,
    val currencySymbol: String?,
    val note: String,
    val occurredAt: LocalDate,
    val categories: List<TransactionFormCategory>,
    val categoryStep: Boolean,
    val chooseCategoryEnabled: Boolean,
)

sealed interface TransactionFormEvent {
    data class Keypad(val event: KeypadEvent) : TransactionFormEvent
    data class NoteChanged(val text: String) : TransactionFormEvent
    data object DateHeaderClicked : TransactionFormEvent
    data object SelectCategoryClicked : TransactionFormEvent
    data object BackToAmount : TransactionFormEvent
    data object AddCategoryClicked : TransactionFormEvent
    data class CategoryPicked(val categoryId: Long) : TransactionFormEvent
}
