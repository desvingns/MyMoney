package com.kshavrin.mymoney.feature.transaction.expense

import com.kshavrin.mymoney.core.designsystem.keypad.Operator
import java.time.LocalDate

sealed interface AddExpenseEvent {
    data class KeypadDigit(
        val d: Int,
    ) : AddExpenseEvent

    data class KeypadOperator(
        val op: Operator,
    ) : AddExpenseEvent

    data object KeypadDot : AddExpenseEvent

    data object KeypadBackspace : AddExpenseEvent

    data object KeypadEquals : AddExpenseEvent

    data class NoteChanged(
        val text: String,
    ) : AddExpenseEvent

    data class DateChanged(
        val date: LocalDate,
    ) : AddExpenseEvent

    data class AccountChanged(
        val accountId: Long,
    ) : AddExpenseEvent

    data object SelectCategoryClicked : AddExpenseEvent

    data object BackToAmount : AddExpenseEvent

    data object AddCategoryClicked : AddExpenseEvent

    data class CategoryPicked(
        val categoryId: Long,
    ) : AddExpenseEvent

    data object SaveClicked : AddExpenseEvent

    data object SwapMode : AddExpenseEvent

    data object BackClicked : AddExpenseEvent

    data object DismissError : AddExpenseEvent
}
