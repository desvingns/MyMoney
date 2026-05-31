package com.kshavrin.mymoney.feature.transaction.categorygrid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kshavrin.mymoney.core.designsystem.icon.categoryIcon
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.transaction.R

const val CATEGORY_GRID_ADD_CELL_TAG = "category_picker_add_cell"

private const val ADD_CELL_KEY = "category_picker_add_cell"

@Composable
fun CategoryGrid(
    categories: List<Category>,
    onCategoryClick: (Long) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(categories, key = { it.id }) { cat ->
            CategoryCell(
                category = cat,
                onClick = { onCategoryClick(cat.id) },
            )
        }
        item(key = ADD_CELL_KEY) {
            AddCategoryCell(onClick = onAddClick)
        }
    }
}

@Composable
private fun CategoryCell(
    category: Category,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = parseHexColor(category.colorHex)
    GridCard(
        contentDescription = category.name,
        icon = categoryIcon(category.iconKey),
        iconTint = tint,
        label = category.name,
        labelColor = tint,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun AddCategoryCell(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    GridCard(
        contentDescription = stringResource(R.string.add_category_cta),
        icon = Icons.Outlined.AddCircleOutline,
        iconTint = neutral,
        label = stringResource(R.string.add_category_cta),
        labelColor = neutral,
        onClick = onClick,
        modifier = modifier.testTag(CATEGORY_GRID_ADD_CELL_TAG),
    )
}

@Composable
private fun GridCard(
    contentDescription: String,
    icon: ImageVector,
    iconTint: Color,
    label: String,
    labelColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(MaterialTheme.colorScheme.outlineVariant),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.m, horizontal = Spacing.s),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = label,
                color = labelColor,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

internal fun parseHexColor(hex: String): Color = try {
    val cleaned = hex.removePrefix("#")
    val argb = if (cleaned.length == 6) "FF$cleaned" else cleaned
    Color(argb.toLong(16))
} catch (_: Exception) {
    Color.Gray
}
