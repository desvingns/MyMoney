package com.kshavrin.mymoney.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun MyMoneyNavHost(
    navController: NavHostController = rememberNavController(),
    shortcutDestination: String? = null,
) {
    NavHost(
        navController = navController,
        startDestination = Destinations.DECISION,
    ) {
        composable(Destinations.DECISION) {
            DecisionRouter(
                navController = navController,
                shortcutDestination = shortcutDestination,
            )
        }
        composable(Destinations.SPLASH) {
            com.kshavrin.mymoney.feature.onboarding.SplashScreen(
                onNavigateToOnboarding = {
                    navController.navigate(Destinations.ONBOARDING) {
                        popUpTo(Destinations.SPLASH) { inclusive = true }
                    }
                },
            )
        }
        composable(Destinations.ONBOARDING) {
            com.kshavrin.mymoney.feature.onboarding.OnboardingScreen(
                onComplete = {
                    navController.navigate(Destinations.DASHBOARD) {
                        popUpTo(Destinations.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(Destinations.DASHBOARD) {
            com.kshavrin.mymoney.feature.dashboard.DashboardRoute(
                onAction = { action ->
                    when (action) {
                        com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateAddExpense ->
                            navController.navigate(Destinations.ADD_EXPENSE)
                        com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateAddIncome ->
                            navController.navigate(Destinations.ADD_INCOME)
                        com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateTransfer ->
                            navController.navigate(Destinations.TRANSFER)
                        com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateSearch ->
                            navController.navigate(Destinations.TRANSACTIONS_LIST)
                        com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateSettings ->
                            navController.navigate(Destinations.SETTINGS)
                        com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateCategories ->
                            navController.navigate(Destinations.CATEGORIES_LIST)
                        com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateAccounts ->
                            navController.navigate(Destinations.ACCOUNTS_LIST)
                        com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateCurrencies ->
                            navController.navigate(Destinations.CURRENCIES_LIST)
                        is com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateTransactionsByAccount ->
                            navController.navigate("${Destinations.TRANSACTIONS_LIST}?accountId=${action.accountId}")
                        is com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateTransactionsByCategory ->
                            navController.navigate(
                                "${Destinations.TRANSACTIONS_LIST}?accountId=${action.accountId}&categoryId=${action.categoryId}",
                            )
                        com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateAbout ->
                            navController.navigate(Destinations.SETTINGS)
                    }
                },
            )
        }
        composable(
            route = "${Destinations.TRANSACTIONS_LIST}?accountId={accountId}&categoryId={categoryId}&from={from}&to={to}",
            arguments = listOf(
                navArgument("accountId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("categoryId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("from") { type = NavType.LongType; defaultValue = -1L },
                navArgument("to") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) {
            com.kshavrin.mymoney.feature.transactionslist.list.TransactionsListRoute(
                onOpenDetail = {},
                onSearch = {},
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destinations.ADD_EXPENSE) {
            com.kshavrin.mymoney.feature.transaction.expense.AddExpenseRoute(navController = navController)
        }
        composable(Destinations.ADD_INCOME) {
            com.kshavrin.mymoney.feature.transaction.income.AddIncomeRoute(navController = navController)
        }
        composable(Destinations.TRANSFER) {
            com.kshavrin.mymoney.feature.transaction.transfer.TransferRoute(navController = navController)
        }
        composable(
            route = "${Destinations.CATEGORY_PICKER}?kind={kind}",
            arguments = listOf(
                navArgument("kind") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) {
            com.kshavrin.mymoney.feature.transaction.picker.CategoryPickerRoute(navController = navController)
        }
        composable(
            route = "${Destinations.CURRENCY_RATE}?fromId={fromId}&toId={toId}",
            arguments = listOf(
                navArgument("fromId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("toId") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) {
            com.kshavrin.mymoney.feature.transaction.rate.CurrencyRateRoute(navController = navController)
        }
        composable(Destinations.CATEGORIES_LIST) {
            com.kshavrin.mymoney.feature.dictionaries.categories.CategoriesListRoute(
                onAdd = { navController.navigate("${Destinations.CATEGORY_EDIT}/-1") },
                onEdit = { id -> navController.navigate("${Destinations.CATEGORY_EDIT}/$id") },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "${Destinations.CATEGORY_EDIT}/{id}?kind={kind}&fromPicker={fromPicker}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                navArgument("kind") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("fromPicker") { type = NavType.BoolType; defaultValue = false },
            ),
        ) {
            com.kshavrin.mymoney.feature.dictionaries.categories.CategoryEditRoute(
                navController = navController,
            )
        }
        composable(Destinations.ACCOUNTS_LIST) {
            com.kshavrin.mymoney.feature.dictionaries.accounts.AccountsListRoute(
                onAdd = { navController.navigate("${Destinations.ACCOUNT_EDIT}/-1") },
                onEdit = { id -> navController.navigate("${Destinations.ACCOUNT_EDIT}/$id") },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "${Destinations.ACCOUNT_EDIT}/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
        ) {
            com.kshavrin.mymoney.feature.dictionaries.accounts.AccountEditRoute(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destinations.CURRENCIES_LIST) {
            com.kshavrin.mymoney.feature.dictionaries.currencies.CurrenciesListRoute(
                onAdd = { navController.navigate("${Destinations.CURRENCY_EDIT}/-1") },
                onEdit = { id -> navController.navigate("${Destinations.CURRENCY_EDIT}/$id") },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "${Destinations.CURRENCY_EDIT}/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
        ) {
            com.kshavrin.mymoney.feature.dictionaries.currencies.CurrencyEditRoute(
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun DecisionRouter(
    navController: NavHostController,
    shortcutDestination: String? = null,
) {
    val viewModel: DecisionRouterViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state) {
        when (state) {
            DecisionDestination.Pending -> Unit
            DecisionDestination.Splash -> navController.navigate(Destinations.SPLASH) {
                popUpTo(Destinations.DECISION) { inclusive = true }
            }
            DecisionDestination.Dashboard -> {
                navController.navigate(Destinations.DASHBOARD) {
                    popUpTo(Destinations.DECISION) { inclusive = true }
                }
                if (shortcutDestination != null) {
                    navController.navigate(shortcutDestination)
                }
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize())
}
