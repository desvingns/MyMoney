package com.kshavrin.mymoney.feature.dictionaries.currencies

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.feature.dictionaries.R

@Composable
fun CurrenciesListRoute(
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: CurrenciesListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.actions.collect { action ->
            when (action) {
                is CurrenciesListAction.NavigateEdit -> onEdit(action.id)
                CurrenciesListAction.NavigateAdd -> onAdd()
                CurrenciesListAction.NavigateBack -> onBack()
            }
        }
    }
    CurrenciesListContent(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrenciesListContent(
    state: CurrenciesListState,
    onEvent: (CurrenciesListEvent) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dictionaries_currencies_title)) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(CurrenciesListEvent.BackClicked) }) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.dictionaries_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEvent(CurrenciesListEvent.AddClicked) }) {
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
            items(state.currencies, key = { it.id }) { currency ->
                CurrencyRowItem(
                    currency = currency,
                    onClick = { onEvent(CurrenciesListEvent.ItemClicked(currency.id)) },
                    onActiveChange = { active ->
                        onEvent(CurrenciesListEvent.ActiveToggled(currency.id, active))
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun CurrencyRowItem(
    currency: Currency,
    onClick: () -> Unit,
    onActiveChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${currency.code} ${currency.symbol}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = currency.name,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(
            checked = currency.isActive,
            onCheckedChange = onActiveChange,
        )
    }
}
