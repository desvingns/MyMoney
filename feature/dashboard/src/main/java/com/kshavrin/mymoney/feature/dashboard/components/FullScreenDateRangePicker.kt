package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.dashboard.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FullScreenDateRangePicker(
    initialRange: Period.CustomRange?,
    onApply: (Period.CustomRange) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState =
        rememberDateRangePickerState(
            initialSelectedStartDateMillis =
                initialRange?.start?.let(::localDateToMaterialPickerUtcMillis),
            initialSelectedEndDateMillis =
                initialRange?.end?.let(::localDateToMaterialPickerUtcMillis),
        )
    val startMillis = pickerState.selectedStartDateMillis
    val endMillis = pickerState.selectedEndDateMillis
    val validRange = startMillis != null && endMillis != null && startMillis <= endMillis
    val dateRangePaneTitle = stringResource(R.string.period_date_range)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .semantics { paneTitle = dateRangePaneTitle },
            color = DatePickerDefaults.colors().containerColor,
        ) {
            DateRangePicker(
                state = pickerState,
                modifier = Modifier.fillMaxSize(),
                title = {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.l),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.period_cancel))
                        }
                        TextButton(
                            enabled = validRange,
                            onClick = {
                                if (startMillis != null && endMillis != null && startMillis <= endMillis) {
                                    onApply(
                                        Period.CustomRange(
                                            start = materialPickerUtcMillisToLocalDate(startMillis),
                                            end = materialPickerUtcMillisToLocalDate(endMillis),
                                        ),
                                    )
                                    onDismiss()
                                }
                            },
                        ) {
                            Text(stringResource(R.string.period_apply))
                        }
                    }
                },
                showModeToggle = false,
            )
        }
    }
}
