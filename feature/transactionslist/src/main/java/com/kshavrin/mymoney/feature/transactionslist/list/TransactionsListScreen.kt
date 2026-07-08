package com.kshavrin.mymoney.feature.transactionslist.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.kshavrin.mymoney.feature.transactionslist.R
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TransactionsListRoute(
    onOpenDetail: (Long) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    viewModel: TransactionsListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.actions.collect { action ->
            when (action) {
                is TransactionsListAction.OpenDetail -> onOpenDetail(action.transactionId)
            }
        }
    }

    TransactionsListContent(
        state = state,
        onEvent = viewModel::onEvent,
        onSearch = onSearch,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsListContent(
    state: TransactionsListUiState,
    onEvent: (TransactionsListEvent) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.transactions_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.transactions_list_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = stringResource(R.string.transactions_list_search),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            if (state.hasCategoryFilter) {
                CategoryFilterChip(
                    name = state.categoryName.orEmpty(),
                    onClear = { onEvent(TransactionsListEvent.CategoryFilterCleared) },
                )
            }
            when {
                state.isEmpty -> TransactionsListEmpty()
                else ->
                    TransactionsListRows(
                        state = state,
                        onEvent = onEvent,
                    )
            }
        }
    }
}

@Composable
private fun CategoryFilterChip(
    name: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = onClear,
        label = {
            Text(text = stringResource(R.string.transactions_list_filter_chip, name))
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.transactions_list_remove_filter),
            )
        },
        modifier =
            modifier
                .padding(horizontal = Spacing.l, vertical = Spacing.s)
                .testTag(RecordsTestTags.FILTER),
    )
}

@Composable
private fun TransactionsListEmpty(
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.transactions_list_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.textSecondary,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(Spacing.l)
                    .testTag(RecordsTestTags.EMPTY),
        )
    }
}

@Composable
private fun TransactionsListRows(
    state: TransactionsListUiState,
    onEvent: (TransactionsListEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .testTag(RecordsTestTags.LIST),
    ) {
        items(
            items = state.records,
            key = ::transactionsListItemKey,
        ) { record ->
            TransactionListRow(
                record = record,
                currency = (record as? SummaryRecord.Operation)?.let { state.currencies[it.currencyId] },
                categoryDisplay =
                    (record as? SummaryRecord.Operation)
                        ?.categoryId
                        ?.let(state.categoryDisplays::get),
                locale = locale,
                onClick = { onEvent(TransactionsListEvent.RowClicked(record.id)) },
            )
        }
    }
}

@Composable
private fun TransactionListRow(
    record: SummaryRecord,
    currency: Currency?,
    categoryDisplay: TransactionCategoryDisplay?,
    locale: Locale,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (record) {
        is SummaryRecord.Operation ->
            OperationRow(
                record = record,
                currency = currency,
                categoryDisplay = categoryDisplay,
                locale = locale,
                onClick = onClick,
                modifier = modifier.testTag(RecordsTestTags.transaction(record.id)),
            )
        is SummaryRecord.Transfer ->
            TransferRow(
                record = record,
                locale = locale,
                onClick = onClick,
                modifier = modifier.testTag(RecordsTestTags.transfer(record.id)),
            )
    }
}

@Composable
private fun OperationRow(
    record: SummaryRecord.Operation,
    currency: Currency?,
    categoryDisplay: TransactionCategoryDisplay?,
    locale: Locale,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BaseRecordRow(
        modifier = modifier,
        onClick = onClick,
        leading = {
            NeonCategoryIcon(
                iconKey = categoryDisplay?.iconKey ?: SUMMARY_RECORD_FALLBACK_ICON_KEY,
                containerSize = NeonCategoryIconDefaults.CompactContainerSize,
                iconSize = NeonCategoryIconDefaults.CompactIconSize,
            )
        },
        primary = categoryDisplay?.name ?: stringResource(R.string.transactions_list_category_other),
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
private fun TransferRow(
    record: SummaryRecord.Transfer,
    locale: Locale,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BaseRecordRow(
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
                R.string.transactions_list_transfer_route,
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
private fun BaseRecordRow(
    leading: @Composable () -> Unit,
    primary: String,
    secondary: String?,
    amount: String,
    amountColor: androidx.compose.ui.graphics.Color,
    date: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasSecondary = !secondary.isNullOrBlank()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
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
                text = if (hasSecondary) secondary.orEmpty() else stringResource(R.string.transactions_list_no_note),
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

private fun transactionsListItemKey(record: SummaryRecord): String =
    when (record) {
        is SummaryRecord.Operation -> "operation_${record.id}"
        is SummaryRecord.Transfer -> "transfer_${record.id}"
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
