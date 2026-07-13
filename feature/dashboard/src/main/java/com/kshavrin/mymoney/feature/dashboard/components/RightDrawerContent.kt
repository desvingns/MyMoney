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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.dashboard.DashboardEvent
import com.kshavrin.mymoney.feature.dashboard.R

@Composable
fun RightDrawerContent(onEvent: (DashboardEvent) -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                // The eight items overflow the drawer height on shorter screens; a scrollable
                // ancestor keeps every row's full 48dp touch target reachable (and stops ATF
                // flagging the bottom row, whose on-screen bounds would otherwise be clipped).
                .verticalScroll(rememberScrollState())
                .padding(Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Spacer(modifier = Modifier.height(Spacing.m))
        RightDrawerItem(
            label = stringResource(R.string.right_drawer_search),
            icon = Icons.Outlined.Search,
            onClick = { onEvent(DashboardEvent.SearchClicked) },
            testTag = RIGHT_DRAWER_SEARCH_TAG,
        )
        RightDrawerItem(
            label = stringResource(R.string.right_drawer_categories),
            icon = Icons.Outlined.Category,
            onClick = { onEvent(DashboardEvent.CategoriesClicked) },
            testTag = RIGHT_DRAWER_CATEGORIES_TAG,
        )
        RightDrawerItem(
            label = stringResource(R.string.right_drawer_accounts),
            icon = Icons.Outlined.AccountBalanceWallet,
            onClick = { onEvent(DashboardEvent.AccountsClicked) },
            testTag = RIGHT_DRAWER_ACCOUNTS_TAG,
        )
        RightDrawerItem(
            label = stringResource(R.string.right_drawer_financial_goals),
            icon = Icons.Outlined.Flag,
            onClick = { onEvent(DashboardEvent.FinancialGoalsClicked) },
            testTag = RIGHT_DRAWER_FINANCIAL_GOALS_TAG,
        )
        RightDrawerItem(
            label = stringResource(R.string.right_drawer_currencies),
            icon = Icons.Outlined.Paid,
            onClick = { onEvent(DashboardEvent.CurrenciesClicked) },
            testTag = RIGHT_DRAWER_CURRENCIES_TAG,
        )
        RightDrawerItem(
            label = stringResource(R.string.right_drawer_chart_settings),
            icon = Icons.AutoMirrored.Outlined.ShowChart,
            onClick = { onEvent(DashboardEvent.ChartSettingsClicked) },
            testTag = RIGHT_DRAWER_CHART_SETTINGS_TAG,
        )
        RightDrawerItem(
            label = stringResource(R.string.right_drawer_settings),
            icon = Icons.Outlined.Settings,
            onClick = { onEvent(DashboardEvent.SettingsClicked) },
            testTag = RIGHT_DRAWER_SETTINGS_TAG,
        )
        RightDrawerItem(
            label = stringResource(R.string.right_drawer_about),
            icon = Icons.Outlined.Info,
            onClick = { onEvent(DashboardEvent.AboutClicked) },
            testTag = RIGHT_DRAWER_ABOUT_TAG,
        )
    }
}

@Composable
private fun RightDrawerItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = Spacing.xs)
                .semantics(mergeDescendants = true) { contentDescription = label }
                .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(44.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

const val RIGHT_DRAWER_SEARCH_TAG = "right_drawer_search"
const val RIGHT_DRAWER_CATEGORIES_TAG = "right_drawer_categories"
const val RIGHT_DRAWER_ACCOUNTS_TAG = "right_drawer_accounts"
const val RIGHT_DRAWER_FINANCIAL_GOALS_TAG = "right_drawer_financial_goals"
const val RIGHT_DRAWER_CURRENCIES_TAG = "right_drawer_currencies"
const val RIGHT_DRAWER_SETTINGS_TAG = "right_drawer_settings"
const val RIGHT_DRAWER_CHART_SETTINGS_TAG = "right_drawer_chart_settings"
const val RIGHT_DRAWER_ABOUT_TAG = "right_drawer_about"
