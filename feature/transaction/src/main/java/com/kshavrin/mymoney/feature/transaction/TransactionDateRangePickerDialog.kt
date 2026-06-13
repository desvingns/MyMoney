package com.kshavrin.mymoney.feature.transaction

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDatePickerDialog(
    initialDate: LocalDate,
    onDatePicked: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val pickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
        )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = pickerState.selectedDateMillis
                if (millis != null) {
                    val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    onDatePicked(picked)
                }
                onDismiss()
            }) {
                Text(stringResource(R.string.apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

@Composable
fun TransactionDateRangePickerDialog(
    initialDate: LocalDate,
    onDatePicked: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    TransactionDatePickerDialog(
        initialDate = initialDate,
        onDatePicked = onDatePicked,
        onDismiss = onDismiss,
    )
}
