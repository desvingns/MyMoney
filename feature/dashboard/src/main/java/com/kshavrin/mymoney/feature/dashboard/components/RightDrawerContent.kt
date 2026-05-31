package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.dashboard.DashboardEvent
import com.kshavrin.mymoney.feature.dashboard.R

@Composable
fun RightDrawerContent(onEvent: (DashboardEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        Spacer(modifier = Modifier.height(Spacing.l))
        RightDrawerItem(
            label = stringResource(R.string.right_drawer_categories),
            icon = Icons.Outlined.Category,
            onClick = { onEvent(DashboardEvent.CategoriesClicked) },
        )
        RightDrawerItem(
            label = stringResource(R.string.right_drawer_accounts),
            icon = Icons.Outlined.AccountBalanceWallet,
            onClick = { onEvent(DashboardEvent.AccountsClicked) },
        )
        RightDrawerItem(
            label = stringResource(R.string.right_drawer_currencies),
            icon = Icons.Outlined.Paid,
            onClick = { onEvent(DashboardEvent.CurrenciesClicked) },
        )
        RightDrawerItem(
            label = stringResource(R.string.right_drawer_settings),
            icon = Icons.Outlined.Settings,
            onClick = { onEvent(DashboardEvent.SettingsClicked) },
        )
        RightDrawerItem(
            label = stringResource(R.string.right_drawer_about),
            icon = Icons.Outlined.Info,
            onClick = { onEvent(DashboardEvent.AboutClicked) },
        )
    }
}

@Composable
private fun RightDrawerItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.s)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(72.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(56.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
