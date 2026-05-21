package com.kshavrin.mymoney.feature.transaction.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kshavrin.mymoney.core.designsystem.amountinput.MonefyAmountInput
import com.kshavrin.mymoney.core.designsystem.keypad.KeypadEvent
import com.kshavrin.mymoney.core.designsystem.keypad.MonefyKeypad
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.transaction.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class AmountFieldState(
    val display: String,
    val expression: String,
    val currencyCode: String?,
    val note: String,
    val occurredAt: LocalDate,
    val accountChipLabel: String,
)

sealed interface AmountFieldEvent {
    data class Keypad(val event: KeypadEvent) : AmountFieldEvent
    data class NoteChanged(val text: String) : AmountFieldEvent
    data class DateChanged(val date: LocalDate) : AmountFieldEvent
    data object AccountChipClicked : AmountFieldEvent
    data object DateChipClicked : AmountFieldEvent
}

private val DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmountFieldSection(
    state: AmountFieldState,
    onEvent: (AmountFieldEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        MonefyAmountInput(
            display = state.display,
            expression = state.expression,
            currencyCode = state.currencyCode,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = { onEvent(AmountFieldEvent.AccountChipClicked) },
                label = { Text(state.accountChipLabel) },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.AccountBalanceWallet,
                        contentDescription = null,
                        modifier = Modifier.padding(2.dp),
                    )
                },
            )
            AssistChip(
                onClick = { onEvent(AmountFieldEvent.DateChipClicked) },
                label = { Text(state.occurredAt.format(DATE_FORMAT)) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.CalendarToday,
                        contentDescription = stringResource(R.string.pick_date),
                        modifier = Modifier.padding(2.dp),
                    )
                },
                colors = AssistChipDefaults.assistChipColors(),
            )
        }
        OutlinedTextField(
            value = state.note,
            onValueChange = { onEvent(AmountFieldEvent.NoteChanged(it)) },
            label = { Text(stringResource(R.string.note_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        MonefyKeypad(
            onEvent = { keypadEvent -> onEvent(AmountFieldEvent.Keypad(keypadEvent)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
