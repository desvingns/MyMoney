package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.textSecondary
import com.kshavrin.mymoney.feature.dashboard.R

@Composable
fun CategoryTilesList(
    expenseTiles: List<CategoryTileItem>,
    onTileClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (expenseTiles.isEmpty()) {
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(Spacing.xl),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.dashboard_category_tiles_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        Column(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            expenseTiles.forEach { tile ->
                CategoryTile(
                    tile = tile,
                    onTileClick = onTileClick,
                )
            }
        }
    }
}
