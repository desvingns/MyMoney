package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.dashboard.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodStrip(
    currentPeriod: Period,
    onPeriodChange: (Period) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRangePicker by remember { mutableStateOf(false) }

    Row(
        modifier =
            modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.l),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        PeriodChip(
            text = stringResource(R.string.period_today),
            selected = currentPeriod is Period.Day,
        ) { onPeriodChange(Period.Day(LocalDate.now())) }
        PeriodChip(
            text = stringResource(R.string.period_week),
            selected = currentPeriod is Period.Week,
        ) { onPeriodChange(Period.Week(LocalDate.now().with(DayOfWeek.MONDAY))) }
        PeriodChip(
            text = stringResource(R.string.period_month),
            selected = currentPeriod is Period.Month,
        ) { onPeriodChange(Period.Month(YearMonth.now())) }
        PeriodChip(
            text = stringResource(R.string.period_year),
            selected = currentPeriod is Period.Year,
        ) { onPeriodChange(Period.Year(LocalDate.now().year)) }
        PeriodChip(
            text = stringResource(R.string.period_all),
            selected = currentPeriod is Period.All,
        ) { onPeriodChange(Period.All) }
        AssistChip(
            onClick = { showRangePicker = true },
            label = { Text(stringResource(R.string.period_pick_a_date)) },
            colors = AssistChipDefaults.assistChipColors(),
            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
        )
    }

    if (showRangePicker) {
        val pickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showRangePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val startMillis = pickerState.selectedStartDateMillis
                    val endMillis = pickerState.selectedEndDateMillis
                    if (startMillis != null && endMillis != null) {
                        val start = materialPickerUtcMillisToLocalDate(startMillis)
                        val end = materialPickerUtcMillisToLocalDate(endMillis)
                        onPeriodChange(Period.CustomRange(start, end))
                    }
                    showRangePicker = false
                }) {
                    Text(stringResource(R.string.period_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRangePicker = false }) {
                    Text(stringResource(R.string.period_cancel))
                }
            },
        ) {
            DateRangePicker(state = pickerState)
        }
    }
}

@Composable
private fun PeriodChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        // Material3 Chip lays its content at content-width and ignores the incoming minWidth, so
        // defaultMinSize on the chip cannot reach the 48dp touch target for short labels. Pad the
        // single label Text (no wrapper layout) so the chip grows while onNodeWithText still
        // resolves to the merged chip node — the touch-target assertion measures the chip, not an
        // inner box.
        label = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        },
        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
    )
}
