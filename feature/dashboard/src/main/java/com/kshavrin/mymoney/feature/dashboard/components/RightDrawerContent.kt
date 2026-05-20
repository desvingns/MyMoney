package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.dashboard.DashboardEvent
import com.kshavrin.mymoney.feature.dashboard.R

@Composable
fun RightDrawerContent(onEvent: (DashboardEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.l),
    ) {
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.right_drawer_settings)) },
            selected = false,
            onClick = { onEvent(DashboardEvent.SettingsClicked) },
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.right_drawer_categories)) },
            selected = false,
            onClick = { onEvent(DashboardEvent.CategoriesClicked) },
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.right_drawer_accounts)) },
            selected = false,
            onClick = { onEvent(DashboardEvent.AccountsClicked) },
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.right_drawer_currencies)) },
            selected = false,
            onClick = { onEvent(DashboardEvent.CurrenciesClicked) },
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.right_drawer_about)) },
            selected = false,
            onClick = { onEvent(DashboardEvent.AboutClicked) },
        )
    }
}
