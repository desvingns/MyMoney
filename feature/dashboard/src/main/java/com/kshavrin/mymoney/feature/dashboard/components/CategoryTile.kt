package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.kshavrin.mymoney.core.common.money.MoneyFormatter
import com.kshavrin.mymoney.core.designsystem.icon.NeonCategoryIcon
import com.kshavrin.mymoney.core.designsystem.icon.NeonCategoryIconDefaults
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.dashboardCategoryTileAmount
import com.kshavrin.mymoney.core.ui.theme.dashboardCategoryTileTitle
import com.kshavrin.mymoney.core.ui.theme.textSecondary
import com.kshavrin.mymoney.core.ui.theme.tileSurface
import com.kshavrin.mymoney.feature.dashboard.parseHexColor

data class CategoryTileItem(
    val categoryId: Long,
    val label: String,
    val amount: Money,
    val fraction: Float,
    val colorHex: String,
    val iconKey: String,
    val textColorHex: String = colorHex,
    val hasBudgetAlert: Boolean = false,
)

@Composable
fun CategoryTile(
    tile: CategoryTileItem,
    onTileClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val categoryColor = remember(tile.colorHex) { parseHexColor(tile.colorHex) }
    val categoryTextColor = remember(tile.textColorHex) { parseHexColor(tile.textColorHex) }
    val formattedAmount =
        remember(tile.amount, locale) {
            MoneyFormatter.format(
                amount = tile.amount.amount,
                currencySymbol = tile.amount.currency.symbol,
                decimalDigits = 0,
                locale = locale,
                symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
            )
        }
    val tileShape = RoundedCornerShape(Spacing.dashboardTileCornerRadius)

    BoxWithConstraints(
        modifier =
            modifier
                .testTag("category_tile_${tile.categoryId}")
                .fillMaxWidth()
                .height(Spacing.dashboardTileHeight)
                .clip(tileShape)
                .background(MaterialTheme.colorScheme.tileSurface)
                .clickable { onTileClick(tile.categoryId) },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NeonCategoryIcon(
                iconKey = tile.iconKey,
                accent = categoryColor,
                modifier =
                    Modifier
                        .size(Spacing.dashboardTileIconChipSize),
                containerSize = Spacing.dashboardTileIconChipSize,
                iconSize = NeonCategoryIconDefaults.DashboardTileIconSize,
                shape = MaterialTheme.shapes.extraLarge,
            )
            Spacer(modifier = Modifier.width(Spacing.l))
            Text(
                text = tile.label,
                style = MaterialTheme.typography.dashboardCategoryTileTitle,
                color = categoryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(Spacing.m))
            Text(
                text = formattedAmount,
                style = MaterialTheme.typography.dashboardCategoryTileAmount,
                color = MaterialTheme.colorScheme.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .testTag("category_tile_progress_${tile.categoryId}")
                    .height(Spacing.dashboardTileProgressBarHeight)
                    .width(maxWidth * tile.fraction.coerceIn(0f, 1f))
                    .background(categoryColor, MaterialTheme.shapes.extraLarge),
        )
    }
}
