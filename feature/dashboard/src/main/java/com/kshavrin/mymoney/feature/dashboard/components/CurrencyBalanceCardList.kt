package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.kshavrin.mymoney.core.common.money.MoneyFormatter
import com.kshavrin.mymoney.core.designsystem.chart.BalanceTrendChart
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.dashboardBalanceLabel
import com.kshavrin.mymoney.core.ui.theme.dashboardBalancePanel
import com.kshavrin.mymoney.core.ui.theme.dashboardBalancePanelContainer
import com.kshavrin.mymoney.core.ui.theme.dashboardBalancePanelContent
import com.kshavrin.mymoney.core.ui.theme.dashboardBalancePanelContentNegative
import com.kshavrin.mymoney.core.ui.theme.dashboardBalancePanelOutline
import com.kshavrin.mymoney.core.ui.theme.dashboardBalanceValue
import com.kshavrin.mymoney.core.ui.theme.dashboardCurrencyCardCurrencyCode
import com.kshavrin.mymoney.core.ui.theme.dashboardCurrencyCardCurrencyLabel
import com.kshavrin.mymoney.feature.dashboard.ChartConfig
import com.kshavrin.mymoney.feature.dashboard.CurrencyBalanceCard
import com.kshavrin.mymoney.feature.dashboard.R
import java.util.Locale

// "All accounts → show separately" (D6): a vertical stack of per-currency balance cards. Each card
// is self-contained in its own currency — income / expense / balance, no conversion. The donut is
// hidden by the caller in this mode; the list itself scrolls inside the dashboard's scroll content.
@Composable
fun CurrencyBalanceCardList(
    cards: List<CurrencyBalanceCard>,
    modifier: Modifier = Modifier,
    chartConfig: ChartConfig = ChartConfig(),
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(DASHBOARD_CURRENCY_CARDS_TAG),
        verticalArrangement = Arrangement.spacedBy(Spacing.dashboardCurrencyCardStackSpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        cards.forEach { card ->
            CurrencyBalanceCardItem(card = card, chartConfig = chartConfig)
        }
    }
}

@Composable
private fun CurrencyBalanceCardItem(
    card: CurrencyBalanceCard,
    chartConfig: ChartConfig,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val shape = MaterialTheme.shapes.dashboardBalancePanel
    val netNegative =
        card.snapshot.net.amount
            .signum() < 0
    Column(
        modifier =
            modifier
                .widthIn(max = Spacing.dashboardBalancePanelMaxWidth)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.dashboardBalancePanelContainer, shape)
                .border(
                    width = Spacing.dashboardBalancePanelBorderWidth,
                    color = MaterialTheme.colorScheme.dashboardBalancePanelOutline,
                    shape = shape,
                ).padding(horizontal = Spacing.m, vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text(
            text = card.currency.code,
            style = MaterialTheme.typography.dashboardCurrencyCardCurrencyCode,
            color = MaterialTheme.colorScheme.dashboardCurrencyCardCurrencyLabel,
            maxLines = 1,
        )
        CardFigureRow(
            label = stringResource(R.string.dashboard_currency_card_income),
            amount = formatCardAmount(card.snapshot.income, locale),
            isNegative = false,
        )
        CardFigureRow(
            label = stringResource(R.string.dashboard_currency_card_expense),
            amount = formatCardAmount(card.snapshot.expense, locale),
            isNegative = false,
        )
        CardFigureRow(
            label = stringResource(R.string.dashboard_currency_card_balance),
            amount = formatCardAmount(card.snapshot.net, locale),
            isNegative = netNegative,
        )
        if (chartConfig.visible && card.trendPoints.isNotEmpty()) {
            BalanceTrendChart(
                points = card.trendPoints.map { it.value.amount.toFloat() },
                showGridlines = false,
                showLabels = false,
                colorRule = chartConfig.colorRule,
                style = chartConfig.style,
                chartHeight = Spacing.trendChartMiniHeight,
                modifier = Modifier.testTag(DASHBOARD_CURRENCY_CARD_MINI_CHART_TAG),
            )
        }
    }
}

@Composable
private fun CardFigureRow(
    label: String,
    amount: String,
    isNegative: Boolean,
) {
    val contentColor =
        if (isNegative) {
            MaterialTheme.colorScheme.dashboardBalancePanelContentNegative
        } else {
            MaterialTheme.colorScheme.dashboardBalancePanelContent
        }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.dashboardBalanceLabel,
            color = contentColor,
            maxLines = 1,
        )
        Text(
            text = amount,
            style = MaterialTheme.typography.dashboardBalanceValue,
            color = contentColor,
            maxLines = 1,
        )
    }
}

private fun formatCardAmount(
    money: Money,
    locale: Locale,
): String =
    MoneyFormatter.format(
        amount = money.amount,
        currencySymbol = money.currency.symbol,
        decimalDigits = money.currency.decimalDigits,
        locale = locale,
        symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
    )

const val DASHBOARD_CURRENCY_CARDS_TAG = "dashboard_currency_cards"
const val DASHBOARD_CURRENCY_CARD_MINI_CHART_TAG = "dashboard_currency_card_mini_chart"
