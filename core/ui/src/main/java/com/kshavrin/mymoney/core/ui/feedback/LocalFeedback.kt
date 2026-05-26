package com.kshavrin.mymoney.core.ui.feedback

import androidx.compose.runtime.staticCompositionLocalOf
import com.kshavrin.mymoney.core.ui.haptic.HapticKind
import com.kshavrin.mymoney.core.ui.haptic.HapticPlayer
import com.kshavrin.mymoney.core.ui.sound.SoundKey
import com.kshavrin.mymoney.core.ui.sound.SoundPlayer

private object NoopSoundPlayer : SoundPlayer {
    override fun play(key: SoundKey) = Unit
}

private object NoopHapticPlayer : HapticPlayer {
    override fun fire(kind: HapticKind) = Unit
}

val LocalSoundPlayer = staticCompositionLocalOf<SoundPlayer> { NoopSoundPlayer }

val LocalHapticPlayer = staticCompositionLocalOf<HapticPlayer> { NoopHapticPlayer }
