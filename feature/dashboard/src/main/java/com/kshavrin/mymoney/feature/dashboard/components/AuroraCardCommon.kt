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
import androidx.compose.foundation.layout.widthIn
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

// Shared "Aurora" building blocks (SecAurora — 03_balance-variants.jsx). The aurora gradient
// container and the income/expense pill row are reused by both the full-width hero card
// (AuroraBalanceCard) and the compact per-currency cards (CurrencyBalanceCardList) so the
// gradient / border / glow / pill styling lives in exactly one place.

// Aurora container surface: a centered Column with the SecAurora look —
//   radial-gradient(120% 90% at 50% 0%, accent@0.20, white@0.02 70%),
//   inset 1dp border accent@0.28, soft neon glow, 24dp corners, and the
//   SecAurora padding (18dp top/horizontal, 14dp bottom). Callers supply the
//   inner content (label, value, pills, chart) plus the card's testTag.
@Composable
internal fun AuroraCardSurface(
    cardTestTag: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val accent = MaterialTheme.colorScheme.dashboardAuroraAccent
    val shape = MaterialTheme.shapes.dashboardAuroraCard
    // SecAurora: radial-gradient(120% 90% at 50% 0%, accent@0.20, white@0.02 70%). Anchored at the
    // top-center; the gradient brush is sized to the card by drawBehind so it scales with width.
    val gradientTop = accent.copy(alpha = 0.20f)
    val gradientBottom = Color.White.copy(alpha = 0.02f)

    Column(
        modifier =
            modifier
                .widthIn(max = Spacing.dashboardBalancePanelMaxWidth)
                .fillMaxWidth()
                .testTag(cardTestTag)
                // Soft neon glow around the card (SecAurora boxShadow neonGlow(accent)).
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
                    top = Spacing.dashboardAuroraCardPaddingTop,
                    bottom = Spacing.dashboardAuroraCardPaddingBottom,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

// Centered income/expense pill row (SecAurora). Two pills — "↑ <income>" (green neon) and
// "↓ <expense>" (coral-pink neon) — separated by dashboardAuroraPillGap. Callers pass the
// pre-formatted amount strings and optional per-pill testTags.
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

// Income/expense pill (SecAurora StatPill-as-pill): rounded 20dp, text + border in the pill colour,
// translucent fill (@0.12) and a 1dp inset ring (@0.3).
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
