package com.kshavrin.mymoney.feature.dictionaries.accounts

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kshavrin.mymoney.core.common.money.MoneyFormatter
import com.kshavrin.mymoney.core.designsystem.icon.accountIcon
import com.kshavrin.mymoney.feature.dictionaries.R
import com.kshavrin.mymoney.feature.dictionaries.common.parseHexColor
import java.util.Locale

@Composable
fun AccountsListRoute(
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: AccountsListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.actions.collect { action ->
            when (action) {
                is AccountsListAction.NavigateEdit -> onEdit(action.id)
                AccountsListAction.NavigateAdd -> onAdd()
                AccountsListAction.NavigateBack -> onBack()
            }
        }
    }
    AccountsListContent(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsListContent(
    state: AccountsListState,
    onEvent: (AccountsListEvent) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dictionaries_accounts_title)) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(AccountsListEvent.BackClicked) }) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.dictionaries_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEvent(AccountsListEvent.AddClicked) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.dictionaries_add))
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            items(state.rows, key = { it.account.id }) { row ->
                AccountRowItem(
                    row = row,
                    onClick = { onEvent(AccountsListEvent.ItemClicked(row.account.id)) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun AccountRowItem(
    row: AccountRow,
    onClick: () -> Unit,
) {
    val currencySymbol = row.currency?.symbol ?: ""
    val decimalDigits = row.currency?.decimalDigits ?: 2
    val balanceText =
        MoneyFormatter.format(
            amount = row.balance,
            currencySymbol = currencySymbol,
            decimalDigits = decimalDigits,
            locale = Locale.getDefault(),
        )
    val rowDescription = stringResource(R.string.dictionaries_account_row_cd, row.account.name)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = rowDescription }
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(parseHexColor(row.account.colorHex)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = accountIcon(row.account.iconKey),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.account.name, style = MaterialTheme.typography.bodyLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${stringResource(R.string.dictionaries_balance_label)}: $balanceText",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (row.account.isDefault) {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.dictionaries_default_badge)) },
                        colors = AssistChipDefaults.assistChipColors(),
                    )
                }
            }
        }
    }
}
