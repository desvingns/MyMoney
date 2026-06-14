package com.kshavrin.mymoney.feature.transactionslist.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kshavrin.mymoney.core.common.money.MoneyFormatter
import com.kshavrin.mymoney.core.designsystem.appbar.MoneyHeroAppBar
import com.kshavrin.mymoney.core.designsystem.icon.categoryIcon
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.CategoryRecordGroup
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.model.TransferRecord
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.recordsHeaderBalance
import com.kshavrin.mymoney.core.ui.theme.recordsHeaderBalanceContainer
import com.kshavrin.mymoney.core.ui.theme.recordsHeaderBalanceContent
import com.kshavrin.mymoney.core.ui.theme.recordsHeaderBalanceOutline
import com.kshavrin.mymoney.core.ui.theme.recordsHeaderBalanceValue
import com.kshavrin.mymoney.core.ui.theme.recordsHeaderControl
import com.kshavrin.mymoney.core.ui.theme.recordsHeaderSortContainer
import com.kshavrin.mymoney.core.ui.theme.recordsHeaderSortTint
import com.kshavrin.mymoney.core.ui.theme.recordsHeaderStripContainer
import com.kshavrin.mymoney.core.ui.theme.transactionLeafNote
import com.kshavrin.mymoney.core.ui.theme.transactionLeafNoteColor
import com.kshavrin.mymoney.core.ui.theme.transferArrowTint
import com.kshavrin.mymoney.core.ui.theme.transferRowAmount
import com.kshavrin.mymoney.core.ui.theme.transferRowMeta
import com.kshavrin.mymoney.core.ui.theme.transferRowRoute
import com.kshavrin.mymoney.feature.transactionslist.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.kshavrin.mymoney.core.designsystem.R as DesignSystemR

