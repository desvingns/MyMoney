package com.kshavrin.mymoney.feature.dictionaries.currencies

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kshavrin.mymoney.feature.dictionaries.R
import com.kshavrin.mymoney.feature.dictionaries.common.BlockedDeleteDialog

@Composable
fun CurrencyEditRoute(
    onBack: () -> Unit,
    viewModel: CurrencyEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.actions.collect { action ->
            when (action) {
                CurrencyEditAction.NavigateBack -> onBack()
            }
        }
    }
    CurrencyEditContent(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyEditContent(
    state: CurrencyEditState,
    onEvent: (CurrencyEditEvent) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isCreateMode) R.string.dictionaries_currency_new
                            else R.string.dictionaries_currency_edit,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onEvent(CurrencyEditEvent.SaveClicked) }) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.dictionaries_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.code,
                onValueChange = { onEvent(CurrencyEditEvent.CodeChanged(it)) },
                label = { Text(stringResource(R.string.dictionaries_field_code)) },
                isError = state.errorMessage == "code_format",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.symbol,
                onValueChange = { onEvent(CurrencyEditEvent.SymbolChanged(it)) },
                label = { Text(stringResource(R.string.dictionaries_field_symbol)) },
                isError = state.errorMessage == "symbol_required",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.name,
                onValueChange = { onEvent(CurrencyEditEvent.NameChanged(it)) },
                label = { Text(stringResource(R.string.dictionaries_field_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.decimalDigitsText,
                onValueChange = { onEvent(CurrencyEditEvent.DecimalDigitsChanged(it)) },
                label = { Text(stringResource(R.string.dictionaries_field_decimal_digits)) },
                isError = state.errorMessage == "decimal_digits",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.dictionaries_field_is_active))
                Switch(
                    checked = state.isActive,
                    onCheckedChange = { onEvent(CurrencyEditEvent.IsActiveChanged(it)) },
                )
            }

            if (state.errorMessage != null) {
                val errorText = when (state.errorMessage) {
                    "code_format" -> stringResource(R.string.dictionaries_error_code_format)
                    "symbol_required" -> stringResource(R.string.dictionaries_error_symbol_required)
                    "decimal_digits" -> stringResource(R.string.dictionaries_error_decimal_digits)
                    else -> stringResource(R.string.dictionaries_error_generic)
                }
                Text(
                    text = errorText,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Button(
                    onClick = { onEvent(CurrencyEditEvent.SaveClicked) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.dictionaries_save))
                }
                if (!state.isCreateMode) {
                    OutlinedButton(
                        onClick = { onEvent(CurrencyEditEvent.DeleteClicked) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.dictionaries_delete))
                    }
                }
            }
        }
    }

    if (state.blockedDeleteCount != null) {
        BlockedDeleteDialog(
            entityName = state.code,
            transactionCount = state.blockedDeleteCount,
            onDismiss = { onEvent(CurrencyEditEvent.BlockedDeleteDismissed) },
        )
    }
}
