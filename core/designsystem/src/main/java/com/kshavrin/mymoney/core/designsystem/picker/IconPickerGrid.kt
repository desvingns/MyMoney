package com.kshavrin.mymoney.core.designsystem.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kshavrin.mymoney.core.designsystem.icon.NeonCategoryIcon
import com.kshavrin.mymoney.core.designsystem.icon.NeonCategoryIconDefaults
import com.kshavrin.mymoney.core.designsystem.icon.NeonIconTile
import com.kshavrin.mymoney.core.designsystem.icon.categoryIconAccent
import com.kshavrin.mymoney.core.designsystem.icon.categoryNeonIconResOrNull
import com.kshavrin.mymoney.core.ui.theme.Spacing

@Composable
fun IconPickerGrid(
    iconKeys: List<String>,
    selectedIconKey: String,
    iconFor: (String) -> ImageVector,
    onIconSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    iconContentDescription: (String) -> String? = { null },
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = Spacing.wizardIconPickerItemSize + Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp),
    ) {
        items(iconKeys) { key ->
            val selected = key == selectedIconKey
            val iconDescription = iconContentDescription(key)
            val categoryAsset = categoryNeonIconResOrNull(key)
            Box(
                modifier =
                    Modifier
                        .testTag(key)
                        .semantics {
                            iconDescription?.let { contentDescription = it }
                            this.selected = selected
                        }.clickable { onIconSelected(key) },
                contentAlignment = Alignment.Center,
            ) {
                if (categoryAsset != null) {
                    NeonCategoryIcon(
                        iconKey = key,
                        selected = selected,
                        containerSize = Spacing.wizardIconPickerItemSize,
                        iconSize = NeonCategoryIconDefaults.WizardIconSize,
                    )
                } else {
                    NeonIconTile(
                        imageVector = iconFor(key),
                        accent = categoryIconAccent(key),
                        selected = selected,
                        containerSize = Spacing.wizardIconPickerItemSize,
                        iconSize = NeonCategoryIconDefaults.WizardIconSize,
                    )
                }
            }
        }
    }
}
