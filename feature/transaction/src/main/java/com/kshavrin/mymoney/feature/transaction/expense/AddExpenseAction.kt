package com.kshavrin.mymoney.feature.transaction.expense

import com.kshavrin.mymoney.core.designsystem.sound.SoundKey
import com.kshavrin.mymoney.feature.transaction.HapticKind

sealed interface AddExpenseAction {
    data object NavigateBack : AddExpenseAction
    data object NavigateToCreateCategory : AddExpenseAction
    data object NavigateToIncomeForm : AddExpenseAction
    data class FireHaptic(val kind: HapticKind) : AddExpenseAction
    data class PlaySound(val key: SoundKey) : AddExpenseAction
    data object ShowSavedConfetti : AddExpenseAction
}
