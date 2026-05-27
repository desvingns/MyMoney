package com.kshavrin.mymoney.feature.transaction.transfer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.kshavrin.mymoney.core.designsystem.amountfield.AmountFieldEvent
import com.kshavrin.mymoney.core.designsystem.amountfield.AmountFieldSection
import com.kshavrin.mymoney.core.designsystem.amountfield.AmountFieldState
import com.kshavrin.mymoney.core.designsystem.keypad.KeypadEvent
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.ui.feedback.LocalHapticPlayer
import com.kshavrin.mymoney.core.ui.feedback.LocalSoundPlayer
import com.kshavrin.mymoney.core.ui.haptic.HapticKind
import com.kshavrin.mymoney.core.ui.sound.SoundKey
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.transaction.R
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun TransferRoute(
    navController: NavController,
    viewModel: TransferViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.actions.collect { action ->
            when (action) {
                TransferAction.NavigateBack -> navController.popBackStack()
                is TransferAction.NavigateToRateSetup ->
                    navController.navigate(
                        "transaction/currency_rate?fromId=${action.fromCurrencyId}&toId=${action.toCurrencyId}",
                    )
                is TransferAction.FireHaptic,
                is TransferAction.PlaySound -> Unit
            }
        }
    }
    TransferScreen(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(
    state: TransferState,
    onEvent: (TransferEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var datePickerVisible by remember { mutableStateOf(false) }
    var keypadVisible by remember { mutableStateOf(false) }
    val soundPlayer = LocalSoundPlayer.current
    val hapticPlayer = LocalHapticPlayer.current

    LaunchedEffect(state.savedSignal) {
        if (state.savedSignal > 0L) {
            soundPlayer.play(SoundKey.SAVE_OK)
            hapticPlayer.fire(HapticKind.HEAVY)
        }
    }

    val errorRes = state.errorBannerRes
    val errorMessage = errorRes?.let { stringResource(it) }
    LaunchedEffect(errorRes) {
        if (errorMessage != null) {
            soundPlayer.play(SoundKey.ERROR)
            hapticPlayer.fire(HapticKind.WARNING)
            snackbarHostState.showSnackbar(errorMessage)
            onEvent(TransferEvent.DismissError)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_transfer_title)) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(TransferEvent.BackClicked) }) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onEvent(TransferEvent.SaveClicked) },
                        enabled = isSaveEnabled(state),
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = stringResource(R.string.currency_rate_save),
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
            Card(modifier = Modifier.fillMaxWidth()) {
                AmountFieldSection(
                    state = AmountFieldState(
                        display = state.amountInput,
                        expression = state.expression,
                        currencyCode = state.sourceCurrency?.code,
                        note = state.note,
                        occurredAt = state.occurredAt,
                        accountChipLabel = buildAccountChipLabel(
                            state.sourceAccount?.name,
                            state.sourceCurrency?.code,
                        ),
                    ),
                    onEvent = { e -> dispatchAmountEvent(e, onEvent) { datePickerVisible = true } },
                    modifier = Modifier.padding(Spacing.m),
                    amountInputModifier = Modifier.clickable { keypadVisible = true },
                    showKeypad = keypadVisible,
                )
            }

            AccountDropdown(
                label = stringResource(R.string.source_label),
                selected = state.sourceAccount,
                options = state.accounts,
                onSelected = { onEvent(TransferEvent.SourceAccountChanged(it.id)) },
            )

            AccountDropdown(
                label = stringResource(R.string.target_label),
                selected = state.targetAccount,
                options = state.accounts,
                onSelected = { onEvent(TransferEvent.TargetAccountChanged(it.id)) },
            )

            if (state.sameAccountsError) {
                Text(
                    text = stringResource(R.string.accounts_have_to_be_different),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (showRatePanel(state)) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.m),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        Text(
                            text = stringResource(R.string.currency_rate),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            text = state.ratePreviewText,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        TextButton(onClick = { onEvent(TransferEvent.ChangeRateClicked) }) {
                            Text(stringResource(R.string.transfer_change_rate_cta))
                        }
                    }
                }
            }
        }
    }

    if (datePickerVisible) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.occurredAt.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { datePickerVisible = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onEvent(TransferEvent.DateChanged(picked))
                    }
                    datePickerVisible = false
                }) { Text(stringResource(R.string.pick_date)) }
            },
            dismissButton = {
                TextButton(onClick = { datePickerVisible = false }) {
                    Text(stringResource(R.string.dismiss))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun AccountDropdown(
    label: String,
    selected: Account?,
    options: List<Account>,
    onSelected: (Account) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
        )
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = selected?.name ?: label,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.name) },
                    onClick = {
                        onSelected(account)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun isSaveEnabled(state: TransferState): Boolean {
    if (state.isSaving) return false
    if (state.amount <= BigDecimal.ZERO) return false
    val source = state.sourceAccount ?: return false
    val target = state.targetAccount ?: return false
    if (source.id == target.id) return false
    val sourceCurrency = state.sourceCurrency ?: return false
    val targetCurrency = state.targetCurrency ?: return false
    if (sourceCurrency.id != targetCurrency.id && state.currentRate == null) return false
    return true
}

private fun showRatePanel(state: TransferState): Boolean {
    val source = state.sourceCurrency ?: return false
    val target = state.targetCurrency ?: return false
    return source.id != target.id && state.ratePreviewText.isNotBlank()
}

private fun buildAccountChipLabel(name: String?, code: String?): String {
    if (name == null) return ""
    return if (code != null) "$name · $code" else name
}

private fun dispatchAmountEvent(
    e: AmountFieldEvent,
    onEvent: (TransferEvent) -> Unit,
    onDateChipClicked: () -> Unit,
) {
    when (e) {
        is AmountFieldEvent.Keypad -> when (val k = e.event) {
            is KeypadEvent.Digit -> onEvent(TransferEvent.KeypadDigit(k.d))
            is KeypadEvent.Op -> onEvent(TransferEvent.KeypadOperator(k.op))
            KeypadEvent.Dot -> onEvent(TransferEvent.KeypadDot)
            KeypadEvent.Backspace -> onEvent(TransferEvent.KeypadBackspace)
            KeypadEvent.Equals -> onEvent(TransferEvent.KeypadEquals)
        }
        is AmountFieldEvent.NoteChanged -> onEvent(TransferEvent.NoteChanged(e.text))
        is AmountFieldEvent.DateChanged -> onEvent(TransferEvent.DateChanged(e.date))
        AmountFieldEvent.AccountChipClicked -> Unit
        AmountFieldEvent.DateChipClicked -> onDateChipClicked()
    }
}
