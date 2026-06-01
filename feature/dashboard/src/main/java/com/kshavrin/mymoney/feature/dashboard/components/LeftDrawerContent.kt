package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.ui.feedback.LocalHapticPlayer
import com.kshavrin.mymoney.core.ui.feedback.LocalSoundPlayer
import com.kshavrin.mymoney.core.ui.haptic.HapticKind
import com.kshavrin.mymoney.core.ui.sound.SoundKey
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.dashboard.DashboardEvent
import com.kshavrin.mymoney.feature.dashboard.DashboardState
import com.kshavrin.mymoney.feature.dashboard.R
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeftDrawerContent(
    state: DashboardState,
    onEvent: (DashboardEvent) -> Unit,
) {
    var accountsExpanded by remember { mutableStateOf(false) }
    var showRangePicker by remember { mutableStateOf(false) }
    val soundPlayer = LocalSoundPlayer.current
    val hapticPlayer = LocalHapticPlayer.current

    fun changePeriod(period: Period) {
        soundPlayer.play(SoundKey.SWIPE)
        hapticPlayer.fire(HapticKind.SOFT)
        onEvent(DashboardEvent.PeriodChanged(period))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.l),
    ) {
        state.currentCurrency?.let { currency ->
            CurrencyHeaderRow(
                name = currency.name,
                code = currency.code,
                expanded = accountsExpanded,
                onClick = { accountsExpanded = !accountsExpanded },
            )
            Spacer(modifier = Modifier.height(Spacing.m))
        }
        if (accountsExpanded) {
            AccountDropdown(
                accounts = state.accounts,
                currencies = state.currencies,
                selectedAccountId = state.currentAccount?.id,
                onAccountClick = { accountId ->
                    accountsExpanded = false
                    onEvent(DashboardEvent.AccountChanged(accountId))
                },
            )
            Spacer(modifier = Modifier.height(Spacing.m))
        }
        PeriodButton(
            label = stringResource(R.string.period_day),
            selected = state.period is Period.Day,
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
            label = stringResource(R.string.period_pick_a_date),
            selected = state.period is Period.CustomRange,
            leadingIcon = Icons.Outlined.CalendarToday,
            onClick = { showRangePicker = true },
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
                        val start = Instant.ofEpochMilli(startMillis)
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                        val end = Instant.ofEpochMilli(endMillis)
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                        changePeriod(Period.CustomRange(start, end))
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
            }
        ) {
            DateRangePicker(state = pickerState)
        }
    }
}

@Composable
private fun AccountDropdown(
    accounts: List<Account>,
    currencies: List<Currency>,
    selectedAccountId: Long?,
    onAccountClick: (Long) -> Unit,
) {
    Column {
        DrawerOutlinedRow(
            label = stringResource(R.string.left_drawer_all_accounts),
            selected = false,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.AccountBalanceWallet,
                    contentDescription = null,
                )
            },
            enabled = false,
            onClick = {},
        )
        accounts.forEach { account ->
            AccountDrawerRow(
                account = account,
                currencyCode = currencies.firstOrNull { it.id == account.currencyId }?.code,
                selected = account.id == selectedAccountId,
                onClick = { onAccountClick(account.id) },
            )
        }
    }
}

@Composable
private fun CurrencyHeaderRow(
    name: String,
    code: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(drawerRowShape)
            .clickable(onClick = onClick),
        shape = drawerRowShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.AttachMoney,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(32.dp),
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
private fun AccountDrawerRow(
    account: Account,
    currencyCode: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DrawerOutlinedRow(
        label = account.name,
        subtitle = currencyCode,
        selected = selected,
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.AccountBalanceWallet,
                contentDescription = null,
            )
        },
        onClick = onClick,
    )
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
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs)
            .clip(drawerRowShape)
            .background(backgroundColor)
            .border(1.dp, MaterialTheme.colorScheme.outline, drawerRowShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics(mergeDescendants = true) { this.selected = selected }
            .padding(horizontal = Spacing.m, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (selected) {
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
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .padding(vertical = Spacing.xs)
            .clip(drawerRowShape)
            .background(backgroundColor)
            .border(1.dp, MaterialTheme.colorScheme.outline, drawerRowShape)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { this.selected = selected }
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
            style = MaterialTheme.typography.titleLarge,
            color = contentColor,
        )
    }
}

private val drawerRowShape = RoundedCornerShape(6.dp)
