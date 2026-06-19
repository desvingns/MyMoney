package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.kshavrin.mymoney.core.common.money.MoneyFormatter
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.dashboardIncomeExpenseBadge
import com.kshavrin.mymoney.core.ui.theme.dashboardRingBalanceLabel
import com.kshavrin.mymoney.core.ui.theme.dashboardRingBalanceValue
import com.kshavrin.mymoney.core.ui.theme.dashboardRingCenterBadgeDivider
import com.kshavrin.mymoney.core.ui.theme.expenseAccent
import com.kshavrin.mymoney.core.ui.theme.incomeAccent
import com.kshavrin.mymoney.core.ui.theme.textPrimary
import com.kshavrin.mymoney.feature.dashboard.R

@Composable
fun RingCenterContent(
    periodNet: Money,
    income: Money,
    expense: Money,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val balanceLabel = stringResource(R.string.dashboard_ring_balance)
    val periodNetText = formatRingAmount(periodNet, locale)
    val incomeText = stringResource(R.string.dashboard_ring_income, formatRingAmount(income, locale))
    val expenseText = stringResource(R.string.dashboard_ring_expense, formatRingAmount(expense, locale))

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val contentMaxWidth = maxWidth
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val labelStyle = MaterialTheme.typography.dashboardRingBalanceLabel
        val balanceStyle = MaterialTheme.typography.dashboardRingBalanceValue
        val badgeStyle = MaterialTheme.typography.dashboardIncomeExpenseBadge
        val minimumBalanceFontSize = MaterialTheme.typography.labelLarge.fontSize
        val minimumBadgeFontSize = MaterialTheme.typography.labelSmall.fontSize
        val layout =
            remember(
                periodNetText,
                incomeText,
                expenseText,
                balanceLabel,
                contentMaxWidth,
                maxHeight,
                labelStyle,
                balanceStyle,
                badgeStyle,
                density,
            ) {
                with(density) {
                    calculateRingCenterLayout(
                        availableWidth = contentMaxWidth.toPx(),
                        availableHeight = maxHeight.toPx(),
                        label = balanceLabel,
                        periodNet = periodNetText,
                        income = incomeText,
                        expense = expenseText,
                        labelStyle = labelStyle,
                        balanceStyle = balanceStyle,
                        badgeStyle = badgeStyle,
                        minimumBalanceFontSize = minimumBalanceFontSize,
                        minimumBadgeFontSize = minimumBadgeFontSize,
                        topGap = Spacing.s.toPx(),
                        badgeHorizontalPadding = Spacing.dashboardRingCenterBadgeHorizontalPadding.toPx(),
                        badgeVerticalPadding = Spacing.dashboardRingCenterBadgeVerticalPadding.toPx(),
                        badgeLineGap = Spacing.none.toPx(),
                        badgeMinHeight = Spacing.dashboardRingCenterBadgeMinHeight.toPx(),
                        badgeMaxHeight = Spacing.dashboardRingCenterBadgeMaxHeight.toPx(),
                        badgeHorizontalInset = Spacing.dashboardRingCenterBadgeHorizontalInset.toPx(),
                        badgeDividerThickness = Spacing.dashboardRingCenterBadgeDividerThickness.toPx(),
                        badgeRowMinHeight = Spacing.dashboardRingCenterBadgeRowMinHeight.toPx(),
                        badgeBottomInset = Spacing.dashboardRingCenterBadgeBottomInset.toPx(),
                        measure = { text, style ->
                            val result = textMeasurer.measure(text = text, style = style, maxLines = 1, softWrap = false)
                            MeasuredText(width = result.size.width.toFloat(), height = result.size.height.toFloat())
                        },
                    )
                }
            }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.weight(1f))
            Text(
                text = balanceLabel,
                style = labelStyle,
                color = MaterialTheme.colorScheme.textPrimary,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            Text(
                text = periodNetText,
                style = balanceStyle.scaled(layout.balanceScale),
                color = MaterialTheme.colorScheme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                softWrap = false,
                textAlign = TextAlign.Center,
            )
            Surface(
                modifier =
                    Modifier
                        .padding(top = Spacing.s)
                        .padding(horizontal = Spacing.dashboardRingCenterBadgeHorizontalInset)
                        .testTag(RING_CENTER_BADGE_TAG)
                        .fillMaxWidth()
                        .height(with(density) { layout.badgeHeight.toDp() }),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(
                                horizontal = Spacing.dashboardRingCenterBadgeHorizontalPadding * layout.badgeScale,
                                vertical = Spacing.dashboardRingCenterBadgeVerticalPadding * layout.badgeScale,
                            ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BadgeRow(
                        text = incomeText,
                        style = badgeStyle.scaled(layout.badgeScale),
                        color = MaterialTheme.colorScheme.incomeAccent,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(RING_CENTER_BADGE_DIVIDER_TAG)
                                .height(Spacing.dashboardRingCenterBadgeDividerThickness)
                                .background(MaterialTheme.colorScheme.dashboardRingCenterBadgeDivider),
                    )
                    BadgeRow(
                        text = expenseText,
                        style = badgeStyle.scaled(layout.badgeScale),
                        color = MaterialTheme.colorScheme.expenseAccent,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Box(modifier = Modifier.height(Spacing.dashboardRingCenterBadgeBottomInset))
        }
    }
}

@Composable
private fun BadgeRow(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            softWrap = false,
        )
    }
}

