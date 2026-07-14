package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.ui.feedback.LocalHapticPlayer
import com.kshavrin.mymoney.core.ui.feedback.LocalSoundPlayer
import com.kshavrin.mymoney.core.ui.haptic.HapticKind
import com.kshavrin.mymoney.core.ui.sound.SoundKey
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.dashboard.DashboardEvent
import com.kshavrin.mymoney.feature.dashboard.DashboardSelection
import com.kshavrin.mymoney.feature.dashboard.DashboardState
import com.kshavrin.mymoney.feature.dashboard.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeftDrawerContent(
    state: DashboardState,
    onEvent: (DashboardEvent) -> Unit,
    onPickDateRangeClick: () -> Unit,
) {
    var accountsExpanded by remember { mutableStateOf(false) }
    val currentInterval = state.period as? Period.Interval
    var intervalExpanded by remember { mutableStateOf(currentInterval != null) }
    var intervalStart by remember { mutableStateOf(currentInterval?.start) }
    var intervalEnd by remember { mutableStateOf(currentInterval?.end) }
    var intervalEndpointPicker by remember { mutableStateOf<IntervalEndpoint?>(null) }
    val soundPlayer = LocalSoundPlayer.current
    val hapticPlayer = LocalHapticPlayer.current

    fun changePeriod(period: Period) {
        if (period !is Period.Interval) intervalExpanded = false
        soundPlayer.play(SoundKey.SWIPE)
        hapticPlayer.fire(HapticKind.SOFT)
        onEvent(DashboardEvent.PeriodChanged(period))
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.l),
    ) {
        // The account toggle must always be present so the user can open the account list from any
        // selection — including AllAccounts→Separate, where currentCurrency is null by design.
        val currency = state.currentCurrency
        AccountToggleHeaderRow(
            name = currency?.name ?: stringResource(R.string.left_drawer_all_accounts),
            code = currency?.code ?: stringResource(R.string.left_drawer_separate_currencies),
            icon = if (currency != null) Icons.Outlined.AttachMoney else Icons.Outlined.AccountBalanceWallet,
            expanded = accountsExpanded,
            onClick = { accountsExpanded = !accountsExpanded },
        )
        Spacer(modifier = Modifier.height(Spacing.m))
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                PeriodButton(
                    label = stringResource(R.string.period_day),
                    selected = state.period is Period.Day && (state.period as Period.Day).date == LocalDate.now(),
                    onClick = { changePeriod(Period.Day(LocalDate.now())) },
                )
                PeriodButton(
                    label = stringResource(R.string.period_week),
                    selected = state.period is Period.Week,
                    onClick = { changePeriod(Period.Week(LocalDate.now().with(DayOfWeek.MONDAY))) },
                )
                PeriodButton(
                    label = stringResource(R.string.period_month),
                    selected = state.period is Period.Month,
                    onClick = { changePeriod(Period.Month(YearMonth.now())) },
                )
                PeriodButton(
                    label = stringResource(R.string.period_year),
                    selected = state.period is Period.Year,
                    onClick = { changePeriod(Period.Year(LocalDate.now().year)) },
                )
                PeriodButton(
                    label = stringResource(R.string.period_all),
                    selected = state.period is Period.All,
                    onClick = { changePeriod(Period.All) },
                )
                PeriodButton(
                    label = stringResource(R.string.period_date_range),
                    selected = state.period is Period.Interval,
                    leadingIcon = Icons.Outlined.CalendarToday,
                    onClick = { intervalExpanded = true },
                )
                if (intervalExpanded) {
                    InlineIntervalEditor(
                        start = intervalStart,
                        end = intervalEnd,
                        onStartClick = { intervalEndpointPicker = IntervalEndpoint.Start },
                        onEndClick = { intervalEndpointPicker = IntervalEndpoint.End },
                        onApply = {
                            val start = intervalStart
                            val end = intervalEnd
                            if (start != null && end != null && !start.isAfter(end)) {
                                changePeriod(Period.Interval(start, end))
                            }
                        },
                    )
                }
                PeriodButton(
                    label = stringResource(R.string.period_pick_a_date),
                    selected = state.period is Period.CustomRange,
                    leadingIcon = Icons.Outlined.Event,
                    onClick = {
                        intervalExpanded = false
                        onPickDateRangeClick()
                    },
                )
            }
            if (accountsExpanded) {
                AccountDropdown(
                    accounts = state.accounts,
                    currencies = state.currencies,
                    selection = state.dashboardSelection,
                    onAllAccountsClick = {
                        accountsExpanded = false
                        onEvent(DashboardEvent.AllAccountsSelected)
                    },
                    onAccountClick = { accountId ->
                        accountsExpanded = false
                        onEvent(DashboardEvent.AccountSelected(accountId))
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .zIndex(1f),
                )
            }
        }
    }

    val endpoint = intervalEndpointPicker
    if (endpoint != null) {
        val initialDate =
            when (endpoint) {
                IntervalEndpoint.Start -> intervalStart ?: intervalEnd ?: LocalDate.now()
                IntervalEndpoint.End -> intervalEnd ?: intervalStart ?: LocalDate.now()
            }
        val pickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = localDateToMaterialPickerUtcMillis(initialDate),
            )
        DatePickerDialog(
            onDismissRequest = { intervalEndpointPicker = null },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedDateMillis != null,
                    onClick = {
                        pickerState.selectedDateMillis?.let { selectedMillis ->
                            val selectedDate = materialPickerUtcMillisToLocalDate(selectedMillis)
                            when (endpoint) {
                                IntervalEndpoint.Start -> intervalStart = selectedDate
                                IntervalEndpoint.End -> intervalEnd = selectedDate
                            }
                            intervalEndpointPicker = null
                        }
                    },
                ) {
                    Text(stringResource(R.string.period_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { intervalEndpointPicker = null }) {
                    Text(stringResource(R.string.period_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun InlineIntervalEditor(
    start: LocalDate?,
    end: LocalDate?,
    onStartClick: () -> Unit,
    onEndClick: () -> Unit,
    onApply: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val formatter =
        remember(locale) {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        }
    val notSelected = stringResource(R.string.period_interval_not_selected)
    val startValue = start?.format(formatter) ?: notSelected
    val endValue = end?.format(formatter) ?: notSelected
    val invalidRange = start != null && end != null && start.isAfter(end)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.m, vertical = Spacing.s),
    ) {
        IntervalEndpointButton(
            label = stringResource(R.string.period_interval_start),
            value = startValue,
            contentDescription = stringResource(R.string.period_interval_start_cd, startValue),
            onClick = onStartClick,
        )
        Spacer(modifier = Modifier.height(Spacing.s))
        IntervalEndpointButton(
            label = stringResource(R.string.period_interval_end),
            value = endValue,
            contentDescription = stringResource(R.string.period_interval_end_cd, endValue),
            onClick = onEndClick,
        )
        if (invalidRange) {
            Text(
                text = stringResource(R.string.period_interval_invalid),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Spacing.s),
            )
        }
        TextButton(
            enabled = start != null && end != null && !invalidRange,
            onClick = onApply,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.period_apply))
        }
    }
}

