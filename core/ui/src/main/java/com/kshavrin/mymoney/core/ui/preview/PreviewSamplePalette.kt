package com.kshavrin.mymoney.core.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kshavrin.mymoney.core.ui.theme.CategoryColors
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.core.ui.theme.Spacing

@Composable
fun PreviewSamplePalette() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text("Display Large", style = MaterialTheme.typography.displayLarge)
            Text("Headline Medium", style = MaterialTheme.typography.headlineMedium)
            Text("Title Large", style = MaterialTheme.typography.titleLarge)
            Text("Body Large", style = MaterialTheme.typography.bodyLarge)
            Text("Label Small", style = MaterialTheme.typography.labelSmall)

            SwatchRow("primary", MaterialTheme.colorScheme.primary)
            SwatchRow("secondary", MaterialTheme.colorScheme.secondary)
            SwatchRow("tertiary", MaterialTheme.colorScheme.tertiary)
            SwatchRow("error", MaterialTheme.colorScheme.error)
            SwatchRow("surfaceVariant", MaterialTheme.colorScheme.surfaceVariant)

            Text(
                text = "Categories",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = Spacing.m),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                CategoryColors.values.take(8).forEach { swatch ->
                    Swatch(swatch)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                CategoryColors.values.drop(8).forEach { swatch ->
                    Swatch(swatch)
                }
            }
        }
    }
}

@Composable
private fun SwatchRow(
    label: String,
    color: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Swatch(color)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Swatch(color: Color) {
    Surface(
        modifier =
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(Spacing.xs))
                .background(color),
        color = color,
        content = {},
    )
}

@ThemePreviews
@Composable
private fun PreviewSamplePalettePreview() {
    MyMoneyTheme {
        PreviewSamplePalette()
    }
}
