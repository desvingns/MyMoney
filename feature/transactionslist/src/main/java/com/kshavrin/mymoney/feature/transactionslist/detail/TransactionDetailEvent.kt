package com.kshavrin.mymoney.feature.transactionslist.detail

import com.kshavrin.mymoney.core.designsystem.keypad.Operator
import java.time.LocalDate

sealed interface TransactionDetailEvent {
    data class KeypadDigit(
        val d: Int,
    ) : TransactionDetailEvent

    data class KeypadOperator(
        val op: Operator,
    ) : TransactionDetailEvent

    data object KeypadDot : TransactionDetailEvent

    data object KeypadBackspace : TransactionDetailEvent

    data object KeypadEquals : TransactionDetailEvent

    data class NoteChanged(
        val text: String,
    ) : TransactionDetailEvent

    data class DateChanged(
        val date: LocalDate,
    ) : TransactionDetailEvent

    data class AccountChanged(
        val accountId: Long,
    ) : TransactionDetailEvent

    data class TargetAccountChanged(
        val accountId: Long,
    ) : TransactionDetailEvent

    data class RateChanged(
        val text: String,
    ) : TransactionDetailEvent

    data object SelectCategoryClicked : TransactionDetailEvent

    data object BackToAmount : TransactionDetailEvent

    data object AddCategoryClicked : TransactionDetailEvent

    data class CategoryPicked(
        val categoryId: Long,
    ) : TransactionDetailEvent

    data object SaveClicked : TransactionDetailEvent

    data object DeleteClicked : TransactionDetailEvent

    data object ConfirmDelete : TransactionDetailEvent

    data object DismissDelete : TransactionDetailEvent

    data class UndoDeleteClicked(
        val id: Long,
    ) : TransactionDetailEvent

    data object BackClicked : TransactionDetailEvent

    data object DismissError : TransactionDetailEvent
}