@Composable
private fun IntervalEndpointButton(
    label: String,
    value: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { this.contentDescription = contentDescription },
    ) {
        Text(stringResource(R.string.period_interval_value, label, value))
    }
}

@Composable
private fun AccountDropdown(
    accounts: List<Account>,
    currencies: List<Currency>,
    selection: DashboardSelection?,
    onAllAccountsClick: () -> Unit,
    onAccountClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 6.dp,
    ) {
        Column {
            AccountDropdownRow(
                label = stringResource(R.string.left_drawer_all_accounts),
                subtitle = stringResource(R.string.left_drawer_all_currencies),
                selected = selection is DashboardSelection.AllAccounts,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.AccountBalanceWallet,
                        contentDescription = null,
                    )
                },
                onClick = onAllAccountsClick,
            )
            accounts.forEachIndexed { index, account ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AccountDropdownRow(
                    label = account.name,
                    subtitle = currencies.firstOrNull { it.id == account.currencyId }?.code,
                    selected = (selection as? DashboardSelection.SpecificAccount)?.account?.id == account.id,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.AccountBalanceWallet,
                            contentDescription = null,
                        )
                    },
                    onClick = { onAccountClick(account.id) },
                )
                if (index == accounts.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun AccountToggleHeaderRow(
    name: String,
    code: String,
    icon: ImageVector,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val rowDescription = stringResource(R.string.dashboard_account_toggle_cd, name, code)
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(drawerRowShape)
                .semantics(mergeDescendants = true) { contentDescription = rowDescription }
                .clickable(onClick = onClick),
        shape = drawerRowShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.width(Spacing.m))
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun AccountDropdownRow(
    label: String,
    subtitle: String?,
    selected: Boolean,
    leadingIcon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val backgroundColor =
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
    val rowDescription =
        if (subtitle.isNullOrBlank()) {
            stringResource(R.string.dashboard_account_option_cd, label)
        } else {
            stringResource(R.string.dashboard_account_option_with_subtitle_cd, label, subtitle)
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = rowDescription
                    this.selected = selected
                }
                .padding(horizontal = Spacing.m, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.secondary,
        ) {
            Row(
                modifier = Modifier.size(44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                leadingIcon()
            }
        }
        Spacer(modifier = Modifier.width(Spacing.m))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DrawerOutlinedRow(
    label: String,
    subtitle: String? = null,
    selected: Boolean,
    leadingIcon: @Composable () -> Unit,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val backgroundColor =
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    val rowDescription = stringResource(R.string.dashboard_drawer_option_cd, label)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xs)
                .clip(drawerRowShape)
                .background(backgroundColor)
                .border(1.dp, MaterialTheme.colorScheme.outline, drawerRowShape)
                .clickable(enabled = enabled, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = rowDescription
                    this.selected = selected
                }
                .padding(horizontal = Spacing.m, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color =
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            contentColor =
                if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.secondary
                },
        ) {
            Row(
                modifier = Modifier.size(40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                leadingIcon()
            }
        }
        Spacer(modifier = Modifier.width(Spacing.m))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PeriodButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null,
) {
    val backgroundColor =
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    val rowDescription = stringResource(R.string.dashboard_drawer_option_cd, label)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(vertical = Spacing.xs)
                .clip(drawerRowShape)
                .background(backgroundColor)
                .border(1.dp, MaterialTheme.colorScheme.outline, drawerRowShape)
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = rowDescription
                    this.selected = selected
                }
                .padding(horizontal = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
            )
            Spacer(modifier = Modifier.width(Spacing.s))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
        )
    }
}

private val drawerRowShape = RoundedCornerShape(6.dp)

private enum class IntervalEndpoint {
    Start,
    End,
}
