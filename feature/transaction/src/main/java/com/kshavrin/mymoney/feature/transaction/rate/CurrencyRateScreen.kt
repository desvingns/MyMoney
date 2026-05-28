package com.kshavrin.mymoney.feature.transaction.rate

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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.transaction.R

@Composable
fun CurrencyRateRoute(
    navController: NavController,
    viewModel: CurrencyRateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.actions.collect { action ->
            when (action) {
                CurrencyRateAction.NavigateBack -> navController.popBackStack()
                is CurrencyRateAction.NavigateBackWithRate -> {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("pendingRate", action.rate)
                    navController.popBackStack()
                }
            }
        }
    }
    CurrencyRateScreen(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyRateScreen(
    state: CurrencyRateState,
    onEvent: (CurrencyRateEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val showRateError = state.rateInput.isNotBlank() && !state.isValid

    val errorRes = state.errorBannerRes
    val errorMessage = errorRes?.let { stringResource(it) }
    LaunchedEffect(errorRes) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            onEvent(CurrencyRateEvent.DismissError)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.currency_rate_title)) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(CurrencyRateEvent.BackClicked) }) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Spacing.l)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            CurrencyRow(
                label = stringResource(R.string.source_label),
                code = state.fromCurrency?.code ?: "?",
            )
            CurrencyRow(
                label = stringResource(R.string.target_label),
                code = state.toCurrency?.code ?: "?",
            )
            Text(
                text = buildPreview(state),
                style = MaterialTheme.typography.headlineSmall,
            )
            OutlinedTextField(
                value = state.rateInput,
                onValueChange = { onEvent(CurrencyRateEvent.RateInputChanged(it)) },
                label = { Text(stringResource(R.string.currency_rate)) },
                singleLine = true,
                isError = showRateError,
                supportingText = {
                    if (showRateError) {
                        Text(stringResource(R.string.currency_rate_invalid))
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onEvent(CurrencyRateEvent.SaveClicked) },
                enabled = state.isValid && !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.currency_rate_save))
            }
        }
    }
}

@Composable
private fun CurrencyRow(
    label: String,
    code: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = code, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun buildPreview(state: CurrencyRateState): String {
    val fromCode = state.fromCurrency?.code ?: "?"
    val toCode = state.toCurrency?.code ?: "?"
    val rateText = state.rateInput.ifBlank { "?" }
    return stringResource(R.string.currency_rate_pattern, fromCode, rateText, toCode)
}
