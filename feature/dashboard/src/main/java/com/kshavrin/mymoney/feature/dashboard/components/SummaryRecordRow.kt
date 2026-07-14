package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import com.kshavrin.mymoney.core.common.money.MoneyFormatter
import com.kshavrin.mymoney.core.designsystem.icon.NeonCategoryIcon
import com.kshavrin.mymoney.core.designsystem.icon.NeonCategoryIconDefaults
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.SummaryRecord
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.expenseAccent
import com.kshavrin.mymoney.core.ui.theme.incomeAccent
import com.kshavrin.mymoney.core.ui.theme.textSecondary
import com.kshavrin.mymoney.core.ui.theme.transferArrowTint
import com.kshavrin.mymoney.core.ui.theme.transferRowAmount
import com.kshavrin.mymoney.core.ui.theme.transferRowMeta
import com.kshavrin.mymoney.core.ui.theme.transferRowRoute
import com.kshavrin.mymoney.feature.dashboard.R
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class SummaryRecordCategoryDisplay(
    val name: String,
    val iconKey: String,
)

@Composable
fun SummaryRecordRow(
    record: SummaryRecord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    currency: Currency? = null,
    categoryDisplay: SummaryRecordCategoryDisplay? = null,
    locale: Locale = Locale.getDefault(),
) {
    when (record) {
        is SummaryRecord.Operation ->
            OperationSummaryRecordRow(
                record = record,
                currency = currency,
                categoryDisplay = categoryDisplay,
                locale = locale,
                onClick = onClick,
                modifier = modifier,
            )

        is SummaryRecord.Transfer ->
            TransferSummaryRecordRow(
                record = record,
                locale = locale,
                onClick = onClick,
                modifier = modifier,
            )
    }
}

@Composable
private fun OperationSummaryRecordRow(
    record: SummaryRecord.Operation,
    currency: Currency?,
    categoryDisplay: SummaryRecordCategoryDisplay?,
    locale: Locale,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SummaryRecordBaseRow(
        modifier = modifier,
        onClick = onClick,
        leading = {
            NeonCategoryIcon(
                iconKey = categoryDisplay?.iconKey ?: SUMMARY_RECORD_FALLBACK_ICON_KEY,
                containerSize = NeonCategoryIconDefaults.CompactContainerSize,
                iconSize = NeonCategoryIconDefaults.CompactIconSize,
            )
        },
        primary = categoryDisplay?.name ?: stringResource(R.string.category_other),
        secondary = record.note,
        amount = formatOperationAmount(record.amount, currency, locale),
        amountColor =
            when (record.kind) {
                TransactionKind.Income -> MaterialTheme.colorScheme.incomeAccent
                TransactionKind.Expense -> MaterialTheme.colorScheme.expenseAccent
                TransactionKind.Transfer -> MaterialTheme.colorScheme.transferRowAmount
            },
        date = formatDate(record.timestamp, locale),
    )
}

@Composable
private fun TransferSummaryRecordRow(
    record: SummaryRecord.Transfer,
    locale: Locale,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SummaryRecordBaseRow(
        modifier = modifier,
        onClick = onClick,
        leading = {
            Icon(
                imageVector = Icons.Filled.SwapHoriz,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.transferArrowTint,
            )
        },
        primary =
            stringResource(
                R.string.operations_summary_transfer_route,
                record.fromAccountName,
                record.toAccountName,
            ),
        secondary = record.note,
        amount = formatMoney(record.amount, locale),
        amountColor = MaterialTheme.colorScheme.transferRowAmount,
        date = formatDate(record.timestamp, locale),
    )
}

@Composable
private fun SummaryRecordBaseRow(
    leading: @Composable () -> Unit,
    primary: String,
    secondary: String?,
    amount: String,
    amountColor: Color,
    date: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasSecondary = !secondary.isNullOrBlank()
    val recordDescription =
        stringResource(
            R.string.dashboard_summary_record_cd,
            primary,
            if (hasSecondary) secondary.orEmpty() else stringResource(R.string.dashboard_inline_records_no_note),
            amount,
            date,
        )
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = recordDescription }
                .heightIn(min = Spacing.dashboardInlineRecordRowHeight)
                .padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        leading()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = primary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    if (hasSecondary) {
                        secondary.orEmpty()
                    } else {
                        stringResource(R.string.dashboard_inline_records_no_note)
                    },
                style = MaterialTheme.typography.transferRowMeta,
                color = MaterialTheme.colorScheme.textSecondary,
                fontStyle = if (hasSecondary) FontStyle.Normal else FontStyle.Italic,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(Spacing.s))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = amount,
                style = MaterialTheme.typography.transferRowRoute,
                color = amountColor,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            Text(
                text = date,
                style = MaterialTheme.typography.transferRowMeta,
                color = MaterialTheme.colorScheme.textSecondary,
                maxLines = 1,
            )
        }
    }
}

private fun formatOperationAmount(
    amount: BigDecimal,
    currency: Currency?,
    locale: Locale,
): String =
    MoneyFormatter
        .format(
            amount = amount,
            currencySymbol = currency?.symbol.orEmpty(),
            decimalDigits = currency?.decimalDigits ?: DEFAULT_OPERATION_DECIMAL_DIGITS,
            locale = locale,
            symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
        ).trim()

private fun formatMoney(
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

private fun formatDate(
    timestamp: Instant,
    locale: Locale,
): String =
    timestamp
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ofPattern("d MMM", locale))

private const val DEFAULT_OPERATION_DECIMAL_DIGITS = 2
private const val SUMMARY_RECORD_FALLBACK_ICON_KEY = "ic_cat_other"
