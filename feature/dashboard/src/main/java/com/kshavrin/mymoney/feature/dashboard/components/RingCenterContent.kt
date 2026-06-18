package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
                        badgeHorizontalPadding = Spacing.m.toPx(),
                        badgeVerticalPadding = Spacing.xs.toPx(),
                        badgeLineGap = Spacing.xxs.toPx(),
                        measure = { text, style ->
                            val result = textMeasurer.measure(text = text, style = style, maxLines = 1, softWrap = false)
                            MeasuredText(width = result.size.width.toFloat(), height = result.size.height.toFloat())
                        },
                    )
                }
            }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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
                        .widthIn(max = contentMaxWidth),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(
                    modifier =
                        Modifier.padding(
                            horizontal = Spacing.m * layout.badgeScale,
                            vertical = Spacing.xs * layout.badgeScale,
                        ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs * layout.badgeScale),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = incomeText,
                        style = badgeStyle.scaled(layout.badgeScale),
                        color = MaterialTheme.colorScheme.incomeAccent,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = expenseText,
                        style = badgeStyle.scaled(layout.badgeScale),
                        color = MaterialTheme.colorScheme.expenseAccent,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
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

private data class RingCenterLayout(
    val balanceScale: Float,
    val badgeScale: Float,
)

private data class MeasuredText(
    val width: Float,
    val height: Float,
)

private fun calculateRingCenterLayout(
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
        val badgeWidth = maxOf(incomeSize.width, expenseSize.width) + badgeHorizontalPadding * badgeScale * 2
        val badgeHeight =
            incomeSize.height +
                expenseSize.height +
                badgeLineGap * badgeScale +
                badgeVerticalPadding * badgeScale * 2
        val contentWidth = maxOf(labelSize.width, balanceSize.width, badgeWidth)
        val contentHeight = labelSize.height + balanceSize.height + topGap + badgeHeight
        return contentWidth <= availableWidth && contentHeight <= availableHeight
    }

    var lower = 0f
    var upper = 1f
    repeat(FIT_SEARCH_ITERATIONS) {
        val candidate = (lower + upper) / 2
        if (fits(candidate)) lower = candidate else upper = candidate
    }
    return RingCenterLayout(
        balanceScale = maxOf(minimumBalanceScale, lower),
        badgeScale = maxOf(minimumBadgeScale, lower),
    )
}

private fun TextStyle.scaled(scale: Float): TextStyle =
    copy(
        fontSize = fontSize * scale,
        lineHeight = lineHeight * scale,
    )

private const val FIT_SEARCH_ITERATIONS = 12
