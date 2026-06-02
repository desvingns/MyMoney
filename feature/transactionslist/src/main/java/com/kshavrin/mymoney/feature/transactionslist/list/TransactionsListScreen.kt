package com.kshavrin.mymoney.feature.transactionslist.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kshavrin.mymoney.core.common.money.MoneyFormatter
import com.kshavrin.mymoney.core.designsystem.icon.categoryIcon
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.CategoryRecordGroup
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.transactionslist.R
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.kshavrin.mymoney.core.designsystem.R as DesignSystemR

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
                    IconButton(
                        onClick = { onEvent(TransactionsListEvent.SortClicked) },
                        modifier = Modifier.testTag(RecordsTestTags.SORT),
                    ) {
                        Icon(
                            Icons.Filled.SwapVert,
                            contentDescription = stringResource(R.string.transactions_list_sort),
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            BalanceBar(net = state.net)
            if (state.isEmpty) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = stringResource(R.string.transactions_list_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(Spacing.l)
                            .testTag(RecordsTestTags.EMPTY),
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    state.sortedGroups.forEach { group ->
                        val expanded = group.categoryId in state.expandedCategoryIds
                        item(key = "cat_${group.categoryId}") {
                            CategoryHeader(
                                group = group,
                                expanded = expanded,
                                onClick = { onEvent(TransactionsListEvent.CategoryClicked(group.categoryId)) },
                            )
                        }
                        if (expanded) {
                            items(
                                items = group.transactions,
                                key = { tx -> "tx_${tx.id}" },
                            ) { tx ->
                                SwipeToDelete(
                                    onDelete = { onEvent(TransactionsListEvent.SwipeDeleted(tx.id)) },
                                ) {
                                    TransactionLeaf(
                                        transaction = tx,
                                        colorHex = group.colorHex,
                                        kind = group.kind,
                                        currency = state.currency,
                                        onClick = { onEvent(TransactionsListEvent.RowClicked(tx.id)) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceBar(net: Money?, modifier: Modifier = Modifier) {
    val label = stringResource(DesignSystemR.string.balance_bar_label)
    val amount = net?.let {
        MoneyFormatter.format(
            amount = it.amount,
            currencySymbol = it.currency.symbol,
            decimalDigits = it.currency.decimalDigits,
            locale = Locale.getDefault(),
            symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
        )
    } ?: ""
    val isPositive = net == null || !net.isNegative()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.l, vertical = Spacing.s)
            .testTag(RecordsTestTags.BALANCE),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = "$label $amount",
            style = MaterialTheme.typography.titleMedium,
            color = if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
        )
    }
}

@Composable
private fun CategoryHeader(
    group: CategoryRecordGroup,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = parseHexColor(group.colorHex)
    val totalColor = when (group.kind) {
        CategoryKind.Income -> MaterialTheme.colorScheme.primary
        CategoryKind.Expense -> MaterialTheme.colorScheme.tertiary
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(RecordsTestTags.category(group.categoryId))
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(
            imageVector = if (expanded) {
                Icons.Filled.KeyboardArrowDown
            } else {
                Icons.AutoMirrored.Filled.KeyboardArrowRight
            },
            contentDescription = stringResource(
                if (expanded) R.string.transactions_list_collapse else R.string.transactions_list_expand,
            ),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(RecordsTestTags.chevron(group.categoryId)),
        )
        Icon(
            imageVector = categoryIcon(group.iconKey),
            contentDescription = null,
            tint = tint,
        )
        Text(
            text = group.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.transactions_list_count, group.count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(RecordsTestTags.count(group.categoryId)),
        )
        Text(
            text = formatMoney(group.total),
            style = MaterialTheme.typography.titleMedium,
            color = totalColor,
            modifier = Modifier.testTag(RecordsTestTags.total(group.categoryId)),
        )
    }
}

@Composable
private fun TransactionLeaf(
    transaction: Transaction,
    colorHex: String,
    kind: CategoryKind,
    currency: Currency?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dotColor = parseHexColor(colorHex)
    val amountColor = when (kind) {
        CategoryKind.Income -> MaterialTheme.colorScheme.primary
        CategoryKind.Expense -> MaterialTheme.colorScheme.tertiary
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(RecordsTestTags.transaction(transaction.id))
            .padding(start = Spacing.xl, end = Spacing.l, top = Spacing.s, bottom = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            text = formatLeafAmount(transaction.kind, transaction.amount, currency),
            style = MaterialTheme.typography.bodyLarge,
            color = amountColor,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = transaction.occurredAt.atZone(ZoneId.systemDefault()).toLocalDate().format(LEAF_DATE_FORMAT),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val LEAF_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

private fun formatMoney(money: Money): String = MoneyFormatter.format(
    amount = money.amount,
    currencySymbol = money.currency.symbol,
    decimalDigits = money.currency.decimalDigits,
    locale = Locale.getDefault(),
    symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
)

private fun formatLeafAmount(
    kind: TransactionKind,
    amount: BigDecimal,
    currency: Currency?,
): String {
    val symbol = currency?.symbol ?: ""
    val digits = currency?.decimalDigits ?: 2
    return MoneyFormatter.format(
        amount = amount,
        currencySymbol = symbol,
        decimalDigits = digits,
        locale = Locale.getDefault(),
        symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
    )
}

private fun parseHexColor(hex: String): Color = try {
    val cleaned = hex.removePrefix("#")
    val argb = if (cleaned.length == 6) "FF$cleaned" else cleaned
    Color(argb.toLong(16))
} catch (_: Exception) {
    Color.Gray
}

private const val UNDO_WINDOW_MILLIS = 5_000L
