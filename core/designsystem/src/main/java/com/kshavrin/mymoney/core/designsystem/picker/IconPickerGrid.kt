package com.kshavrin.mymoney.core.designsystem.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kshavrin.mymoney.core.ui.theme.Spacing

/**
 * Reusable inline grid for picking an icon by key. Shared from `:core:designsystem` so feature
 * modules can embed it (e.g. as a wizard step) without depending on each other. The caller resolves
 * each key to an [ImageVector] via [iconFor] — typically
 * [com.kshavrin.mymoney.core.designsystem.icon.categoryIcon].
 */
@Composable
fun IconPickerGrid(
    iconKeys: List<String>,
    selectedIconKey: String,
    iconFor: (String) -> ImageVector,
    onIconSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = Spacing.wizardIconPickerItemSize + Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
        // Bounded max height: hosted inside a Column(verticalScroll); an unbounded grid is measured
        // with infinite height and crashes.
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp),
    ) {
        items(iconKeys) { key ->
            val selected = key == selectedIconKey
            Box(
                modifier =
                    Modifier
                        .size(Spacing.wizardIconPickerItemSize)
                        .semantics { contentDescription = key }
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ).clickable { onIconSelected(key) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = iconFor(key),
                    contentDescription = null,
                    tint =
                        if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}
