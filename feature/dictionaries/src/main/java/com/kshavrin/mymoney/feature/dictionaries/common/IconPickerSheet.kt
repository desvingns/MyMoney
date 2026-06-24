package com.kshavrin.mymoney.feature.dictionaries.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kshavrin.mymoney.core.designsystem.icon.NeonCategoryIcon
import com.kshavrin.mymoney.core.designsystem.icon.NeonCategoryIconDefaults
import com.kshavrin.mymoney.core.designsystem.icon.NeonIconTile
import com.kshavrin.mymoney.core.designsystem.icon.categoryIconAccent
import com.kshavrin.mymoney.core.designsystem.icon.categoryNeonIconResOrNull
import com.kshavrin.mymoney.feature.dictionaries.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPickerSheet(
    iconKeys: List<String>,
    selectedIconKey: String,
    iconFor: (String) -> ImageVector,
    onIconSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF00040F),
        contentColor = Color.White,
        dragHandle = null,
        scrimColor = Color.Transparent,
        shape = RectangleShape,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 42.dp, end = 16.dp, bottom = 28.dp)) {
            val iconOptionDescription = stringResource(R.string.dictionaries_icon_option_cd)
            Text(
                text = stringResource(R.string.dictionaries_choose_icon),
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(start = 8.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(top = 24.dp),
            ) {
                items(iconKeys) { key ->
                    val selected = key == selectedIconKey
                    val categoryAsset = categoryNeonIconResOrNull(key)
                    Box(
                        modifier =
                            Modifier
                                .testTag(key)
                                .semantics {
                                    contentDescription = iconOptionDescription
                                    this.selected = selected
                                }.clickable { onIconSelected(key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (categoryAsset != null) {
                            NeonCategoryIcon(
                                iconKey = key,
                                selected = selected,
                                containerSize = NeonCategoryIconDefaults.PickerContainerSize,
                                iconSize = NeonCategoryIconDefaults.PickerIconSize,
                            )
                        } else {
                            NeonIconTile(
                                imageVector = iconFor(key),
                                accent = categoryIconAccent(key),
                                selected = selected,
                                containerSize = NeonCategoryIconDefaults.PickerContainerSize,
                                iconSize = NeonCategoryIconDefaults.PickerIconSize,
                            )
                        }
                    }
                }
            }
        }
    }
}
