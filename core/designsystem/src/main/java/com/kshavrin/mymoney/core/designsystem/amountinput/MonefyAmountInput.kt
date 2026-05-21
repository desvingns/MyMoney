package com.kshavrin.mymoney.core.designsystem.amountinput

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.kshavrin.mymoney.core.ui.theme.Spacing

@Composable
fun MonefyAmountInput(
    display: String,
    expression: String,
    currencyCode: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (currencyCode != null) {
            Text(
                text = currencyCode,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val fontSize: TextUnit = computeDisplayFontSize(display.length)
            val style: TextStyle = MaterialTheme.typography.headlineLarge.copy(
                fontSize = fontSize,
                color = MaterialTheme.colorScheme.onSurface,
            )
            BasicText(
                text = display,
                style = style,
            )
        }
        Text(
            text = expression,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

internal fun computeDisplayFontSize(length: Int): TextUnit = when {
    length >= 11 -> 24.sp
    length >= 9 -> 28.sp
    length >= 7 -> 32.sp
    else -> 36.sp
}
