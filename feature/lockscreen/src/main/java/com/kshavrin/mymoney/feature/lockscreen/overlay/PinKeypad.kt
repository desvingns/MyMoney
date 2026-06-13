package com.kshavrin.mymoney.feature.lockscreen.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.lockscreen.R

const val PIN_LENGTH = 4
private const val PIN_BACKSPACE_TAG = "pin_keypad_backspace"

@Composable
fun PinKeypad(
    entered: String,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        PinDots(filled = entered.length)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                DigitKey(1, onDigit, enabled)
                DigitKey(2, onDigit, enabled)
                DigitKey(3, onDigit, enabled)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                DigitKey(4, onDigit, enabled)
                DigitKey(5, onDigit, enabled)
                DigitKey(6, onDigit, enabled)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                DigitKey(7, onDigit, enabled)
                DigitKey(8, onDigit, enabled)
                DigitKey(9, onDigit, enabled)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                KeySpacer()
                DigitKey(0, onDigit, enabled)
                BackspaceKey(onBackspace, enabled)
            }
        }
    }
}

@Composable
private fun PinDots(filled: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        repeat(PIN_LENGTH) { index ->
            Surface(
                modifier = Modifier.size(Spacing.m),
                shape = CircleShape,
                color = if (index < filled) MaterialTheme.colorScheme.primary else Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                content = {},
            )
        }
    }
}

@Composable
private fun RowScope.DigitKey(
    digit: Int,
    onDigit: (Int) -> Unit,
    enabled: Boolean,
) {
    TextButton(
        onClick = { onDigit(digit) },
        enabled = enabled,
        modifier =
            Modifier
                .weight(1f)
                .aspectRatio(1.4f),
    ) {
        Text(text = digit.toString(), style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun RowScope.BackspaceKey(
    onBackspace: () -> Unit,
    enabled: Boolean,
) {
    TextButton(
        onClick = onBackspace,
        enabled = enabled,
        modifier =
            Modifier
                .weight(1f)
                .aspectRatio(1.4f)
                .testTag(PIN_BACKSPACE_TAG),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Backspace,
            contentDescription = stringResource(R.string.lock_pin_backspace),
        )
    }
}

@Composable
private fun RowScope.KeySpacer() {
    Box(
        modifier =
            Modifier
                .weight(1f)
                .aspectRatio(1.4f),
    )
}
