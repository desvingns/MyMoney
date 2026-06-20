package com.kshavrin.mymoney.feature.transaction.transfer

import com.kshavrin.mymoney.core.designsystem.sound.SoundKey
import com.kshavrin.mymoney.feature.transaction.HapticKind

sealed interface TransferAction {
    data object NavigateBack : TransferAction

    data class NavigateToRateSetup(
        val fromCurrencyId: Long,
        val toCurrencyId: Long,
    ) : TransferAction

    data object ShowRateDialog : TransferAction

    data class FireHaptic(
        val kind: HapticKind,
    ) : TransferAction

    data class PlaySound(
        val key: SoundKey,
    ) : TransferAction
}
