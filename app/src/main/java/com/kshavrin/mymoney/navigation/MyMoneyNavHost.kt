package com.kshavrin.mymoney.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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
                    if (com.kshavrin.mymoney.BuildConfig.SHOW_ONBOARDING) {
                        navController.navigate(Destinations.ONBOARDING) {
                            popUpTo(Destinations.SPLASH) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Destinations.DASHBOARD) {
                            popUpTo(Destinations.SPLASH) { inclusive = true }
                        }
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
            var searchOverlayOpen by rememberSaveable { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxSize()) {
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
                                searchOverlayOpen = true
                            com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateSettings ->
                                navController.navigate(Destinations.SETTINGS)
                            com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateCategories ->
                                navController.navigate(Destinations.CATEGORIES_LIST)
                            com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateAccounts ->
                                navController.navigate(Destinations.ACCOUNTS_LIST)
                            com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateFinancialGoals ->
                                navController.navigate(Destinations.FINANCIAL_GOALS)
                            com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateCurrencies ->
                                navController.navigate(Destinations.CURRENCIES_LIST)
                            is com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateTransactionsByAccount ->
                                navController.navigate("${Destinations.TRANSACTIONS_LIST}?accountId=${action.accountId}")
                            is com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateTransactionsByCurrency ->
                                navController.navigate("${Destinations.TRANSACTIONS_LIST}?currencyId=${action.currencyId}")
                            is com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateTransactionsByCategory ->
                                navController.navigate(
                                    buildString {
                                        append("${Destinations.TRANSACTIONS_LIST}?")
                                        if (action.accountId != null) {
                                            append("accountId=${action.accountId}&")
                                        }
                                        append("currencyId=${action.currencyId}&categoryId=${action.categoryId}")
                                    },
                                )
                            com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateAbout ->
                                navController.navigate(Destinations.SETTINGS)
                        }
                    },
                )
                if (searchOverlayOpen) {
                    com.kshavrin.mymoney.feature.transactionslist.search.SearchRoute(
                        onOpenDetail = { id ->
                            searchOverlayOpen = false
                            navController.navigate("${Destinations.TRANSACTION_DETAIL}/$id")
                        },
                        onBack = { searchOverlayOpen = false },
                        contextualOverlay = true,
                    )
                }
            }
        }
        composable(
            route = "${Destinations.TRANSACTIONS_LIST}?accountId={accountId}&currencyId={currencyId}&categoryId={categoryId}&from={from}&to={to}",
            arguments = listOf(
                navArgument("accountId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("currencyId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("categoryId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("from") { type = NavType.LongType; defaultValue = -1L },
                navArgument("to") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) {
            com.kshavrin.mymoney.feature.transactionslist.list.TransactionsListRoute(
                onOpenDetail = { id -> navController.navigate("${Destinations.TRANSACTION_DETAIL}/$id") },
                onSearch = { navController.navigate(Destinations.SEARCH) },
                onTransfer = { navController.navigate(Destinations.TRANSFER) },
                onOverflow = { navController.navigate(Destinations.SETTINGS) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destinations.SEARCH) {
            com.kshavrin.mymoney.feature.transactionslist.search.SearchRoute(
                onOpenDetail = { id -> navController.navigate("${Destinations.TRANSACTION_DETAIL}/$id") },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "${Destinations.TRANSACTION_DETAIL}/{transactionId}",
            arguments = listOf(
                navArgument("transactionId") { type = NavType.LongType },
            ),
        ) { entry ->
            com.kshavrin.mymoney.feature.transactionslist.detail.TransactionDetailRoute(
                onBack = { navController.popBackStack() },
                navController = navController,
                backStackEntry = entry,
            )
        }
        composable(Destinations.ADD_EXPENSE) { entry ->
            com.kshavrin.mymoney.feature.transaction.expense.AddExpenseRoute(
                navController = navController,
                backStackEntry = entry,
            )
        }
        composable(Destinations.ADD_INCOME) { entry ->
            com.kshavrin.mymoney.feature.transaction.income.AddIncomeRoute(
                navController = navController,
                backStackEntry = entry,
            )
        }
        composable(Destinations.TRANSFER) { entry ->
            com.kshavrin.mymoney.feature.transaction.transfer.TransferRoute(
                navController = navController,
                backStackEntry = entry,
            )
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
        composable(Destinations.FINANCIAL_GOALS) {
            com.kshavrin.mymoney.feature.dictionaries.goals.GoalsListRoute(
                onAdd = { navController.navigate("${Destinations.FINANCIAL_GOAL_EDIT}/-1") },
                onEdit = { id -> navController.navigate("${Destinations.FINANCIAL_GOAL_EDIT}/$id") },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "${Destinations.FINANCIAL_GOAL_EDIT}/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
        ) {
            com.kshavrin.mymoney.feature.dictionaries.goals.GoalEditRoute(
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
        composable(Destinations.SETTINGS) {
            com.kshavrin.mymoney.feature.settings.root.SettingsRootRoute(
                onOpenTheme = { navController.navigate(Destinations.SETTINGS_THEME) },
                onOpenLanguage = { navController.navigate(Destinations.SETTINGS_LANGUAGE) },
                onOpenBackup = { navController.navigate(Destinations.SETTINGS_BACKUP) },
                onOpenCloudSync = { navController.navigate(Destinations.CLOUD_SYNC) },
                onOpenBiometricLock = { navController.navigate(Destinations.LOCK_SCREEN) },
                onOpenAbout = { navController.navigate(Destinations.SETTINGS_ABOUT) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destinations.CLOUD_SYNC) {
            com.kshavrin.mymoney.feature.cloudsync.CloudSyncRoute(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destinations.LOCK_SCREEN) {
            com.kshavrin.mymoney.feature.lockscreen.setup.BiometricSetupRoute(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destinations.SETTINGS_THEME) {
            com.kshavrin.mymoney.feature.settings.theme.ThemeSettingsRoute(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destinations.SETTINGS_LANGUAGE) {
            com.kshavrin.mymoney.feature.settings.language.LanguageRoute(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destinations.SETTINGS_ABOUT) {
            com.kshavrin.mymoney.feature.settings.about.AboutHelpRoute(
                versionName = com.kshavrin.mymoney.BuildConfig.VERSION_NAME,
                versionCode = com.kshavrin.mymoney.BuildConfig.VERSION_CODE,
                onOpenPrivacy = { navController.navigate(Destinations.SETTINGS_ABOUT_PRIVACY) },
                onOpenHelp = { navController.navigate(Destinations.SETTINGS_ABOUT_HELP) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destinations.SETTINGS_ABOUT_PRIVACY) {
            com.kshavrin.mymoney.feature.settings.about.PrivacyPolicyScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destinations.SETTINGS_ABOUT_HELP) {
            com.kshavrin.mymoney.feature.settings.about.HelpScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destinations.SETTINGS_BACKUP) {
            com.kshavrin.mymoney.feature.settings.backup.BackupRestoreRoute(
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
