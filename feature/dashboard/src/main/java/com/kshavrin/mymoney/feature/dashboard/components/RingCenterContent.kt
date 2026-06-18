package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    val periodNetText = formatRingAmount(periodNet, locale)
    val incomeText = stringResource(R.string.dashboard_ring_income, formatRingAmount(income, locale))
    val expenseText = stringResource(R.string.dashboard_ring_expense, formatRingAmount(expense, locale))

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val balanceStyle = MaterialTheme.typography.dashboardRingBalanceValue
        val minimumFontSize = MaterialTheme.typography.headlineMedium.fontSize
        var balanceFontSize by
            remember(periodNetText, maxWidth) {
                mutableStateOf(balanceStyle.fontSize)
            }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.dashboard_ring_balance),
                style = MaterialTheme.typography.dashboardRingBalanceLabel,
                color = MaterialTheme.colorScheme.textPrimary,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            Text(
                text = periodNetText,
                style = balanceStyle.copy(fontSize = balanceFontSize),
                color = MaterialTheme.colorScheme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                softWrap = false,
                textAlign = TextAlign.Center,
                onTextLayout = { result ->
                    if (result.didOverflowWidth && balanceFontSize > minimumFontSize) {
                        val reducedFontSize = balanceFontSize * FONT_SIZE_REDUCTION_FACTOR
                        balanceFontSize = if (reducedFontSize < minimumFontSize) minimumFontSize else reducedFontSize
                    }
                },
            )
            Surface(
                modifier = Modifier.padding(top = Spacing.s),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier =
                        Modifier.padding(
                            horizontal = Spacing.m,
                            vertical = Spacing.xs,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = incomeText,
                        style = MaterialTheme.typography.dashboardIncomeExpenseBadge,
                        color = MaterialTheme.colorScheme.incomeAccent,
                        maxLines = 1,
                    )
                    Text(
                        text = expenseText,
                        style = MaterialTheme.typography.dashboardIncomeExpenseBadge,
                        color = MaterialTheme.colorScheme.expenseAccent,
                        maxLines = 1,
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

private const val FONT_SIZE_REDUCTION_FACTOR = 0.9f
