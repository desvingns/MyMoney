package com.kshavrin.mymoney.feature.transaction.income

import com.kshavrin.mymoney.core.designsystem.keypad.Operator
import java.time.LocalDate

sealed interface AddIncomeEvent {
    data class KeypadDigit(val d: Int) : AddIncomeEvent
    data class KeypadOperator(val op: Operator) : AddIncomeEvent
    data object KeypadDot : AddIncomeEvent
    data object KeypadBackspace : AddIncomeEvent
    data object KeypadEquals : AddIncomeEvent
    data class NoteChanged(val text: String) : AddIncomeEvent
    data class DateChanged(val date: LocalDate) : AddIncomeEvent
    data class AccountChanged(val accountId: Long) : AddIncomeEvent
    data object ChooseCategoryClicked : AddIncomeEvent
    data class CategoryPicked(val categoryId: Long) : AddIncomeEvent
    data object SaveClicked : AddIncomeEvent
    data object SwapMode : AddIncomeEvent
    data object BackClicked : AddIncomeEvent
    data object DismissError : AddIncomeEvent
}
