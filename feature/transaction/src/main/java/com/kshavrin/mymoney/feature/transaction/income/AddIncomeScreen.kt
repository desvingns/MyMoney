package com.kshavrin.mymoney.feature.transaction.income

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import com.kshavrin.mymoney.core.designsystem.amountfield.AmountFieldEvent
import com.kshavrin.mymoney.core.designsystem.amountfield.AmountFieldSection
import com.kshavrin.mymoney.core.designsystem.amountfield.AmountFieldState
import com.kshavrin.mymoney.core.designsystem.keypad.KeypadEvent
import com.kshavrin.mymoney.core.designsystem.keypad.MonefyKeypad
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.ui.feedback.LocalHapticPlayer
import com.kshavrin.mymoney.core.ui.feedback.LocalSoundPlayer
import com.kshavrin.mymoney.core.ui.haptic.HapticKind
import com.kshavrin.mymoney.core.ui.sound.SoundKey
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.transaction.R
import com.kshavrin.mymoney.feature.transaction.categorygrid.CategoryGrid
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun AddIncomeRoute(
    navController: NavController,
    backStackEntry: NavBackStackEntry,
    viewModel: AddIncomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    val createdCategoryId by backStackEntry.savedStateHandle
        .getStateFlow(AddIncomeViewModel.KEY_CREATED_CATEGORY_ID, -1L)
        .collectAsState()
    LaunchedEffect(createdCategoryId) {
        if (createdCategoryId != -1L) {
            viewModel.onEvent(AddIncomeEvent.CategoryPicked(createdCategoryId))
            backStackEntry.savedStateHandle[AddIncomeViewModel.KEY_CREATED_CATEGORY_ID] = -1L
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.actions.collect { action ->
            when (action) {
                AddIncomeAction.NavigateBack -> navController.popBackStack()
                AddIncomeAction.NavigateToCreateCategory ->
                    navController.navigate(
                        "dictionaries/categories/edit/-1?kind=${CategoryKind.Income.name}&fromPicker=true",
                    )
                AddIncomeAction.NavigateToExpenseForm -> {
                    navController.navigate("transaction/expense") {
                        popUpTo("transaction/income") { inclusive = true }
                    }
                }
                AddIncomeAction.ShowSavedConfetti,
                is AddIncomeAction.FireHaptic,
                is AddIncomeAction.PlaySound -> Unit
            }
        }
    }
    AddIncomeScreen(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddIncomeScreen(
    state: AddIncomeState,
    onEvent: (AddIncomeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var datePickerVisible by remember { mutableStateOf(false) }
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
            onEvent(AddIncomeEvent.DismissError)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_income_title)) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(AddIncomeEvent.BackClicked) }) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onEvent(AddIncomeEvent.SwapMode) }) {
                        Icon(
                            Icons.Filled.SwapHoriz,
                            contentDescription = stringResource(R.string.swap_mode),
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
                .padding(Spacing.m),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                AmountFieldSection(
                    state = AmountFieldState(
                        display = state.amountInput,
                        expression = state.expression,
                        currencyCode = state.currency?.code,
                        note = state.note,
                        occurredAt = state.occurredAt,
                        accountChipLabel = buildAccountChipLabel(state.account?.name, state.currency?.code),
                    ),
                    onEvent = { e -> dispatchAmountEvent(e, onEvent) { datePickerVisible = true } },
                    showKeypad = false,
                    amountInputModifier = Modifier.clickable { onEvent(AddIncomeEvent.AmountClicked) },
                    modifier = Modifier
                        .padding(Spacing.m),
                )
            }

            CategoryGrid(
                categories = state.categories,
                onCategoryClick = { onEvent(AddIncomeEvent.CategoryPicked(it)) },
                onAddClick = { onEvent(AddIncomeEvent.AddCategoryClicked) },
                modifier = Modifier
                    .weight(1f)
                    .padding(top = Spacing.m),
            )
        }
    }

    if (state.keypadVisible) {
        ModalBottomSheet(
            onDismissRequest = { onEvent(AddIncomeEvent.KeypadDismissed) },
            sheetState = keypadSheetState,
        ) {
            MonefyKeypad(
                onEvent = { e ->
                    dispatchAmountEvent(AmountFieldEvent.Keypad(e), onEvent) { datePickerVisible = true }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.m),
            )
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
                        onEvent(AddIncomeEvent.DateChanged(picked))
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

private fun buildAccountChipLabel(name: String?, code: String?): String {
    if (name == null) return ""
    return if (code != null) "$name · $code" else name
}

private fun dispatchAmountEvent(
    e: AmountFieldEvent,
    onEvent: (AddIncomeEvent) -> Unit,
    onDateChipClicked: () -> Unit,
) {
    when (e) {
        is AmountFieldEvent.Keypad -> dispatchKeypadEvent(e.event, onEvent)
        is AmountFieldEvent.NoteChanged -> onEvent(AddIncomeEvent.NoteChanged(e.text))
        is AmountFieldEvent.DateChanged -> onEvent(AddIncomeEvent.DateChanged(e.date))
        AmountFieldEvent.AccountChipClicked -> Unit
        AmountFieldEvent.DateChipClicked -> onDateChipClicked()
    }
}

private fun dispatchKeypadEvent(
    k: KeypadEvent,
    onEvent: (AddIncomeEvent) -> Unit,
) {
    when (k) {
        is KeypadEvent.Digit -> onEvent(AddIncomeEvent.KeypadDigit(k.d))
        is KeypadEvent.Op -> onEvent(AddIncomeEvent.KeypadOperator(k.op))
        KeypadEvent.Dot -> onEvent(AddIncomeEvent.KeypadDot)
        KeypadEvent.Backspace -> onEvent(AddIncomeEvent.KeypadBackspace)
        KeypadEvent.Equals -> onEvent(AddIncomeEvent.KeypadEquals)
    }
}
