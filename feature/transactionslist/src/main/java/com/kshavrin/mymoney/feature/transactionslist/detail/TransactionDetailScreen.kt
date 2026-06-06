package com.kshavrin.mymoney.feature.transactionslist.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import com.kshavrin.mymoney.core.designsystem.amountfield.AmountFieldEvent
import com.kshavrin.mymoney.core.designsystem.amountfield.AmountFieldSection
import com.kshavrin.mymoney.core.designsystem.amountfield.AmountFieldState
import com.kshavrin.mymoney.core.designsystem.form.TransactionFormCategory
import com.kshavrin.mymoney.core.designsystem.form.TransactionFormContent
import com.kshavrin.mymoney.core.designsystem.form.TransactionFormEvent
import com.kshavrin.mymoney.core.designsystem.form.TransactionFormMode
import com.kshavrin.mymoney.core.designsystem.form.TransactionFormState
import com.kshavrin.mymoney.core.designsystem.keypad.KeypadEvent
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.transactionslist.R
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun TransactionDetailRoute(
    onBack: () -> Unit,
    navController: NavController,
    backStackEntry: NavBackStackEntry,
    viewModel: TransactionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val undoMessage = stringResource(R.string.transactions_list_delete_undo)
    val undoAction = stringResource(R.string.transactions_list_undo_action)

    val createdCategoryId by backStackEntry.savedStateHandle
        .getStateFlow(TransactionDetailViewModel.KEY_CREATED_CATEGORY_ID, -1L)
        .collectAsState()
    LaunchedEffect(createdCategoryId) {
        if (createdCategoryId != -1L) {
            viewModel.onEvent(TransactionDetailEvent.CategoryPicked(createdCategoryId))
            backStackEntry.savedStateHandle[TransactionDetailViewModel.KEY_CREATED_CATEGORY_ID] = -1L
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.actions.collect { action ->
            when (action) {
                TransactionDetailAction.NavigateBack -> onBack()
                is TransactionDetailAction.NavigateToCreateCategory ->
                    navController.navigate(
                        "dictionaries/categories/edit/-1?kind=${action.kind}&fromPicker=true",
                    )
                is TransactionDetailAction.ShowUndoSnackbar -> {
                    // AS-9: same 5s window as S12. Indefinite duration defers dismissal to the
                    // timeout so the soft-delete becomes final exactly after 5 seconds. We pop
                    // back to S12 only after the window resolves so the snackbar stays visible.
                    val result = withTimeoutOrNull(UNDO_WINDOW_MILLIS) {
                        snackbarHostState.showSnackbar(
                            message = undoMessage,
                            actionLabel = undoAction,
                            duration = SnackbarDuration.Indefinite,
                        )
                    }
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.onEvent(TransactionDetailEvent.UndoDeleteClicked(action.transactionId))
                    }
                    onBack()
                }
            }
        }
    }

    TransactionDetailContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailContent(
    state: TransactionDetailState,
    snackbarHostState: SnackbarHostState,
    onEvent: (TransactionDetailEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var datePickerVisible by remember { mutableStateOf(false) }

    val errorRes = state.errorBannerRes
    val errorMessage = errorRes?.let { stringResource(it) }
    LaunchedEffect(errorRes) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            onEvent(TransactionDetailEvent.DismissError)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes(state.kind))) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(TransactionDetailEvent.BackClicked) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.transactions_list_back),
                        )
                    }
                },
                actions = {
                    // The shared edit form renders its own in-form delete button for
                    // expense/income; the transfer branch still relies on this toolbar action.
                    if (state.isTransfer) {
                        IconButton(onClick = { onEvent(TransactionDetailEvent.DeleteClicked) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.detail_delete),
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.canSave) {
                FloatingActionButton(onClick = { onEvent(TransactionDetailEvent.SaveClicked) }) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = stringResource(R.string.detail_save),
                    )
                }
            }
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        },
    ) { innerPadding ->
        if (state.isTransfer) {
            TransferEditBody(
                state = state,
                onEvent = onEvent,
                onDatePickerRequested = { datePickerVisible = true },
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            TransactionFormContent(
                state = state.toTransactionFormState(),
                onEvent = { event ->
                    dispatchTransactionFormEvent(
                        event = event,
                        onDateHeaderClick = { datePickerVisible = true },
                        onEvent = onEvent,
                    )
                },
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    if (state.confirmDeleteVisible) {
        AlertDialog(
            onDismissRequest = { onEvent(TransactionDetailEvent.DismissDelete) },
            title = { Text(stringResource(R.string.delete_transaction_title)) },
            text = { Text(stringResource(R.string.delete_transaction_body)) },
            confirmButton = {
                TextButton(onClick = { onEvent(TransactionDetailEvent.ConfirmDelete) }) {
                    Text(stringResource(R.string.delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(TransactionDetailEvent.DismissDelete) }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (datePickerVisible) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.occurredAt
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { datePickerVisible = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onEvent(TransactionDetailEvent.DateChanged(picked))
                    }
                    datePickerVisible = false
                }) { Text(stringResource(R.string.detail_pick_date)) }
            },
            dismissButton = {
                TextButton(onClick = { datePickerVisible = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun TransferEditBody(
    state: TransactionDetailState,
    onEvent: (TransactionDetailEvent) -> Unit,
    onDatePickerRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.m)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            AmountFieldSection(
                state = AmountFieldState(
                    display = state.amountInput,
                    expression = state.expression,
                    currencyCode = state.currency?.code,
                    note = state.note,
                    occurredAt = state.occurredAt,
                    accountChipLabel = buildAccountChipLabel(
                        state.account?.name,
                        state.currency?.code,
                    ),
                ),
                onEvent = { e -> dispatchAmountEvent(e, onEvent, onDatePickerRequested) },
                modifier = Modifier.padding(Spacing.m),
            )
        }

        AccountDropdown(
            label = stringResource(R.string.detail_source_label),
            selected = state.account,
            options = state.accounts,
            onSelected = { onEvent(TransactionDetailEvent.AccountChanged(it.id)) },
        )
        AccountDropdown(
            label = stringResource(R.string.detail_target_label),
            selected = state.targetAccount,
            options = state.accounts,
            onSelected = { onEvent(TransactionDetailEvent.TargetAccountChanged(it.id)) },
        )
        if (state.isCrossCurrency) {
            OutlinedTextField(
                value = state.rateInput,
                onValueChange = { onEvent(TransactionDetailEvent.RateChanged(it)) },
                label = { Text(stringResource(R.string.detail_rate_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
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
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = selected?.name ?: label, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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

private fun titleRes(kind: TransactionKind): Int = when (kind) {
    TransactionKind.Expense -> R.string.detail_title_expense
    TransactionKind.Income -> R.string.detail_title_income
    TransactionKind.Transfer -> R.string.detail_title_transfer
}

private fun buildAccountChipLabel(name: String?, code: String?): String {
    if (name == null) return ""
    return if (code != null) "$name · $code" else name
}

private fun TransactionDetailState.toTransactionFormState(): TransactionFormState = TransactionFormState(
    amountInput = amountInput,
    expression = expression,
    currencyCode = currency?.code,
    currencySymbol = currency?.symbol,
    note = note,
    occurredAt = occurredAt,
    categories = categories.map { it.toTransactionFormCategory() },
    categoryStep = categoryStep,
    chooseCategoryEnabled = amount > BigDecimal.ZERO,
    mode = TransactionFormMode.Edit,
)

private fun Category.toTransactionFormCategory(): TransactionFormCategory = TransactionFormCategory(
    id = id,
    name = name,
    colorHex = colorHex,
    iconKey = iconKey,
)

private fun dispatchTransactionFormEvent(
    event: TransactionFormEvent,
    onDateHeaderClick: () -> Unit,
    onEvent: (TransactionDetailEvent) -> Unit,
) {
    when (event) {
        is TransactionFormEvent.Keypad -> dispatchKeypadEvent(event.event, onEvent)
        is TransactionFormEvent.NoteChanged -> onEvent(TransactionDetailEvent.NoteChanged(event.text))
        TransactionFormEvent.DateHeaderClicked -> onDateHeaderClick()
        TransactionFormEvent.SelectCategoryClicked -> onEvent(TransactionDetailEvent.SelectCategoryClicked)
        TransactionFormEvent.BackToAmount -> onEvent(TransactionDetailEvent.BackToAmount)
        TransactionFormEvent.AddCategoryClicked -> onEvent(TransactionDetailEvent.AddCategoryClicked)
        is TransactionFormEvent.CategoryPicked -> onEvent(TransactionDetailEvent.CategoryPicked(event.categoryId))
        TransactionFormEvent.DeleteClicked -> onEvent(TransactionDetailEvent.DeleteClicked)
    }
}

private fun dispatchKeypadEvent(
    k: KeypadEvent,
    onEvent: (TransactionDetailEvent) -> Unit,
) {
    when (k) {
        is KeypadEvent.Digit -> onEvent(TransactionDetailEvent.KeypadDigit(k.d))
        is KeypadEvent.Op -> onEvent(TransactionDetailEvent.KeypadOperator(k.op))
        KeypadEvent.Dot -> onEvent(TransactionDetailEvent.KeypadDot)
        KeypadEvent.Backspace -> onEvent(TransactionDetailEvent.KeypadBackspace)
        KeypadEvent.Equals -> onEvent(TransactionDetailEvent.KeypadEquals)
    }
}

private fun dispatchAmountEvent(
    e: AmountFieldEvent,
    onEvent: (TransactionDetailEvent) -> Unit,
    onDateChipClicked: () -> Unit,
) {
    when (e) {
        is AmountFieldEvent.Keypad -> when (val k = e.event) {
            is KeypadEvent.Digit -> onEvent(TransactionDetailEvent.KeypadDigit(k.d))
            is KeypadEvent.Op -> onEvent(TransactionDetailEvent.KeypadOperator(k.op))
            KeypadEvent.Dot -> onEvent(TransactionDetailEvent.KeypadDot)
            KeypadEvent.Backspace -> onEvent(TransactionDetailEvent.KeypadBackspace)
            KeypadEvent.Equals -> onEvent(TransactionDetailEvent.KeypadEquals)
        }
        is AmountFieldEvent.NoteChanged -> onEvent(TransactionDetailEvent.NoteChanged(e.text))
        is AmountFieldEvent.DateChanged -> onEvent(TransactionDetailEvent.DateChanged(e.date))
        AmountFieldEvent.AccountChipClicked -> Unit
        AmountFieldEvent.DateChipClicked -> onDateChipClicked()
    }
}

private const val UNDO_WINDOW_MILLIS = 5_000L
