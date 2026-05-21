package com.kshavrin.mymoney.feature.transaction.transfer

import com.kshavrin.mymoney.core.designsystem.keypad.Operator
import java.time.LocalDate

sealed interface TransferEvent {
    data class KeypadDigit(val d: Int) : TransferEvent
    data class KeypadOperator(val op: Operator) : TransferEvent
    data object KeypadDot : TransferEvent
    data object KeypadBackspace : TransferEvent
    data object KeypadEquals : TransferEvent
    data class NoteChanged(val text: String) : TransferEvent
    data class DateChanged(val date: LocalDate) : TransferEvent
    data class SourceAccountChanged(val accountId: Long) : TransferEvent
    data class TargetAccountChanged(val accountId: Long) : TransferEvent
    data object ChangeRateClicked : TransferEvent
    data object SaveClicked : TransferEvent
    data object BackClicked : TransferEvent
    data object DismissError : TransferEvent
}
