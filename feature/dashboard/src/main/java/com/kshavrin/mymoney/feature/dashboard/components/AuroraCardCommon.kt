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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.dashboardAuroraAccent
import com.kshavrin.mymoney.core.ui.theme.dashboardAuroraBalanceLabel
import com.kshavrin.mymoney.core.ui.theme.dashboardAuroraPill
import com.kshavrin.mymoney.core.ui.theme.dashboardExpensePill
import com.kshavrin.mymoney.core.ui.theme.dashboardIncomePill
import com.kshavrin.mymoney.feature.dashboard.R

@Composable
internal fun AuroraCardSurface(
    cardTestTag: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.dashboardAuroraAccent,
    content: @Composable ColumnScope.() -> Unit,
) {
    // "Без подложки" (reference Dashboard.dc.html, variant "1 · Без подложки"): the framed
    // substrate — the 1dp accent border + outer accent glow — is dropped so the content floats
    // on the dashboard background. Only a soft top radial glow remains, mirroring isV1's
    // radial-gradient(70% 62% at 50% 12%, accent@0.15, accent@0 72%) with no box-shadow.
    // The host horizontal padding stays outside the test tag so the card still spans the host
    // width with the same side insets.
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.dashboardAuroraHostHorizontalPaddingWide)
                .testTag(cardTestTag)
                .drawBehind {
                    drawRect(
                        brush =
                            Brush.radialGradient(
                                colorStops =
                                    arrayOf(
                                        0f to accent.copy(alpha = 0.15f),
                                        0.72f to accent.copy(alpha = 0f),
                                    ),
                                center = Offset(size.width / 2f, size.height * 0.12f),
                                radius = size.width * 0.7f,
                            ),
                    )
                }.padding(
                    start = Spacing.dashboardAuroraPlainPaddingHorizontal,
                    end = Spacing.dashboardAuroraPlainPaddingHorizontal,
                    top = Spacing.dashboardAuroraPlainPaddingTop,
                    bottom = Spacing.dashboardAuroraPlainPaddingBottom,
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
            text = "↑ " + stringResource(R.string.dashboard_aurora_income_label) + " " + income,
            color = MaterialTheme.colorScheme.dashboardIncomePill,
            modifier = incomePillTestTag?.let { Modifier.testTag(it) } ?: Modifier,
        )
        AuroraStatPill(
            text = "↓ " + stringResource(R.string.dashboard_aurora_expense_label) + " " + expense,
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
            style = MaterialTheme.typography.dashboardAuroraBalanceLabel.copy(fontSize = 12.sp),
            color = lerp(color, Color.White, 0.2f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
