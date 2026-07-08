package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.SummaryRecord
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.dashboardInlineRecordDivider
import com.kshavrin.mymoney.core.ui.theme.textSecondary
import com.kshavrin.mymoney.feature.dashboard.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationsSummarySheet(
    records: List<SummaryRecord>,
    loading: Boolean,
    title: String,
    onRowClick: (Long) -> Unit,
    onOpenTransactionsList: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    currencies: List<Currency> = emptyList(),
    categoryDisplays: Map<Long, SummaryRecordCategoryDisplay> = emptyMap(),
) {
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val topBarClearance =
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
            Spacing.dashboardTopBarMinHeight
    val sheetHeight =
        (configuration.screenHeightDp.dp - topBarClearance)
            .coerceAtLeast(Spacing.dashboardTopBarMinHeight)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier =
            modifier
                .height(sheetHeight)
                .testTag(OPERATIONS_SUMMARY_SHEET_TAG),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = Spacing.l)
                    .padding(bottom = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenTransactionsList) {
                    Text(text = stringResource(R.string.operations_summary_open_transactions))
                }
            }
            when {
                loading -> {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                records.isEmpty() -> {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag(OPERATIONS_SUMMARY_EMPTY_TAG),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.operations_summary_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                    ) {
                        itemsIndexed(
                            items = records,
                            key = { _, record -> operationsSummaryItemKey(record) },
                        ) { index, record ->
                            SummaryRecordRow(
                                record = record,
                                onClick = { onRowClick(record.id) },
                                modifier = Modifier.testTag(operationsSummaryRowTag(record.id)),
                                currency =
                                    (record as? SummaryRecord.Operation)?.let { operation ->
                                        currencies.firstOrNull { it.id == operation.currencyId }
                                    },
                                categoryDisplay =
                                    (record as? SummaryRecord.Operation)
                                        ?.categoryId
                                        ?.let(categoryDisplays::get),
                                locale = locale,
                            )
                            if (index < records.lastIndex) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .height(Spacing.dashboardBalancePanelBorderWidth)
                                            .background(MaterialTheme.colorScheme.dashboardInlineRecordDivider),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

const val OPERATIONS_SUMMARY_SHEET_TAG = "operations_summary_sheet"
const val OPERATIONS_SUMMARY_EMPTY_TAG = "operations_summary_empty"

fun operationsSummaryRowTag(id: Long): String = "operations_summary_row_$id"

private fun operationsSummaryItemKey(record: SummaryRecord): String =
    when (record) {
        is SummaryRecord.Operation -> "operation_${record.id}"
        is SummaryRecord.Transfer -> "transfer_${record.id}"
    }
