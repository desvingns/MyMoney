package com.kshavrin.mymoney.core.designsystem.form

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kshavrin.mymoney.core.ui.theme.Spacing

@Composable
fun FormBottomBar(
    text: String,
    enabled: Boolean = true,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = Spacing.xs,
        shadowElevation = Spacing.xs,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
        ) {
            Button(
                onClick = onSave,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = text)
            }
        }
    }
}
