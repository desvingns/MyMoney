package com.kshavrin.mymoney.feature.transaction.transfer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import com.kshavrin.mymoney.core.designsystem.amountfield.AmountFieldEvent
import com.kshavrin.mymoney.core.designsystem.amountfield.AmountFieldSection
import com.kshavrin.mymoney.core.designsystem.amountfield.AmountFieldState
import com.kshavrin.mymoney.core.designsystem.dialog.RateConfirmDialog
import com.kshavrin.mymoney.core.designsystem.form.DateHeader
import com.kshavrin.mymoney.core.designsystem.icon.accountIcon
import com.kshavrin.mymoney.core.designsystem.keypad.Keypad
import com.kshavrin.mymoney.core.designsystem.keypad.KeypadEvent
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.ui.feedback.LocalHapticPlayer
import com.kshavrin.mymoney.core.ui.feedback.LocalSoundPlayer
import com.kshavrin.mymoney.core.ui.flow.CollectActions
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
    backStackEntry: NavBackStackEntry,
    onNavigateToRateSetup: (fromId: Long, toId: Long) -> Unit,
    viewModel: TransferViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // S27 writes the saved rate signal into THIS destination's NavBackStackEntry savedStateHandle
    // (its previousBackStackEntry), which is a different instance from the one Hilt injects into the
    // ViewModel. Observe it here on the entry and forward as an event so the form re-reads the rate.
    val pendingRate by backStackEntry.savedStateHandle
        .getStateFlow(TransferViewModel.KEY_PENDING_RATE, TransferViewModel.NO_PENDING_RATE)
        .collectAsState()
    LaunchedEffect(pendingRate) {
        if (pendingRate > 0.0) {
            viewModel.onEvent(TransferEvent.PendingRateResolved)
            backStackEntry.savedStateHandle[TransferViewModel.KEY_PENDING_RATE] =
                TransferViewModel.NO_PENDING_RATE
        }
    }

    CollectActions(flow = viewModel.actions, key = viewModel) { action ->
        when (action) {
            TransferAction.NavigateBack -> navController.popBackStack()
            is TransferAction.NavigateToRateSetup ->
                onNavigateToRateSetup(action.fromCurrencyId, action.toCurrencyId)
            // Dialog visibility is driven from state.rateDialogRow; this one-shot action marks the
            // moment the resolved rate is ready (G13).
            TransferAction.ShowRateDialog,
            is TransferAction.FireHaptic,
            is TransferAction.PlaySound,
            -> Unit
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
    val keypadSheetState = rememberModalBottomSheetState()
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
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { keypadVisible = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    Icons.Filled.Dialpad,
                    contentDescription = stringResource(R.string.transfer_open_keypad_cd),
                )
            }
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(Spacing.l)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            DateHeader(
                date = state.occurredAt,
                onClick = { datePickerVisible = true },
            )

            AmountFieldSection(
                state =
                    AmountFieldState(
                        display = state.amountInput,
                        expression = state.expression,
                        currencyCode = state.sourceCurrency?.code,
                        currencySymbol = state.sourceCurrency?.symbol,
                        note = state.note,
                        occurredAt = state.occurredAt,
                        accountChipLabel =
                            buildAccountChipLabel(
                                state.sourceAccount?.name,
                                state.sourceCurrency?.code,
                            ),
                    ),
                onEvent = { e -> dispatchAmountEvent(e, onEvent) { datePickerVisible = true } },
                showKeypad = false,
                showAccountDateRow = false,
            )

            TransferAccountSelectorStack(
                sourceAccount = state.sourceAccount,
                sourceCurrencyCode =
                    state.sourceCurrency?.code
                        ?: currencyCodeFor(state.sourceAccount, state.currencies),
                targetAccount = state.targetAccount,
                targetCurrencyCode =
                    state.targetCurrency?.code
                        ?: currencyCodeFor(state.targetAccount, state.currencies),
                sourcePlaceholder = stringResource(R.string.source_label),
                targetPlaceholder = stringResource(R.string.target_label),
                directionContentDescription = stringResource(R.string.transfer_direction_cd),
                options = state.accounts,
                currencies = state.currencies,
                onSourceSelected = { onEvent(TransferEvent.SourceAccountChanged(it.id)) },
                onTargetSelected = { onEvent(TransferEvent.TargetAccountChanged(it.id)) },
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
                        modifier =
                            Modifier
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

    if (keypadVisible) {
        ModalBottomSheet(
            onDismissRequest = { keypadVisible = false },
            sheetState = keypadSheetState,
        ) {
            Keypad(
                onEvent = { e ->
                    dispatchAmountEvent(AmountFieldEvent.Keypad(e), onEvent) { datePickerVisible = true }
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(Spacing.m),
            )
        }
    }

    if (datePickerVisible) {
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    state.occurredAt
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant()
                        .toEpochMilli(),
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

    val rateRow = state.rateDialogRow
    if (rateRow != null) {
        RateConfirmDialog(
            rows = listOf(rateRow),
            onConfirm = { resolved ->
                resolved[0]?.let { onEvent(TransferEvent.RateDialogConfirmed(it)) }
            },
            onDismiss = { onEvent(TransferEvent.RateDialogDismissed) },
        )
    }
}

@Composable
private fun TransferAccountSelectorStack(
    sourceAccount: Account?,
    sourceCurrencyCode: String?,
    targetAccount: Account?,
    targetCurrencyCode: String?,
    sourcePlaceholder: String,
    targetPlaceholder: String,
    directionContentDescription: String,
    options: List<Account>,
    currencies: List<Currency>,
    onSourceSelected: (Account) -> Unit,
    onTargetSelected: (Account) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        AccountSelectorRow(
            account = sourceAccount,
            currencyCode = sourceCurrencyCode,
            placeholder = sourcePlaceholder,
            options = options,
            currencies = currencies,
            onSelected = onSourceSelected,
        )
        Icon(
            imageVector = Icons.Filled.ArrowDownward,
            contentDescription = directionContentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(32.dp),
        )
        AccountSelectorRow(
            account = targetAccount,
            currencyCode = targetCurrencyCode,
            placeholder = targetPlaceholder,
            options = options,
            currencies = currencies,
            onSelected = onTargetSelected,
        )
    }
}

@Composable
private fun AccountSelectorRow(
    account: Account?,
    currencyCode: String?,
    placeholder: String,
    options: List<Account>,
    currencies: List<Currency>,
    onSelected: (Account) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectorShape = MaterialTheme.shapes.extraSmall
    val accentColor = account?.colorHex?.let(::parseAccountColor) ?: MaterialTheme.colorScheme.primary
    val selectorDescription =
        stringResource(
            R.string.transfer_account_selector_cd,
            placeholder,
            account?.name ?: placeholder,
        )
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .border(
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        shape = selectorShape,
                    ).background(MaterialTheme.colorScheme.surface, selectorShape)
                    .semantics { contentDescription = selectorDescription }
                    .clickable { expanded = true }
                    .padding(horizontal = Spacing.m, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Icon(
                imageVector = accountIcon(account?.iconKey),
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(36.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account?.name ?: placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (currencyCode != null) {
                    Text(
                        text = currencyCode,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        val code = currencyCodeFor(option, currencies)
                        Text(if (code != null) "${option.name} · $code" else option.name)
                    },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun parseAccountColor(hex: String): Color? =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()

private fun currencyCodeFor(
    account: Account?,
    currencies: List<Currency>,
): String? {
    if (account == null) return null
    return currencies.find { it.id == account.currencyId }?.code
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

private fun buildAccountChipLabel(
    name: String?,
    code: String?,
): String {
    if (name == null) return ""
    return if (code != null) "$name · $code" else name
}

private fun dispatchAmountEvent(
    e: AmountFieldEvent,
    onEvent: (TransferEvent) -> Unit,
    onDateChipClicked: () -> Unit,
) {
    when (e) {
        is AmountFieldEvent.Keypad ->
            when (val k = e.event) {
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
