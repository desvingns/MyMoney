package com.kshavrin.mymoney.feature.dictionaries.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kshavrin.mymoney.core.designsystem.icon.goalIcon
import com.kshavrin.mymoney.core.domain.model.GoalStatus
import com.kshavrin.mymoney.core.domain.model.GoalVariant
import com.kshavrin.mymoney.core.ui.theme.goalCreditProjectionAmount
import com.kshavrin.mymoney.core.ui.theme.goalCreditProjectionContainer
import com.kshavrin.mymoney.core.ui.theme.goalCreditProjectionContent
import com.kshavrin.mymoney.core.ui.theme.goalCreditProjectionLabel
import com.kshavrin.mymoney.core.ui.theme.goalCreditUnderfundedContainer
import com.kshavrin.mymoney.core.ui.theme.goalCreditUnderfundedContent
import com.kshavrin.mymoney.core.ui.theme.goalFormReadOnlyContainer
import com.kshavrin.mymoney.feature.dictionaries.R
import com.kshavrin.mymoney.feature.dictionaries.common.GOAL_ICON_KEYS
import com.kshavrin.mymoney.feature.dictionaries.common.IconPickerSheet
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun GoalEditRoute(
    onBack: () -> Unit,
    viewModel: GoalEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.actions.collect { action ->
            when (action) {
                GoalEditAction.NavigateBack -> onBack()
            }
        }
    }
    GoalEditContent(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditContent(
    state: GoalEditState,
    onEvent: (GoalEditEvent) -> Unit,
) {
    var iconPickerVisible by remember { mutableStateOf(false) }
    var accountMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.goals_title)) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(GoalEditEvent.BackClicked) }) {
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
                value = state.name,
                onValueChange = { onEvent(GoalEditEvent.NameChanged(it)) },
                label = { Text(stringResource(R.string.goal_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(R.string.goal_icon),
                style = MaterialTheme.typography.titleSmall,
            )
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .semantics { contentDescription = state.iconKey }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { iconPickerVisible = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = goalIcon(state.iconKey),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.variant == GoalVariant.SAVINGS,
                    onClick = { onEvent(GoalEditEvent.VariantChanged(GoalVariant.SAVINGS)) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) {
                    Text(stringResource(R.string.goal_variant_savings))
                }
                SegmentedButton(
                    selected = state.variant == GoalVariant.CREDIT,
                    onClick = { onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT)) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) {
                    Text(stringResource(R.string.goal_variant_credit))
                }
            }

            val selectedAccount = state.accounts.firstOrNull { it.id == state.accountId }
            ExposedDropdownMenuBox(
                expanded = accountMenuOpen,
                onExpandedChange = { accountMenuOpen = it },
            ) {
                OutlinedTextField(
                    value = selectedAccount?.name.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.goal_account)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountMenuOpen)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = accountMenuOpen,
                    onDismissRequest = { accountMenuOpen = false },
                ) {
                    state.accounts.forEach { account ->
                        DropdownMenuItem(
                            text = { Text(account.name) },
                            onClick = {
                                onEvent(GoalEditEvent.AccountSelected(account.id))
                                accountMenuOpen = false
                            },
                        )
                    }
                }
            }

            Text(text = stringResource(R.string.goal_current_balance, state.currentBalanceFormatted))

            OutlinedTextField(
                value = state.startingCapital,
                onValueChange = { onEvent(GoalEditEvent.StartingCapitalChanged(it)) },
                label = { Text(stringResource(R.string.goal_starting_capital)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            state.capitalDelta?.let { delta ->
                val amount = state.capitalDeltaAmountFormatted.orEmpty()
                val text = when {
                    delta.signum() > 0 -> stringResource(R.string.goal_remaining_on_account, amount)
                    delta.signum() < 0 -> stringResource(R.string.goal_short_on_account, amount)
                    else -> stringResource(R.string.goal_capital_exact)
                }
                Text(text = text, style = MaterialTheme.typography.bodySmall)
            }

            OutlinedTextField(
                value = state.monthlyContribution,
                onValueChange = { onEvent(GoalEditEvent.MonthlyChanged(it)) },
                label = { Text(stringResource(R.string.goal_monthly_contribution)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.targetAmount,
                onValueChange = { onEvent(GoalEditEvent.TargetChanged(it)) },
                label = { Text(stringResource(R.string.goal_target_amount)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            state.savingsProjection?.let { projection ->
                val text = when (projection.status) {
                    GoalStatus.ALREADY_ACHIEVED -> stringResource(R.string.goal_already_achieved)
                    GoalStatus.UNREACHABLE -> stringResource(R.string.goal_unreachable)
                    GoalStatus.ON_TRACK -> {
                        val date = projection.achievementDate?.format(GOAL_DATE_FORMATTER).orEmpty()
                        stringResource(R.string.goal_achievement_date, date)
                    }
                }
                Text(text = text)
            }

            if (state.variant == GoalVariant.CREDIT) {
                CreditFields(state = state, onEvent = onEvent)
            }

            Button(
                onClick = { onEvent(GoalEditEvent.SaveClicked) },
                enabled = state.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Text(stringResource(R.string.goal_save))
            }
        }
    }

    if (iconPickerVisible) {
        IconPickerSheet(
            iconKeys = GOAL_ICON_KEYS,
            selectedIconKey = state.iconKey,
            iconFor = { goalIcon(it) },
            onIconSelected = {
                onEvent(GoalEditEvent.IconSelected(it))
                iconPickerVisible = false
            },
            onDismiss = { iconPickerVisible = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreditFields(
    state: GoalEditState,
    onEvent: (GoalEditEvent) -> Unit,
) {
    var datePickerVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = state.annualRatePercent,
        onValueChange = { onEvent(GoalEditEvent.RateChanged(it)) },
        label = { Text(stringResource(R.string.goal_interest_rate)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = state.termDate?.format(GOAL_DATE_FORMATTER).orEmpty(),
        onValueChange = {},
        readOnly = true,
        label = { Text(stringResource(R.string.goal_term_date)) },
        trailingIcon = {
            IconButton(onClick = { datePickerVisible = true }) {
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = stringResource(R.string.goal_term_date),
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { datePickerVisible = true },
    )

    state.loanProjection?.let { projection ->
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.goalFormReadOnlyContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.goal_monthly_payment),
                    style = MaterialTheme.typography.goalCreditProjectionLabel,
                )
                Text(
                    text = state.loanProjectionMonthlyPaymentFormatted.orEmpty(),
                    style = MaterialTheme.typography.goalCreditProjectionAmount,
                )
            }
        }

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.goalCreditProjectionContainer,
            contentColor = MaterialTheme.colorScheme.goalCreditProjectionContent,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.goal_total_interest,
                        state.loanProjectionTotalInterestFormatted.orEmpty(),
                    ),
                    style = MaterialTheme.typography.goalCreditProjectionLabel,
                )
                Text(
                    text = stringResource(
                        R.string.goal_total_paid,
                        state.loanProjectionTotalPaidFormatted.orEmpty(),
                    ),
                    style = MaterialTheme.typography.goalCreditProjectionLabel,
                )
                Text(
                    text = stringResource(R.string.goal_overpayment_note),
                    style = MaterialTheme.typography.goalCreditProjectionLabel,
                )
            }
        }

        if (projection.underfunded) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.goalCreditUnderfundedContainer,
                contentColor = MaterialTheme.colorScheme.goalCreditUnderfundedContent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.goal_underfunded),
                    style = MaterialTheme.typography.goalCreditProjectionLabel,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    if (datePickerVisible) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.termDate
                ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { datePickerVisible = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onEvent(
                            GoalEditEvent.TermDateChanged(
                                LocalDate.ofEpochDay(millis / 86_400_000L),
                            ),
                        )
                    }
                    datePickerVisible = false
                }) {
                    Text(stringResource(R.string.goal_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { datePickerVisible = false }) {
                    Text(stringResource(R.string.dictionaries_back))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private val GOAL_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
