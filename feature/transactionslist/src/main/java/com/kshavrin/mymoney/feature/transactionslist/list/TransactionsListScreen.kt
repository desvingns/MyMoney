package com.kshavrin.mymoney.feature.transactionslist.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.kshavrin.mymoney.core.common.money.MoneyFormatter
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.transactionslist.R
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun TransactionsListRoute(
    onOpenDetail: (Long) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    viewModel: TransactionsListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val undoMessage = stringResource(R.string.transactions_list_delete_undo)
    val undoAction = stringResource(R.string.transactions_list_undo_action)

    LaunchedEffect(viewModel) {
        viewModel.actions.collect { action ->
            when (action) {
                is TransactionsListAction.OpenDetail -> onOpenDetail(action.transactionId)
                is TransactionsListAction.ShowUndoSnackbar -> {
                    // AS-9 / BR-24: explicit 5s window. Indefinite duration defers dismissal to
                    // the timeout so the soft-delete becomes final exactly after 5 seconds.
                    val result = withTimeoutOrNull(UNDO_WINDOW_MILLIS) {
                        snackbarHostState.showSnackbar(
                            message = undoMessage,
                            actionLabel = undoAction,
                            duration = SnackbarDuration.Indefinite,
                        )
                    }
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.onEvent(TransactionsListEvent.UndoDeleteClicked(action.transactionId))
                    }
                }
            }
        }
    }

    TransactionsListContent(
        state = state,
        items = viewModel.pagingData.collectAsLazyPagingItems(),
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
        onSearch = onSearch,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsListContent(
    state: TransactionsListUiState,
    items: LazyPagingItems<TransactionListItem>,
    snackbarHostState: SnackbarHostState,
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val refreshing = items.loadState.refresh is LoadState.Loading
            val isEmpty = items.itemCount == 0 && !refreshing
            if (isEmpty) {
                Text(
                    text = stringResource(R.string.transactions_list_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(Spacing.l),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    state.categoryFilter?.let { category ->
                        item(key = "filter_chip") {
                            FilterChipsRow(label = category.name)
                        }
                    }
                    items(
                        count = items.itemCount,
                        key = { index ->
                            when (val it = items.peek(index)) {
                                is TransactionListItem.DayHeader -> "h_${it.date}"
                                is TransactionListItem.Row -> "r_${it.id}"
                                null -> "p_$index"
                            }
                        },
                    ) { index ->
                        when (val item = items[index]) {
                            is TransactionListItem.DayHeader -> DayHeader(item)
                            is TransactionListItem.Row -> SwipeToDelete(
                                onDelete = { onEvent(TransactionsListEvent.SwipeDeleted(item.id)) },
                            ) {
                                TransactionRow(
                                    item = item,
                                    currency = state.currency,
                                    onClick = { onEvent(TransactionsListEvent.RowClicked(item.id)) },
                                )
                            }
                            null -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipsRow(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        FilterChip(
            selected = true,
            onClick = {},
            label = { Text(stringResource(R.string.transactions_list_filter_chip, label)) },
        )
    }
}

@Composable
private fun DayHeader(item: TransactionListItem.DayHeader, modifier: Modifier = Modifier) {
    Text(
        text = item.date.format(DAY_HEADER_FORMAT),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
    )
}

@Composable
private fun TransactionRow(
    item: TransactionListItem.Row,
    currency: Currency?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val amountColor = when (item.kind) {
        TransactionKind.Expense -> MaterialTheme.colorScheme.tertiary
        TransactionKind.Income -> MaterialTheme.colorScheme.primary
        TransactionKind.Transfer -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val leadingIcon = when (item.kind) {
        TransactionKind.Transfer -> Icons.Filled.CompareArrows
        else -> Icons.Filled.Category
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(
            imageVector = leadingIcon,
            contentDescription = null,
            tint = amountColor,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = formatSignedAmount(item.kind, item.amount, currency),
                style = MaterialTheme.typography.titleMedium,
                color = amountColor,
            )
            val note = item.note
            if (!note.isNullOrBlank()) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = formatTime(item.occurredAt),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onClick) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.transactions_list_open_detail),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val DAY_HEADER_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())

private val TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())

private fun formatTime(instant: Instant): String =
    TIME_FORMAT.format(instant.atZone(ZoneId.systemDefault()))

private fun formatSignedAmount(
    kind: TransactionKind,
    amount: BigDecimal,
    currency: Currency?,
): String {
    val symbol = currency?.symbol ?: ""
    val digits = currency?.decimalDigits ?: 2
    val formatted = MoneyFormatter.format(
        amount = amount,
        currencySymbol = symbol,
        decimalDigits = digits,
        locale = Locale.getDefault(),
    )
    val sign = when (kind) {
        TransactionKind.Expense -> "-"
        TransactionKind.Income -> "+"
        TransactionKind.Transfer -> ""
    }
    return "$sign$formatted"
}

private const val UNDO_WINDOW_MILLIS = 5_000L
