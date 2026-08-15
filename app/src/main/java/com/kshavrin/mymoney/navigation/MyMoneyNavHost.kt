package com.kshavrin.mymoney.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kshavrin.mymoney.core.ui.navigation.Destinations
import com.kshavrin.mymoney.core.ui.navigation.PaywallEntryPoint

@Composable
fun MyMoneyNavHost(
    navController: NavHostController = rememberNavController(),
    shortcutDestination: ShortcutDestination? = null,
    openPaywall: Boolean = false,
) {
    NavHost(
        navController = navController,
        startDestination = Destinations.Decision,
    ) {
        composable<Destinations.Decision> {
            DecisionRouter(
                navController = navController,
                shortcutDestination = shortcutDestination,
                openPaywall = openPaywall,
            )
        }
        composable<Destinations.Splash> {
            com.kshavrin.mymoney.feature.onboarding.SplashScreen(
                onNavigateToOnboarding = {
                    if (com.kshavrin.mymoney.BuildConfig.SHOW_ONBOARDING) {
                        navController.navigate(Destinations.Onboarding) {
                            popUpTo<Destinations.Splash> { inclusive = true }
                        }
                    } else {
                        navController.navigate(Destinations.Dashboard) {
                            popUpTo<Destinations.Splash> { inclusive = true }
                        }
                    }
                },
            )
        }
        composable<Destinations.Onboarding> {
            com.kshavrin.mymoney.feature.onboarding.OnboardingScreen(
                onComplete = {
                    navController.navigate(Destinations.Dashboard) {
                        popUpTo<Destinations.Onboarding> { inclusive = true }
                    }
                },
            )
        }
        composable<Destinations.Dashboard> {
            var searchOverlayOpen by rememberSaveable { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxSize()) {
                com.kshavrin.mymoney.feature.dashboard.DashboardRoute(
                    onAction = { action ->
                        when (action) {
                            com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateAddExpense ->
                                navController.navigate(Destinations.AddExpense)
                            com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateAddIncome ->
                                navController.navigate(Destinations.AddIncome)
                            com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateTransfer ->
                                navController.navigate(Destinations.Transfer)
                            com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateSearch ->
                                searchOverlayOpen = true
                            com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateSettings ->
                                navController.navigate(Destinations.Settings)
                            com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateCategories ->
                                navController.navigate(Destinations.CategoriesList)
                            com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateAccounts ->
                                navController.navigate(Destinations.AccountsList)
                            com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateFinancialGoals ->
                                navController.navigate(Destinations.FinancialGoals)
                            com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateCurrencies ->
                                navController.navigate(Destinations.CurrenciesList)
                            com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateSupport ->
                                navController.navigate(Destinations.Support)
                            com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateAbout ->
                                navController.navigate(Destinations.Settings)
                            is com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateToTransactionDetail ->
                                navController.navigate(Destinations.TransactionDetail(action.transactionId))
                            is com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateToTransactionsList ->
                                navController.navigate(transactionsListDestination(action))
                            else -> Unit
                        }
                    },
                )
                if (searchOverlayOpen) {
                    com.kshavrin.mymoney.feature.transactionslist.search.SearchRoute(
                        onOpenDetail = { id ->
                            searchOverlayOpen = false
                            navController.navigate(Destinations.TransactionDetail(id))
                        },
                        onBack = { searchOverlayOpen = false },
                        contextualOverlay = true,
                    )
                }
            }
        }
        composable<Destinations.Search> {
            com.kshavrin.mymoney.feature.transactionslist.search.SearchRoute(
                onOpenDetail = { id -> navController.navigate(Destinations.TransactionDetail(id)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<Destinations.TransactionsList> {
            com.kshavrin.mymoney.feature.transactionslist.list.TransactionsListRoute(
                onOpenDetail = { id -> navController.navigate(Destinations.TransactionDetail(id)) },
                onSearch = { navController.navigate(Destinations.Search) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<Destinations.TransactionDetail> { entry ->
            com.kshavrin.mymoney.feature.transactionslist.detail.TransactionDetailRoute(
                onBack = { navController.popBackStack() },
                backStackEntry = entry,
                onNavigateToCreateCategory = { kind ->
                    navController.navigate(
                        Destinations.CategoryEdit(
                            kind = kind,
                            fromPicker = true,
                        ),
                    )
                },
            )
        }
        composable<Destinations.AddExpense> { entry ->
            com.kshavrin.mymoney.feature.transaction.expense.AddExpenseRoute(
                navController = navController,
                backStackEntry = entry,
                onNavigateToCreateCategory = {
                    navController.navigate(
                        Destinations.CategoryEdit(
                            kind = com.kshavrin.mymoney.core.domain.model.CategoryKind.Expense.name,
                            fromPicker = true,
                        ),
                    )
                },
                onNavigateToIncome = {
                    navController.navigate(Destinations.AddIncome) {
                        popUpTo<Destinations.AddExpense> { inclusive = true }
                    }
                },
            )
        }
        composable<Destinations.AddIncome> { entry ->
            com.kshavrin.mymoney.feature.transaction.income.AddIncomeRoute(
                navController = navController,
                backStackEntry = entry,
                onNavigateToCreateCategory = {
                    navController.navigate(
                        Destinations.CategoryEdit(
                            kind = com.kshavrin.mymoney.core.domain.model.CategoryKind.Income.name,
                            fromPicker = true,
                        ),
                    )
                },
                onNavigateToExpense = {
                    navController.navigate(Destinations.AddExpense) {
                        popUpTo<Destinations.AddIncome> { inclusive = true }
                    }
                },
            )
        }
        composable<Destinations.Transfer> { entry ->
            com.kshavrin.mymoney.feature.transaction.transfer.TransferRoute(
                navController = navController,
                backStackEntry = entry,
                onNavigateToRateSetup = { fromId, toId ->
                    navController.navigate(Destinations.CurrencyRate(fromId, toId))
                },
            )
        }
        composable<Destinations.CurrencyRate> {
            com.kshavrin.mymoney.feature.transaction.rate
                .CurrencyRateRoute(navController = navController)
        }
        composable<Destinations.CategoriesList> {
            com.kshavrin.mymoney.feature.dictionaries.categories.CategoriesListRoute(
                onAdd = { navController.navigate(Destinations.CategoryEdit()) },
                onEdit = { id -> navController.navigate(Destinations.CategoryEdit(id)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<Destinations.CategoryEdit> {
            com.kshavrin.mymoney.feature.dictionaries.categories.CategoryEditRoute(
                navController = navController,
            )
        }
        composable<Destinations.AccountsList> {
            com.kshavrin.mymoney.feature.dictionaries.accounts.AccountsListRoute(
                onAdd = { navController.navigate(Destinations.AccountEdit()) },
                onEdit = { id -> navController.navigate(Destinations.AccountEdit(id)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<Destinations.AccountEdit> {
            com.kshavrin.mymoney.feature.dictionaries.accounts.AccountEditRoute(
                onBack = { navController.popBackStack() },
            )
        }
        composable<Destinations.FinancialGoals> {
            com.kshavrin.mymoney.feature.dictionaries.goals.GoalsListRoute(
                onAdd = { navController.navigate(Destinations.FinancialGoalEdit()) },
                onEdit = { id -> navController.navigate(Destinations.FinancialGoalEdit(id)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<Destinations.FinancialGoalEdit> {
            com.kshavrin.mymoney.feature.dictionaries.goals.GoalEditRoute(
                onBack = { navController.popBackStack() },
            )
        }
        composable<Destinations.CurrenciesList> {
            com.kshavrin.mymoney.feature.dictionaries.currencies.CurrenciesListRoute(
                onAdd = { navController.navigate(Destinations.CurrencyEdit()) },
                onEdit = { id -> navController.navigate(Destinations.CurrencyEdit(id)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<Destinations.CurrencyEdit> {
            com.kshavrin.mymoney.feature.dictionaries.currencies.CurrencyEditRoute(
                onBack = { navController.popBackStack() },
            )
        }
        composable<Destinations.Settings> {
            com.kshavrin.mymoney.feature.settings.root.SettingsRootRoute(
                onOpenTheme = { navController.navigate(Destinations.SettingsTheme) },
                onOpenLanguage = { navController.navigate(Destinations.SettingsLanguage) },
                onOpenBackup = { navController.navigate(Destinations.SettingsBackup) },
                onOpenCloudSync = { navController.navigate(Destinations.CloudSync) },
                onOpenBiometricLock = { navController.navigate(Destinations.LockScreen) },
                onOpenAbout = { navController.navigate(Destinations.SettingsAbout) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<Destinations.Support> {
            com.kshavrin.mymoney.feature.support.SupportRoute(
                onBack = { navController.popBackStack() },
                plusSlot = {
                    com.kshavrin.mymoney.feature.support.paywall.PaywallSupportEntry(
                        onOpenPaywall = {
                            navController.navigate(
                                Destinations.Paywall(PaywallEntryPoint.SupportSection),
                            )
                        },
                    )
                },
            )
        }
        composable<Destinations.Paywall> { entry ->
            val route = entry.toRoute<Destinations.Paywall>()
            com.kshavrin.mymoney.feature.support.paywall.PaywallRoute(
                entryPoint = route.entryPoint,
                onBack = { navController.popBackStack() },
            )
        }
        composable<Destinations.CloudSync> {
            com.kshavrin.mymoney.feature.cloudsync.CloudSyncRoute(
                onBack = { navController.popBackStack() },
                onOpenPaywall = { entryPoint ->
                    navController.navigate(Destinations.Paywall(entryPoint))
                },
            )
        }
        composable<Destinations.LockScreen> {
            com.kshavrin.mymoney.feature.lockscreen.setup.BiometricSetupRoute(
                onBack = { navController.popBackStack() },
            )
        }
        composable<Destinations.SettingsTheme> {
            com.kshavrin.mymoney.feature.settings.theme.ThemeSettingsRoute(
                onBack = { navController.popBackStack() },
            )
        }
        composable<Destinations.SettingsLanguage> {
            com.kshavrin.mymoney.feature.settings.language.LanguageRoute(
                onBack = { navController.popBackStack() },
            )
        }
        composable<Destinations.SettingsAbout> {
            com.kshavrin.mymoney.feature.settings.about.AboutHelpRoute(
                versionName = com.kshavrin.mymoney.BuildConfig.VERSION_NAME,
                versionCode = com.kshavrin.mymoney.BuildConfig.VERSION_CODE,
                onOpenPrivacy = { navController.navigate(Destinations.SettingsAboutPrivacy) },
                onOpenHelp = { navController.navigate(Destinations.SettingsAboutHelp) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<Destinations.SettingsAboutPrivacy> {
            com.kshavrin.mymoney.feature.settings.about.PrivacyPolicyScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable<Destinations.SettingsAboutHelp> {
            com.kshavrin.mymoney.feature.settings.about.HelpScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable<Destinations.SettingsBackup> {
            com.kshavrin.mymoney.feature.settings.backup.BackupRestoreRoute(
                onBack = { navController.popBackStack() },
                onNavigateToImportWizard = { uri ->
                    navController.navigate(Destinations.ImportWizard(uri))
                },
            )
        }
        composable<Destinations.ImportWizard> {
            com.kshavrin.mymoney.feature.settings.importwizard.ImportWizardRoute(
                navController = navController,
            )
        }
    }
}

@Composable
private fun DecisionRouter(
    navController: NavHostController,
    shortcutDestination: ShortcutDestination? = null,
    openPaywall: Boolean = false,
) {
    val viewModel: DecisionRouterViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state) {
        when (state) {
            DecisionDestination.Pending -> Unit
            DecisionDestination.Splash ->
                navController.navigate(Destinations.Splash) {
                    popUpTo<Destinations.Decision> { inclusive = true }
                }
            DecisionDestination.Dashboard -> {
                navController.navigate(Destinations.Dashboard) {
                    popUpTo<Destinations.Decision> { inclusive = true }
                }
                when (shortcutDestination) {
                    ShortcutDestination.AddExpense -> navController.navigate(Destinations.AddExpense)
                    ShortcutDestination.AddIncome -> navController.navigate(Destinations.AddIncome)
                    ShortcutDestination.Transfer -> navController.navigate(Destinations.Transfer)
                    null -> Unit
                }
                if (openPaywall) {
                    navController.navigate(Destinations.Paywall(PaywallEntryPoint.SupportSection))
                }
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize())
}

private fun transactionsListDestination(
    action: com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateToTransactionsList,
): Destinations.TransactionsList =
    Destinations.TransactionsList(
        accountId = action.accountId ?: -1L,
        currencyId = action.currencyId ?: -1L,
        categoryId = action.categoryId ?: -1L,
        from = action.fromMillis,
        to = action.toMillis,
    )
