package com.kshavrin.mymoney.feature.transaction.income

import com.kshavrin.mymoney.core.designsystem.sound.SoundKey
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.feature.transaction.HapticKind

sealed interface AddIncomeAction {
    data object NavigateBack : AddIncomeAction
    data class NavigateToCategoryPicker(val kind: TransactionKind) : AddIncomeAction
    data object NavigateToExpenseForm : AddIncomeAction
    data class FireHaptic(val kind: HapticKind) : AddIncomeAction
    data class PlaySound(val key: SoundKey) : AddIncomeAction
    data object ShowSavedConfetti : AddIncomeAction
}
