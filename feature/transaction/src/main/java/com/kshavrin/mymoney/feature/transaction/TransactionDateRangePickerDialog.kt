package com.kshavrin.mymoney.feature.transaction

import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDateRangePickerDialog(
    initialDate: LocalDate,
    onDatePicked: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialMillis,
        initialDisplayedMonthMillis = initialMillis,
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val startMillis = pickerState.selectedStartDateMillis
                val endMillis = pickerState.selectedEndDateMillis
                if (startMillis != null && endMillis != null) {
                    val picked = Instant.ofEpochMilli(startMillis).atZone(ZoneOffset.UTC).toLocalDate()
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
        DateRangePicker(state = pickerState)
    }
}
