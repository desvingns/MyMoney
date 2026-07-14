package com.kshavrin.mymoney.core.designsystem.amountinput

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.kshavrin.mymoney.core.designsystem.R
import com.kshavrin.mymoney.core.ui.theme.Spacing

@Composable
fun MonefyAmountInput(
    display: String,
    expression: String,
    currencyCode: String?,
    modifier: Modifier = Modifier,
    currencySymbol: String? = null,
    onClear: (() -> Unit)? = null,
    clearContentDescription: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                CurrencyLabel(symbol = currencySymbol, code = currencyCode)
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    val fontSize: TextUnit = computeDisplayFontSize(display.length)
                    val style: TextStyle =
                        MaterialTheme.typography.headlineLarge.copy(
                            fontSize = fontSize,
                            color = MaterialTheme.colorScheme.onPrimary,
                            textAlign = TextAlign.Center,
                        )
                    BasicText(text = display, style = style)
                }
                if (onClear != null) {
                    val clearDescription =
                        clearContentDescription ?: stringResource(R.string.keypad_backspace_cd)
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = clearDescription,
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
        if (expression.isNotBlank()) {
            Text(
                text = expression,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs, end = Spacing.s),
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun CurrencyLabel(
    symbol: String?,
    code: String?,
) {
    if (symbol == null && code == null) return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (symbol != null) {
            Text(
                text = symbol,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        if (code != null) {
            Text(
                text = code,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

internal fun computeDisplayFontSize(length: Int): TextUnit =
    when {
        length >= 11 -> 24.sp
        length >= 9 -> 28.sp
        length >= 7 -> 32.sp
        else -> 36.sp
    }
