package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.dashboardAuroraAccent
import com.kshavrin.mymoney.core.ui.theme.dashboardAuroraBalanceLabel
import com.kshavrin.mymoney.core.ui.theme.dashboardAuroraCard
import com.kshavrin.mymoney.core.ui.theme.dashboardAuroraPill
import com.kshavrin.mymoney.core.ui.theme.dashboardExpensePill
import com.kshavrin.mymoney.core.ui.theme.dashboardIncomePill

@Composable
internal fun AuroraCardSurface(
    cardTestTag: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.dashboardAuroraAccent,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.dashboardAuroraCard
    val gradientTop = accent.copy(alpha = 0.20f)
    val gradientBottom = Color.White.copy(alpha = 0.02f)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.dashboardAuroraHostHorizontalPaddingWide)
                .testTag(cardTestTag)
                .shadow(
                    elevation = Spacing.s,
                    shape = shape,
                    ambientColor = accent,
                    spotColor = accent,
                ).clip(shape)
                .drawBehind {
                    drawRect(
                        brush =
                            Brush.radialGradient(
                                colorStops =
                                    arrayOf(
                                        0f to gradientTop,
                                        0.70f to gradientBottom,
                                    ),
                                center = Offset(x = size.width / 2f, y = 0f),
                                radius = size.height * 1.4f,
                            ),
                    )
                }.border(
                    width = Spacing.dashboardBalancePanelBorderWidth,
                    color = accent.copy(alpha = 0.28f),
                    shape = shape,
                ).padding(
                    start = Spacing.dashboardAuroraCardPaddingHorizontal,
                    end = Spacing.dashboardAuroraCardPaddingHorizontal,
                    top = Spacing.dashboardAuroraCardPaddingTopCompact,
                    bottom = Spacing.dashboardAuroraCardPaddingBottomCompact,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
internal fun IncomeExpensePills(
    income: String,
    expense: String,
    modifier: Modifier = Modifier,
    incomePillTestTag: String? = null,
    expensePillTestTag: String? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.dashboardAuroraPillGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AuroraStatPill(
            text = "↑ $income",
            color = MaterialTheme.colorScheme.dashboardIncomePill,
            modifier = incomePillTestTag?.let { Modifier.testTag(it) } ?: Modifier,
        )
        AuroraStatPill(
            text = "↓ $expense",
            color = MaterialTheme.colorScheme.dashboardExpensePill,
            modifier = expensePillTestTag?.let { Modifier.testTag(it) } ?: Modifier,
        )
    }
}

@Composable
private fun AuroraStatPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.dashboardAuroraPill
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(color.copy(alpha = 0.12f), shape)
                .border(
                    width = Spacing.dashboardBalancePanelBorderWidth,
                    color = color.copy(alpha = 0.3f),
                    shape = shape,
                ).padding(
                    horizontal = Spacing.m,
                    vertical = Spacing.dashboardAuroraPillPaddingVertical,
                ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.dashboardAuroraBalanceLabel,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
