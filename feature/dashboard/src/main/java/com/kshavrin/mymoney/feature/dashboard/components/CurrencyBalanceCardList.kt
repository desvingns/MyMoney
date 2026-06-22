package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.kshavrin.mymoney.core.common.money.MoneyFormatter
import com.kshavrin.mymoney.core.designsystem.chart.BalanceTrendChart
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.dashboardAuroraBalanceValueCompact
import com.kshavrin.mymoney.core.ui.theme.dashboardCurrencyCardCurrencyCode
import com.kshavrin.mymoney.feature.dashboard.ChartConfig
import com.kshavrin.mymoney.feature.dashboard.CurrencyBalanceCard
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

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
    AuroraCardSurface(
        cardTestTag = currencyCardTagFor(card.currency.code),
        modifier = modifier,
    ) {
        Text(
            text = card.currency.code,
            style = MaterialTheme.typography.dashboardCurrencyCardCurrencyCode,
            color = Color.White.copy(alpha = 0.55f),
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Spacing.dashboardAuroraValueBottomMargin))
        Text(
            text = formatCardAmount(card.snapshot.net, locale),
            style = MaterialTheme.typography.dashboardAuroraBalanceValueCompact,
            color = Color.White,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Spacing.dashboardAuroraPillBottomMarginCompact))
        IncomeExpensePills(
            income = formatCardAmount(card.snapshot.income, locale),
            expense = formatCardAmount(card.snapshot.expense, locale),
        )
        if (chartConfig.visible && card.trendPoints.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Spacing.dashboardAuroraPillBottomMarginCompact))
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

private fun formatCardAmount(
    money: Money,
    locale: Locale,
): String =
    MoneyFormatter.format(
        amount = truncateCardAmount(money.amount),
        currencySymbol = money.currency.symbol,
        decimalDigits = 0,
        locale = locale,
        symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
    )

private fun truncateCardAmount(amount: BigDecimal): BigDecimal = amount.setScale(0, RoundingMode.DOWN)

private fun currencyCardTagFor(code: String): String = "${DASHBOARD_CURRENCY_CARDS_TAG}_$code"

const val DASHBOARD_CURRENCY_CARDS_TAG = "dashboard_currency_cards"
const val DASHBOARD_CURRENCY_CARD_MINI_CHART_TAG = "dashboard_currency_card_mini_chart"
