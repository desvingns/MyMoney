package com.kshavrin.mymoney.core.designsystem.keypad

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kshavrin.mymoney.core.designsystem.R
import com.kshavrin.mymoney.core.ui.feedback.LocalHapticPlayer
import com.kshavrin.mymoney.core.ui.feedback.LocalSoundPlayer
import com.kshavrin.mymoney.core.ui.haptic.HapticKind
import com.kshavrin.mymoney.core.ui.theme.Spacing
import kotlinx.coroutines.launch
import com.kshavrin.mymoney.core.ui.sound.SoundKey as UiSoundKey

@Composable
fun MonefyKeypad(
    onEvent: (KeypadEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiSoundPlayer = LocalSoundPlayer.current
    val hapticPlayer = LocalHapticPlayer.current

    val handlePress: (KeypadEvent) -> Unit = { event ->
        hapticPlayer.fire(HapticKind.SOFT)
        uiSoundPlayer.play(UiSoundKey.KEYPAD_TAP)
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
            KeypadKey(label = "1", onPress = { handlePress(KeypadEvent.Digit(1)) })
            KeypadKey(label = "2", onPress = { handlePress(KeypadEvent.Digit(2)) })
            KeypadKey(label = "3", onPress = { handlePress(KeypadEvent.Digit(3)) })
            KeypadKey(
                label = "+",
                onPress = { handlePress(KeypadEvent.Op(Operator.Plus)) },
                operator = true,
                contentDescription = stringResource(R.string.keypad_op_plus_cd),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            KeypadKey(label = "4", onPress = { handlePress(KeypadEvent.Digit(4)) })
            KeypadKey(label = "5", onPress = { handlePress(KeypadEvent.Digit(5)) })
            KeypadKey(label = "6", onPress = { handlePress(KeypadEvent.Digit(6)) })
            KeypadKey(
                label = "−",
                onPress = { handlePress(KeypadEvent.Op(Operator.Minus)) },
                operator = true,
                contentDescription = stringResource(R.string.keypad_op_minus_cd),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            KeypadKey(label = "7", onPress = { handlePress(KeypadEvent.Digit(7)) })
            KeypadKey(label = "8", onPress = { handlePress(KeypadEvent.Digit(8)) })
            KeypadKey(label = "9", onPress = { handlePress(KeypadEvent.Digit(9)) })
            KeypadKey(
                label = "×",
                onPress = { handlePress(KeypadEvent.Op(Operator.Multiply)) },
                operator = true,
                contentDescription = stringResource(R.string.keypad_op_multiply_cd),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            KeypadKey(label = ".", onPress = { handlePress(KeypadEvent.Dot) })
            KeypadKey(label = "0", onPress = { handlePress(KeypadEvent.Digit(0)) })
            KeypadKey(
                label = "=",
                onPress = { handlePress(KeypadEvent.Equals) },
                operator = true,
                contentDescription = stringResource(R.string.keypad_op_equals_cd),
            )
            KeypadKey(
                label = "÷",
                onPress = { handlePress(KeypadEvent.Op(Operator.Divide)) },
                operator = true,
                contentDescription = stringResource(R.string.keypad_op_divide_cd),
            )
        }
    }
}

@Composable
private fun RowScope.KeypadKey(
    label: String,
    onPress: () -> Unit,
    operator: Boolean = false,
    contentDescription: String? = null,
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val container: Color =
        if (operator) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        }
    val content: Color =
        if (operator) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    OutlinedButton(
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
        modifier =
            Modifier
                .weight(1f)
                .aspectRatio(1f)
                .scale(scale.value)
                .then(
                    if (contentDescription != null) {
                        Modifier.semantics { this.contentDescription = contentDescription }
                    } else {
                        Modifier
                    },
                ),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = container,
                contentColor = content,
            ),
    ) {
        Text(text = label, style = MaterialTheme.typography.titleLarge)
    }
}
