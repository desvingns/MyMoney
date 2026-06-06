package com.kshavrin.mymoney.feature.transaction.income

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.kshavrin.mymoney.core.designsystem.form.TransactionFormCategory
import com.kshavrin.mymoney.core.designsystem.form.TransactionFormContent
import com.kshavrin.mymoney.core.designsystem.form.TransactionFormEvent
import com.kshavrin.mymoney.core.designsystem.form.TransactionFormState
import com.kshavrin.mymoney.core.designsystem.keypad.KeypadEvent
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.ui.feedback.LocalHapticPlayer
import com.kshavrin.mymoney.core.ui.feedback.LocalSoundPlayer
import com.kshavrin.mymoney.core.ui.haptic.HapticKind
import com.kshavrin.mymoney.core.ui.sound.SoundKey
import com.kshavrin.mymoney.feature.transaction.R
import com.kshavrin.mymoney.feature.transaction.TransactionDateRangePickerDialog
import java.math.BigDecimal

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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        },
    ) { innerPadding ->
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

    if (datePickerVisible) {
        TransactionDateRangePickerDialog(
            initialDate = state.occurredAt,
            onDatePicked = { onEvent(AddIncomeEvent.DateChanged(it)) },
            onDismiss = { datePickerVisible = false },
        )
    }
}

private fun AddIncomeState.toTransactionFormState(): TransactionFormState = TransactionFormState(
    amountInput = amountInput,
    expression = expression,
    currencyCode = currency?.code,
    currencySymbol = currency?.symbol,
    note = note,
    occurredAt = occurredAt,
    categories = categories.map { it.toTransactionFormCategory() },
    categoryStep = categoryStep,
    chooseCategoryEnabled = amount > BigDecimal.ZERO,
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
    onEvent: (AddIncomeEvent) -> Unit,
) {
    when (event) {
        is TransactionFormEvent.Keypad -> dispatchKeypadEvent(event.event, onEvent)
        is TransactionFormEvent.NoteChanged -> onEvent(AddIncomeEvent.NoteChanged(event.text))
        TransactionFormEvent.DateHeaderClicked -> onDateHeaderClick()
        TransactionFormEvent.SelectCategoryClicked -> onEvent(AddIncomeEvent.SelectCategoryClicked)
        TransactionFormEvent.BackToAmount -> onEvent(AddIncomeEvent.BackToAmount)
        TransactionFormEvent.AddCategoryClicked -> onEvent(AddIncomeEvent.AddCategoryClicked)
        is TransactionFormEvent.CategoryPicked -> onEvent(AddIncomeEvent.CategoryPicked(event.categoryId))
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
