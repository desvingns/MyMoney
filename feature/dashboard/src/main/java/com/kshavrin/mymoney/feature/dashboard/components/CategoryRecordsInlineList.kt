package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kshavrin.mymoney.core.common.money.MoneyFormatter
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.dashboardInlineRecordContainer
import com.kshavrin.mymoney.core.ui.theme.dashboardInlineRecordDivider
import com.kshavrin.mymoney.core.ui.theme.textSecondary
import com.kshavrin.mymoney.feature.dashboard.R
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

const val DASHBOARD_INLINE_RECORDS_TAG = "dashboard_inline_records"
const val DASHBOARD_INLINE_RECORDS_LOADING_TAG = "dashboard_inline_records_loading"

@Composable
fun CategoryRecordsInlineList(
    records: List<Transaction>,
    loading: Boolean,
    currencies: List<Currency>,
    onRowClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier =
            modifier
                .testTag(DASHBOARD_INLINE_RECORDS_TAG)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.dashboardInlineRecordContainer),
    ) {
        if (loading) {
            Text(
                text = stringResource(R.string.dashboard_inline_records_loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.textSecondary,
                modifier =
                    Modifier
                        .testTag(DASHBOARD_INLINE_RECORDS_LOADING_TAG)
                        .fillMaxWidth()
                        .heightIn(min = Spacing.dashboardInlineRecordRowHeight)
                        .padding(horizontal = Spacing.l, vertical = Spacing.s),
            )
        } else {
            records.forEachIndexed { index, record ->
                if (index > 0) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.dashboardInlineRecordDivider),
                    )
                }
                val currencySymbol =
                    currencies.firstOrNull { it.id == record.currencyId }?.symbol.orEmpty()
                CategoryRecordRow(
                    note = record.note,
                    amount = record.amount,
                    currencySymbol = currencySymbol,
                    occurredAt = record.occurredAt,
                    locale = locale,
                    onClick = { onRowClick(record.id) },
                )
            }
        }
    }
}

@Composable
private fun CategoryRecordRow(
    note: String?,
    amount: BigDecimal,
    currencySymbol: String,
    occurredAt: Instant,
    locale: Locale,
    onClick: () -> Unit,
) {
    val hasNote = !note.isNullOrBlank()
    val formattedAmount =
        MoneyFormatter.format(
            amount = amount,
            currencySymbol = currencySymbol,
            decimalDigits = 0,
            locale = locale,
            symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
        )
    val formattedDate =
        occurredAt
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(RECORD_DATE_FORMAT)
    val recordDescription =
        stringResource(
            R.string.dashboard_inline_record_cd,
            if (hasNote) note.orEmpty() else stringResource(R.string.dashboard_inline_records_no_note),
            formattedAmount,
            formattedDate,
        )

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = recordDescription }
                .heightIn(min = Spacing.dashboardInlineRecordRowHeight)
                .padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text =
                if (hasNote) {
                    note.orEmpty()
                } else {
                    stringResource(R.string.dashboard_inline_records_no_note)
                },
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (hasNote) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.textSecondary
                },
            fontStyle = if (hasNote) FontStyle.Normal else FontStyle.Italic,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(Spacing.m))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formattedAmount,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            Text(
                text = formattedDate,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.textSecondary,
                maxLines = 1,
            )
        }
    }
}

private val RECORD_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