@Composable
fun TransactionsListRoute(
    onOpenDetail: (Long) -> Unit,
    onSearch: () -> Unit,
    onTransfer: () -> Unit,
    onOverflow: () -> Unit,
    onBack: () -> Unit,
    viewModel: TransactionsListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val undoMessage = stringResource(R.string.transactions_list_delete_undo)
    val undoAction = stringResource(R.string.transactions_list_undo_action)

    LaunchedEffect(viewModel) {
        viewModel.actions.collect { action ->
            when (action) {
                is TransactionsListAction.OpenDetail -> onOpenDetail(action.transactionId)
                is TransactionsListAction.ShowUndoSnackbar -> {
                    snackbarScope.launch {
                        val result =
                            withTimeoutOrNull(UNDO_WINDOW_MILLIS) {
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
    }

    TransactionsListContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
        onSearch = onSearch,
        onTransfer = onTransfer,
        onOverflow = onOverflow,
        onBack = onBack,
    )
}

@Composable
fun TransactionsListContent(
    state: TransactionsListUiState,
    snackbarHostState: SnackbarHostState,
    onEvent: (TransactionsListEvent) -> Unit,
    onSearch: () -> Unit,
    onTransfer: () -> Unit,
    onOverflow: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            MoneyHeroAppBar(
                subtitle = state.currency?.name,
                leading = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.transactions_list_back),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(Spacing.xxl),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = stringResource(R.string.transactions_list_search),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(Spacing.xxl),
                        )
                    }
                    IconButton(onClick = onTransfer) {
                        Icon(
                            Icons.Filled.SwapHoriz,
                            contentDescription = stringResource(R.string.transactions_list_transfer),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(Spacing.xxl),
                        )
                    }
                    IconButton(onClick = onOverflow) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.transactions_list_more),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(Spacing.xxl),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            RecordsPeriodLabel(period = state.period)
            RecordsBalanceHeader(
                net = state.net,
                onSort = { onEvent(TransactionsListEvent.SortClicked) },
            )
            RecordsTabRow(
                activeTab = state.activeTab,
                onTabSelected = { tab -> onEvent(TransactionsListEvent.TabSelected(tab)) },
            )
            when (state.activeTab) {
                RecordsTab.Operations -> OperationsTab(state = state, onEvent = onEvent)
                RecordsTab.Transfers -> TransfersTab(state = state, onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun RecordsTabRow(
    activeTab: RecordsTab,
    onTabSelected: (RecordsTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    TabRow(
        selectedTabIndex = if (activeTab == RecordsTab.Operations) 0 else 1,
        modifier = modifier.fillMaxWidth(),
    ) {
        Tab(
            selected = activeTab == RecordsTab.Operations,
            onClick = { onTabSelected(RecordsTab.Operations) },
            text = { Text(stringResource(R.string.transactions_list_tab_operations)) },
            modifier = Modifier.testTag(RecordsTestTags.TAB_OPERATIONS),
        )
        Tab(
            selected = activeTab == RecordsTab.Transfers,
            onClick = { onTabSelected(RecordsTab.Transfers) },
            text = { Text(stringResource(R.string.transactions_list_tab_transfers)) },
            modifier = Modifier.testTag(RecordsTestTags.TAB_TRANSFERS),
        )
    }
}

@Composable
private fun OperationsTab(
    state: TransactionsListUiState,
    onEvent: (TransactionsListEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (state.hasCategoryFilter) {
            CategoryFilterChip(
                name = state.categoryName.orEmpty(),
                onClear = { onEvent(TransactionsListEvent.CategoryFilterCleared) },
            )
        }
        if (state.isEmpty) {
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = stringResource(R.string.transactions_list_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
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

@Composable
private fun TransfersTab(
    state: TransactionsListUiState,
    onEvent: (TransactionsListEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isTransfersEmpty) {
        Box(modifier = modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.transactions_list_transfers_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .padding(Spacing.l)
                        .testTag(RecordsTestTags.TRANSFERS_EMPTY),
            )
        }
    } else {
        LazyColumn(modifier = modifier.fillMaxSize()) {
            items(
                items = state.transfers,
                key = { transfer -> "transfer_${transfer.id}" },
            ) { transfer ->
                TransferRow(
                    transfer = transfer,
                    onClick = { onEvent(TransactionsListEvent.RowClicked(transfer.id)) },
                )
            }
        }
    }
}

@Composable
private fun TransferRow(
    transfer: TransferRecord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .clickable(onClick = onClick)
                .testTag(RecordsTestTags.transfer(transfer.id))
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(
            imageVector = Icons.Filled.SwapHoriz,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.transferArrowTint,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text =
                    stringResource(
                        R.string.transactions_list_transfer_route,
                        transfer.fromAccountName,
                        transfer.toAccountName,
                    ),
                style = MaterialTheme.typography.transferRowRoute,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val note = transfer.note
            if (!note.isNullOrBlank()) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.transferRowMeta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(RecordsTestTags.transferNote(transfer.id)),
                )
            }
            Text(
                text =
                    transfer.occurredAt
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .format(LEAF_DATE_FORMAT),
                style = MaterialTheme.typography.transferRowMeta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = formatMoney(transfer.amount),
            style = MaterialTheme.typography.transferRowRoute,
            color = MaterialTheme.colorScheme.transferRowAmount,
        )
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
private fun RecordsPeriodLabel(
    period: Period,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val allLabel = stringResource(R.string.transactions_list_period_all)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.l, vertical = Spacing.s),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = period.previous().localizedLabel(locale, allLabel),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = period.localizedLabel(locale, allLabel),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
        Text(
            text = period.next().localizedLabel(locale, allLabel),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun RecordsBalanceHeader(
    net: Money?,
    onSort: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth(),
        color = MaterialTheme.colorScheme.recordsHeaderStripContainer,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.l, end = Spacing.l, top = Spacing.s, bottom = Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Icon(
                Icons.Filled.Menu,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.recordsHeaderSortTint,
            )
            BalanceBar(
                net = net,
                modifier = Modifier.weight(1f),
            )
            RecordsSortButton(onSort = onSort)
        }
    }
}

@Composable
private fun BalanceBar(
    net: Money?,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(DesignSystemR.string.balance_bar_label)
    val amount =
        net?.let {
            MoneyFormatter.format(
                amount = it.amount,
                currencySymbol = it.currency.symbol,
                decimalDigits = it.currency.decimalDigits,
                locale = Locale.getDefault(),
                symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
            )
        } ?: ""
    Surface(
        modifier =
            modifier
                .testTag(RecordsTestTags.BALANCE),
        shape = MaterialTheme.shapes.recordsHeaderBalance,
        color = MaterialTheme.colorScheme.recordsHeaderBalanceContainer,
        contentColor = MaterialTheme.colorScheme.recordsHeaderBalanceContent,
        border = BorderStroke(Spacing.xxs, MaterialTheme.colorScheme.recordsHeaderBalanceOutline),
        shadowElevation = Spacing.xs,
    ) {
        Text(
            text = "$label $amount",
            style = MaterialTheme.typography.recordsHeaderBalanceValue,
            color = MaterialTheme.colorScheme.recordsHeaderBalanceContent,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.m, vertical = Spacing.m),
        )
    }
}

@Composable
private fun RecordsSortButton(
    onSort: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.recordsHeaderControl,
        color = MaterialTheme.colorScheme.recordsHeaderSortContainer,
        contentColor = MaterialTheme.colorScheme.recordsHeaderSortTint,
    ) {
        IconButton(
            onClick = onSort,
            modifier = Modifier.testTag(RecordsTestTags.SORT),
        ) {
            Icon(
                Icons.Filled.SwapVert,
                contentDescription = stringResource(R.string.transactions_list_sort),
                tint = MaterialTheme.colorScheme.recordsHeaderSortTint,
            )
        }
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
    val totalColor =
        when (group.kind) {
            CategoryKind.Income -> MaterialTheme.colorScheme.primary
            CategoryKind.Expense -> MaterialTheme.colorScheme.tertiary
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .testTag(RecordsTestTags.category(group.categoryId))
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(
            imageVector =
                if (expanded) {
                    Icons.Filled.KeyboardArrowDown
                } else {
                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                },
            contentDescription =
                stringResource(
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
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier.testTag(RecordsTestTags.count(group.categoryId)),
        ) {
            Text(
                text = stringResource(R.string.transactions_list_count, group.count),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
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
    val amountColor =
        when (kind) {
            CategoryKind.Income -> MaterialTheme.colorScheme.primary
            CategoryKind.Expense -> MaterialTheme.colorScheme.tertiary
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .clickable(onClick = onClick)
                .testTag(RecordsTestTags.transaction(transaction.id))
                .padding(start = Spacing.xl, end = Spacing.l, top = Spacing.s, bottom = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(
            modifier =
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor),
        )
        Text(
            text = formatLeafAmount(transaction.kind, transaction.amount, currency),
            style = MaterialTheme.typography.bodyLarge,
            color = amountColor,
        )
        val note = transaction.note
        if (note.isNullOrBlank()) {
            Spacer(modifier = Modifier.weight(1f))
        } else {
            Text(
                text = note,
                style = MaterialTheme.typography.transactionLeafNote,
                color = MaterialTheme.colorScheme.transactionLeafNoteColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .weight(1f)
                        .testTag(RecordsTestTags.transactionNote(transaction.id)),
            )
        }
        Text(
            text =
                transaction.occurredAt
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .format(LEAF_DATE_FORMAT),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val LEAF_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

private fun formatMoney(money: Money): String =
    MoneyFormatter.format(
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

private fun parseHexColor(hex: String): Color =
    try {
        val cleaned = hex.removePrefix("#")
        val argb = if (cleaned.length == 6) "FF$cleaned" else cleaned
        Color(argb.toLong(16))
    } catch (_: Exception) {
        Color.Gray
    }

private fun Period.localizedLabel(
    locale: Locale,
    allLabel: String,
): String =
    when (this) {
        is Period.Day -> date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
        is Period.Week -> {
            val formatter = DateTimeFormatter.ofPattern("d MMM", locale)
            "${weekStart.format(formatter)} - ${weekStart.plusDays(6).format(formatter)}"
        }
        is Period.Month -> yearMonth.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        is Period.Year -> year.toString()
        Period.All -> allLabel
        is Period.CustomRange -> {
            val formatter = DateTimeFormatter.ofPattern("d MMM", locale)
            "${start.format(formatter)} - ${end.format(formatter)}"
        }
    }

private const val UNDO_WINDOW_MILLIS = 5_000L
