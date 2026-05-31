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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.dashboard.DashboardEvent
import com.kshavrin.mymoney.feature.dashboard.DashboardState
import com.kshavrin.mymoney.feature.dashboard.R

@Composable
fun LeftDrawerContent(
    state: DashboardState,
    onEvent: (DashboardEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.l),
    ) {
        state.currentCurrency?.let { currency ->
            CurrencyHeaderRow(
                name = currency.name,
                code = currency.code,
            )
            Spacer(modifier = Modifier.height(Spacing.m))
        }
        Text(
            text = stringResource(R.string.left_drawer_accounts),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(Spacing.s))
        LazyColumn {
            items(items = state.accounts, key = { it.id }) { acc ->
                AccountDrawerRow(
                    account = acc,
                    selected = acc.id == state.currentAccount?.id,
                    onClick = { onEvent(DashboardEvent.AccountChanged(acc.id)) },
                )
            }
        }
        Spacer(modifier = Modifier.height(Spacing.m))
        DrawerOutlinedRow(
            label = stringResource(R.string.left_drawer_manage_accounts),
            selected = false,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = null,
                )
            },
            onClick = { onEvent(DashboardEvent.AccountsClicked) },
        )
    }
}

@Composable
private fun CurrencyHeaderRow(
    name: String,
    code: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
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
        }
    }
}

@Composable
private fun AccountDrawerRow(
    account: Account,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DrawerOutlinedRow(
        label = account.name,
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
    selected: Boolean,
    leadingIcon: @Composable () -> Unit,
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
            .clickable(onClick = onClick)
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
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
        )
    }
}

private val drawerRowShape = RoundedCornerShape(6.dp)
