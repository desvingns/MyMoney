package com.kshavrin.mymoney.core.designsystem.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kshavrin.mymoney.core.designsystem.R
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.wizardColorPickerSelectedBorder

/**
 * Reusable swatch grid for picking a category/account color by hex string. Shared from
 * `:core:designsystem` so feature modules (e.g. the import-migration wizard in `:feature:settings`)
 * can reuse it without depending on each other.
 */
@Composable
fun ColorPickerGrid(
    selectedHex: String,
    onColorSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    palette: List<String> = CATEGORY_COLOR_PALETTE,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = Spacing.wizardColorSwatchSize + Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
        // Bounded max height: this grid is hosted inside a Column(verticalScroll), where an unbounded
        // LazyVerticalGrid is measured with infinite height and crashes.
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp),
    ) {
        items(palette) { hex ->
            val color = parseHexColor(hex)
            val selected = hex.equals(selectedHex, ignoreCase = true)
            val colorDescription = stringResource(R.string.color_picker_option_cd, hex)
            Box(
                modifier =
                    Modifier
                        .size(Spacing.wizardColorSwatchSize)
                        .semantics {
                            contentDescription = colorDescription
                            this.selected = selected
                        }.clip(CircleShape)
                        .background(color)
                        .then(
                            if (selected) {
                                Modifier.border(
                                    width = 3.dp,
                                    color = MaterialTheme.colorScheme.wizardColorPickerSelectedBorder,
                                    shape = CircleShape,
                                )
                            } else {
                                Modifier
                            },
                        ).clickable { onColorSelected(hex) }
                        .minimumInteractiveComponentSize(),
            )
        }
    }
}

internal fun parseHexColor(hex: String): Color =
    try {
        val cleaned = hex.removePrefix("#")
        val argb = if (cleaned.length == 6) "FF$cleaned" else cleaned
        Color(argb.toLong(16))
    } catch (_: Exception) {
        Color.Gray
    }