private fun formatRingAmount(
    money: Money,
    locale: java.util.Locale,
): String =
    MoneyFormatter.format(
        amount = money.amount,
        currencySymbol = money.currency.symbol,
        decimalDigits = 0,
        locale = locale,
        symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
    )

internal data class RingCenterLayout(
    val balanceScale: Float,
    val badgeScale: Float,
    val badgeHeight: Float,
)

internal data class MeasuredText(
    val width: Float,
    val height: Float,
)

internal fun calculateRingCenterLayout(
    availableWidth: Float,
    availableHeight: Float,
    label: String,
    periodNet: String,
    income: String,
    expense: String,
    labelStyle: TextStyle,
    balanceStyle: TextStyle,
    badgeStyle: TextStyle,
    minimumBalanceFontSize: TextUnit,
    minimumBadgeFontSize: TextUnit,
    topGap: Float,
    badgeHorizontalPadding: Float,
    badgeVerticalPadding: Float,
    badgeLineGap: Float,
    badgeMinHeight: Float = 0f,
    badgeMaxHeight: Float = Float.POSITIVE_INFINITY,
    badgeHorizontalInset: Float = 0f,
    badgeDividerThickness: Float = 0f,
    badgeRowMinHeight: Float = 0f,
    badgeBottomInset: Float = 0f,
    measure: (String, TextStyle) -> MeasuredText,
): RingCenterLayout {
    val minimumBalanceScale = minimumBalanceFontSize.value / balanceStyle.fontSize.value
    val minimumBadgeScale = minimumBadgeFontSize.value / badgeStyle.fontSize.value
    val labelSize = measure(label, labelStyle)

    fun fits(scale: Float): Boolean {
        val balanceScale = maxOf(minimumBalanceScale, scale)
        val badgeScale = maxOf(minimumBadgeScale, scale)
        val balanceSize = measure(periodNet, balanceStyle.scaled(balanceScale))
        val incomeSize = measure(income, badgeStyle.scaled(badgeScale))
        val expenseSize = measure(expense, badgeStyle.scaled(badgeScale))
        val maxBadgeTextWidth = availableWidth - badgeHorizontalInset * 2 - badgeHorizontalPadding * badgeScale * 2
        val badgeWidth = maxOf(incomeSize.width, expenseSize.width) + badgeHorizontalPadding * badgeScale * 2 + badgeHorizontalInset * 2
        val badgeHeight =
            calculateBadgeHeight(
                availableHeight = availableHeight,
                labelHeight = labelSize.height,
                balanceHeight = balanceSize.height,
                incomeHeight = incomeSize.height,
                expenseHeight = expenseSize.height,
                topGap = topGap,
                badgeVerticalPadding = badgeVerticalPadding,
                badgeLineGap = badgeLineGap,
                badgeMinHeight = badgeMinHeight,
                badgeMaxHeight = badgeMaxHeight,
                badgeDividerThickness = badgeDividerThickness,
                badgeRowMinHeight = badgeRowMinHeight,
                badgeBottomInset = badgeBottomInset,
                badgeScale = badgeScale,
            )
        val contentWidth = maxOf(labelSize.width, balanceSize.width, badgeWidth)
        val contentHeight = labelSize.height + balanceSize.height + topGap + badgeHeight + badgeBottomInset
        return contentWidth <= availableWidth &&
            maxOf(incomeSize.width, expenseSize.width) <= maxBadgeTextWidth &&
            contentHeight <= availableHeight
    }

    var lower = 0f
    var upper = 1f
    repeat(FIT_SEARCH_ITERATIONS) {
        val candidate = (lower + upper) / 2
        if (fits(candidate)) lower = candidate else upper = candidate
    }
    val balanceScale = maxOf(minimumBalanceScale, lower)
    val badgeScale = maxOf(minimumBadgeScale, lower)
    val balanceSize = measure(periodNet, balanceStyle.scaled(balanceScale))
    val incomeSize = measure(income, badgeStyle.scaled(badgeScale))
    val expenseSize = measure(expense, badgeStyle.scaled(badgeScale))
    return RingCenterLayout(
        balanceScale = balanceScale,
        badgeScale = badgeScale,
        badgeHeight =
            calculateBadgeHeight(
                availableHeight = availableHeight,
                labelHeight = labelSize.height,
                balanceHeight = balanceSize.height,
                incomeHeight = incomeSize.height,
                expenseHeight = expenseSize.height,
                topGap = topGap,
                badgeVerticalPadding = badgeVerticalPadding,
                badgeLineGap = badgeLineGap,
                badgeMinHeight = badgeMinHeight,
                badgeMaxHeight = badgeMaxHeight,
                badgeDividerThickness = badgeDividerThickness,
                badgeRowMinHeight = badgeRowMinHeight,
                badgeBottomInset = badgeBottomInset,
                badgeScale = badgeScale,
            ),
    )
}

