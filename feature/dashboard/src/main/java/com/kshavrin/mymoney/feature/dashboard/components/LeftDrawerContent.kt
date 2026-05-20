package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
        Text(
            text = stringResource(R.string.left_drawer_accounts),
            style = MaterialTheme.typography.titleMedium,
        )
        LazyColumn {
            items(items = state.accounts, key = { it.id }) { acc ->
                NavigationDrawerItem(
                    label = { Text(acc.name) },
                    selected = acc.id == state.currentAccount?.id,
                    onClick = { onEvent(DashboardEvent.AccountChanged(acc.id)) },
                    colors = NavigationDrawerItemDefaults.colors(),
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.m))
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.left_drawer_manage_accounts)) },
            selected = false,
            onClick = { onEvent(DashboardEvent.AccountsClicked) },
        )
    }
}
