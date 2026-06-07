package com.kshavrin.mymoney.feature.dictionaries.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kshavrin.mymoney.core.designsystem.form.FormBottomBar
import com.kshavrin.mymoney.core.designsystem.icon.accountIcon
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.feature.dictionaries.R
import com.kshavrin.mymoney.feature.dictionaries.common.ACCOUNT_ICON_KEYS
import com.kshavrin.mymoney.feature.dictionaries.common.BlockedDeleteDialog
import com.kshavrin.mymoney.feature.dictionaries.common.ColorPicker
import com.kshavrin.mymoney.feature.dictionaries.common.IconPickerSheet

@Composable
fun AccountEditRoute(
    onBack: () -> Unit,
    viewModel: AccountEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.actions.collect { action ->
            when (action) {
                AccountEditAction.NavigateBack -> onBack()
            }
        }
    }
    AccountEditContent(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditContent(
    state: AccountEditState,
    onEvent: (AccountEditEvent) -> Unit,
) {
    var iconPickerVisible by remember { mutableStateOf(false) }
    var currencyMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isCreateMode) R.string.dictionaries_account_new
                            else R.string.dictionaries_account_edit,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onEvent(AccountEditEvent.BackClicked) }) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.dictionaries_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            FormBottomBar(
                text = stringResource(R.string.dictionaries_save),
                onSave = { onEvent(AccountEditEvent.SaveClicked) },
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
                value = state.name,
                onValueChange = { onEvent(AccountEditEvent.NameChanged(it)) },
                label = { Text(stringResource(R.string.dictionaries_field_name)) },
                isError = state.errorMessage == "name_required",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(R.string.dictionaries_field_currency),
                style = MaterialTheme.typography.titleSmall,
            )
            val selectedCurrency = state.availableCurrencies.firstOrNull { it.id == state.currencyId }
            OutlinedButton(
                onClick = { currencyMenuOpen = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = selectedCurrency?.let { "${it.code} (${it.symbol})" }
                        ?: stringResource(R.string.dictionaries_field_currency),
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
                expanded = currencyMenuOpen,
                onDismissRequest = { currencyMenuOpen = false },
            ) {
                state.availableCurrencies.forEach { currency ->
                    DropdownMenuItem(
                        text = { Text("${currency.code} (${currency.symbol})") },
                        onClick = {
                            onEvent(AccountEditEvent.CurrencyChanged(currency.id))
                            currencyMenuOpen = false
                        },
                    )
                }
            }

            OutlinedTextField(
                value = state.initialBalanceText,
                onValueChange = { onEvent(AccountEditEvent.InitialBalanceChanged(it)) },
                label = { Text(stringResource(R.string.dictionaries_field_initial_balance)) },
                isError = state.errorMessage == "balance_format",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(R.string.dictionaries_field_type),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    AccountType.Cash to R.string.dictionaries_type_cash,
                    AccountType.Card to R.string.dictionaries_type_card,
                    AccountType.Bank to R.string.dictionaries_type_bank,
                    AccountType.Savings to R.string.dictionaries_type_savings,
                ).forEach { (type, labelRes) ->
                    FilterChip(
                        selected = state.type == type,
                        onClick = { onEvent(AccountEditEvent.TypeChanged(type)) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }

            Text(
                text = stringResource(R.string.dictionaries_field_icon),
                style = MaterialTheme.typography.titleSmall,
            )
            OutlinedButton(
                onClick = { iconPickerVisible = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(state.iconKey)
            }

            Text(
                text = stringResource(R.string.dictionaries_field_color),
                style = MaterialTheme.typography.titleSmall,
            )
            ColorPicker(
                selectedHex = state.colorHex,
                onColorSelected = { onEvent(AccountEditEvent.ColorChanged(it)) },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.dictionaries_field_set_default))
                Switch(
                    checked = state.isDefault,
                    onCheckedChange = { onEvent(AccountEditEvent.IsDefaultChanged(it)) },
                )
            }

            if (state.errorMessage != null) {
                val errorText = when (state.errorMessage) {
                    "name_required" -> stringResource(R.string.dictionaries_error_name_required)
                    "currency_required" -> stringResource(R.string.dictionaries_error_currency_required)
                    "balance_format" -> stringResource(R.string.dictionaries_error_initial_balance_format)
                    else -> stringResource(R.string.dictionaries_error_generic)
                }
                Text(
                    text = errorText,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (!state.isCreateMode) {
                OutlinedButton(
                    onClick = { onEvent(AccountEditEvent.DeleteClicked) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) {
                    Text(stringResource(R.string.dictionaries_delete))
                }
            }
        }
    }

    if (iconPickerVisible) {
        IconPickerSheet(
            iconKeys = ACCOUNT_ICON_KEYS,
            selectedIconKey = state.iconKey,
            iconFor = { accountIcon(it) },
            onIconSelected = {
                onEvent(AccountEditEvent.IconChanged(it))
                iconPickerVisible = false
            },
            onDismiss = { iconPickerVisible = false },
        )
    }

    if (state.blockedDeleteCount != null) {
        BlockedDeleteDialog(
            entityName = state.name,
            transactionCount = state.blockedDeleteCount,
            onDismiss = { onEvent(AccountEditEvent.BlockedDeleteDismissed) },
        )
    }
}
