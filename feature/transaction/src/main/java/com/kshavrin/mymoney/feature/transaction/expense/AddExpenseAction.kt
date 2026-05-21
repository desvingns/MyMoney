package com.kshavrin.mymoney.feature.transaction.expense

import com.kshavrin.mymoney.core.designsystem.sound.SoundKey
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.feature.transaction.HapticKind

sealed interface AddExpenseAction {
    data object NavigateBack : AddExpenseAction
    data class NavigateToCategoryPicker(val kind: TransactionKind) : AddExpenseAction
    data object NavigateToIncomeForm : AddExpenseAction
    data class FireHaptic(val kind: HapticKind) : AddExpenseAction
    data class PlaySound(val key: SoundKey) : AddExpenseAction
    data object ShowSavedConfetti : AddExpenseAction
}
