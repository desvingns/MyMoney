package com.kshavrin.mymoney.core.designsystem.keypad

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.kshavrin.mymoney.core.designsystem.sound.SoundKey
import com.kshavrin.mymoney.core.designsystem.sound.SoundPlayer
import com.kshavrin.mymoney.core.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun MonefyKeypad(
    onEvent: (KeypadEvent) -> Unit,
    modifier: Modifier = Modifier,
    soundPlayer: SoundPlayer? = null,
) {
    val haptic = LocalHapticFeedback.current

    val handlePress: (KeypadEvent) -> Unit = { event ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        soundPlayer?.play(SoundKey.KEYPAD_TAP)
        onEvent(event)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            KeypadKey(label = "7", onPress = { handlePress(KeypadEvent.Digit(7)) })
            KeypadKey(label = "8", onPress = { handlePress(KeypadEvent.Digit(8)) })
            KeypadKey(label = "9", onPress = { handlePress(KeypadEvent.Digit(9)) })
            KeypadKey(label = "÷", onPress = { handlePress(KeypadEvent.Op(Operator.Divide)) })
            KeypadKey(label = "⌫", onPress = { handlePress(KeypadEvent.Backspace) })
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            KeypadKey(label = "4", onPress = { handlePress(KeypadEvent.Digit(4)) })
            KeypadKey(label = "5", onPress = { handlePress(KeypadEvent.Digit(5)) })
            KeypadKey(label = "6", onPress = { handlePress(KeypadEvent.Digit(6)) })
            KeypadKey(label = "×", onPress = { handlePress(KeypadEvent.Op(Operator.Multiply)) })
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            KeypadKey(label = "1", onPress = { handlePress(KeypadEvent.Digit(1)) })
            KeypadKey(label = "2", onPress = { handlePress(KeypadEvent.Digit(2)) })
            KeypadKey(label = "3", onPress = { handlePress(KeypadEvent.Digit(3)) })
            KeypadKey(label = "−", onPress = { handlePress(KeypadEvent.Op(Operator.Minus)) })
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            KeypadKey(label = "0", onPress = { handlePress(KeypadEvent.Digit(0)) })
            KeypadKey(label = ".", onPress = { handlePress(KeypadEvent.Dot) })
            KeypadKey(label = "=", onPress = { handlePress(KeypadEvent.Equals) })
            KeypadKey(label = "+", onPress = { handlePress(KeypadEvent.Op(Operator.Plus)) })
        }
    }
}

@Composable
private fun RowScope.KeypadKey(
    label: String,
    onPress: () -> Unit,
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Button(
        onClick = {
            scope.launch {
                scale.animateTo(
                    targetValue = 0.92f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
                )
                scale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
                )
            }
            onPress()
        },
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .scale(scale.value),
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(text = label, style = MaterialTheme.typography.titleLarge)
    }
}