private fun calculateBadgeHeight(
    availableHeight: Float,
    labelHeight: Float,
    balanceHeight: Float,
    incomeHeight: Float,
    expenseHeight: Float,
    topGap: Float,
    badgeVerticalPadding: Float,
    badgeLineGap: Float,
    badgeMinHeight: Float,
    badgeMaxHeight: Float,
    badgeDividerThickness: Float,
    badgeRowMinHeight: Float,
    badgeBottomInset: Float,
    badgeScale: Float,
): Float {
    val rowHeight = maxOf(incomeHeight, expenseHeight, badgeRowMinHeight * badgeScale)
    val naturalHeight = rowHeight * 2 + badgeDividerThickness + badgeLineGap * badgeScale + badgeVerticalPadding * badgeScale * 2
    val availableBadgeHeight = availableHeight - labelHeight - balanceHeight - topGap - badgeBottomInset
    return maxOf(naturalHeight, badgeMinHeight, availableBadgeHeight.coerceAtMost(badgeMaxHeight))
        .coerceAtMost(badgeMaxHeight)
        .coerceAtLeast(0f)
}

private fun TextStyle.scaled(scale: Float): TextStyle =
    copy(
        fontSize = fontSize * scale,
        lineHeight = lineHeight * scale,
    )

private const val FIT_SEARCH_ITERATIONS = 12
private const val RING_CENTER_BADGE_TAG = "ring_center_badge"
private const val RING_CENTER_BADGE_DIVIDER_TAG = "ring_center_badge_divider"
