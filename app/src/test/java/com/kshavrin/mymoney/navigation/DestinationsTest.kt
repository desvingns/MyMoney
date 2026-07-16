package com.kshavrin.mymoney.navigation

import com.kshavrin.mymoney.feature.dashboard.DashboardAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class DestinationsTest {
    @Test
    fun `destinations expose no string route constants`() {
        val stringFields =
            Destinations::class.java.declaredFields.filter { it.type == String::class.java }

        assertTrue("String route constants must not be reintroduced: $stringFields", stringFields.isEmpty())
    }

    @Test
    fun `argument destinations preserve their legacy default values`() {
        assertEquals(Destinations.CurrencyRate(-1L, -1L), Destinations.CurrencyRate())
        assertEquals(
            Destinations.TransactionsList(-1L, -1L, -1L, -1L, -1L),
            Destinations.TransactionsList(),
        )
        assertEquals(Destinations.CategoryEdit(-1L, null, false), Destinations.CategoryEdit())
        assertEquals(Destinations.AccountEdit(-1L), Destinations.AccountEdit())
        assertEquals(Destinations.FinancialGoalEdit(-1L), Destinations.FinancialGoalEdit())
        assertEquals(Destinations.CurrencyEdit(-1L), Destinations.CurrencyEdit())
        assertEquals(Destinations.ImportWizard(""), Destinations.ImportWizard())
    }

    @Test
    fun `transactions list destination preserves all dashboard filters`() {
        val destination =
            invokeTransactionsListDestination(
                DashboardAction.NavigateToTransactionsList(
                    accountId = 7L,
                    currencyId = null,
                    categoryId = 42L,
                    fromMillis = 1_746_489_600_000L,
                    toMillis = 1_777_939_199_999L,
                ),
            )

        assertEquals(
            Destinations.TransactionsList(
                accountId = 7L,
                currencyId = -1L,
                categoryId = 42L,
                from = 1_746_489_600_000L,
                to = 1_777_939_199_999L,
            ),
            destination,
        )
    }

    @Test
    fun `transactions list destination preserves all accounts currency mode`() {
        val destination =
            invokeTransactionsListDestination(
                DashboardAction.NavigateToTransactionsList(
                    accountId = null,
                    currencyId = 2L,
                    categoryId = null,
                    fromMillis = 0L,
                    toMillis = Long.MAX_VALUE,
                ),
            )

        assertEquals(
            Destinations.TransactionsList(
                accountId = -1L,
                currencyId = 2L,
                categoryId = -1L,
                from = 0L,
                to = Long.MAX_VALUE,
            ),
            destination,
        )
    }

    @Test
    fun `navigation graph registers every typed destination`() {
        val source = readProjectSource("app", "src", "main", "java", "com", "kshavrin", "mymoney", "navigation", "MyMoneyNavHost.kt")

        assertTrue(source.contains("startDestination = Destinations.Decision"))
        assertFalse(
            "MyMoneyNavHost must not reintroduce string navigation targets",
            source.contains("navController.navigate(\""),
        )
        assertFalse(
            "MyMoneyNavHost must not reintroduce string popUpTo targets",
            source.contains("popUpTo(\""),
        )
        listOf(
            "Decision",
            "Splash",
            "Onboarding",
            "Dashboard",
            "AddExpense",
            "AddIncome",
            "Transfer",
            "CurrencyRate",
            "TransactionsList",
            "TransactionDetail",
            "Search",
            "Settings",
            "SettingsTheme",
            "SettingsLanguage",
            "SettingsAbout",
            "SettingsAboutPrivacy",
            "SettingsAboutHelp",
            "SettingsBackup",
            "ImportWizard",
            "CategoriesList",
            "CategoryEdit",
            "AccountsList",
            "AccountEdit",
            "FinancialGoals",
            "FinancialGoalEdit",
            "CurrenciesList",
            "CurrencyEdit",
            "CloudSync",
            "LockScreen",
        ).forEach { destination ->
            assertTrue(
                "MyMoneyNavHost must register $destination as a typed composable",
                source.contains("composable<Destinations.$destination>"),
            )
        }
    }

    @Test
    fun `argument owning view models decode typed routes and navigation preserves replace semantics`() {
        listOf(
            "CurrencyRate" to
                arrayOf(
                    "feature", "transaction", "src", "main", "java", "com", "kshavrin", "mymoney",
                    "feature", "transaction", "rate", "CurrencyRateViewModel.kt",
                ),
            "TransactionsList" to
                arrayOf(
                    "feature", "transactionslist", "src", "main", "java", "com", "kshavrin", "mymoney",
                    "feature", "transactionslist", "list", "TransactionsListViewModel.kt",
                ),
            "TransactionDetail" to
                arrayOf(
                    "feature", "transactionslist", "src", "main", "java", "com", "kshavrin", "mymoney",
                    "feature", "transactionslist", "detail", "TransactionDetailViewModel.kt",
                ),
            "ImportWizard" to
                arrayOf(
                    "feature", "settings", "src", "main", "java", "com", "kshavrin", "mymoney",
                    "feature", "settings", "importwizard", "ImportWizardViewModel.kt",
                ),
            "CategoryEdit" to
                arrayOf(
                    "feature", "dictionaries", "src", "main", "java", "com", "kshavrin", "mymoney",
                    "feature", "dictionaries", "categories", "CategoryEditViewModel.kt",
                ),
            "AccountEdit" to
                arrayOf(
                    "feature", "dictionaries", "src", "main", "java", "com", "kshavrin", "mymoney",
                    "feature", "dictionaries", "accounts", "AccountEditViewModel.kt",
                ),
            "FinancialGoalEdit" to
                arrayOf(
                    "feature", "dictionaries", "src", "main", "java", "com", "kshavrin", "mymoney",
                    "feature", "dictionaries", "goals", "GoalEditViewModel.kt",
                ),
            "CurrencyEdit" to
                arrayOf(
                    "feature", "dictionaries", "src", "main", "java", "com", "kshavrin", "mymoney",
                    "feature", "dictionaries", "currencies", "CurrencyEditViewModel.kt",
                ),
        ).forEach { (destination, viewModelPath) ->
            val viewModelSource = readProjectSource(*viewModelPath)
            assertTrue(
                "The $destination owner must decode its typed route from SavedStateHandle",
                viewModelSource.contains("savedStateHandle.toRoute<Destinations.$destination>()"),
            )
        }
        val source = readProjectSource("app", "src", "main", "java", "com", "kshavrin", "mymoney", "navigation", "MyMoneyNavHost.kt")
        listOf(
            "popUpTo<Destinations.Splash> { inclusive = true }",
            "popUpTo<Destinations.Onboarding> { inclusive = true }",
            "popUpTo<Destinations.Decision> { inclusive = true }",
            "popUpTo<Destinations.AddExpense> { inclusive = true }",
            "popUpTo<Destinations.AddIncome> { inclusive = true }",
        ).forEach { typedPopUpTo ->
            assertTrue(
                "MyMoneyNavHost must keep typed replace semantics for $typedPopUpTo",
                source.contains(typedPopUpTo),
            )
        }
    }

    @Test
    fun `navigation migration keeps route construction out of feature routes`() {
        val expenseSource =
            readProjectSource(
                "feature",
                "transaction",
                "src",
                "main",
                "java",
                "com",
                "kshavrin",
                "mymoney",
                "feature",
                "transaction",
                "expense",
                "AddExpenseScreen.kt",
            )
        val incomeSource =
            readProjectSource(
                "feature",
                "transaction",
                "src",
                "main",
                "java",
                "com",
                "kshavrin",
                "mymoney",
                "feature",
                "transaction",
                "income",
                "AddIncomeScreen.kt",
            )
        val transferSource =
            readProjectSource(
                "feature",
                "transaction",
                "src",
                "main",
                "java",
                "com",
                "kshavrin",
                "mymoney",
                "feature",
                "transaction",
                "transfer",
                "TransferScreen.kt",
            )
        val detailSource =
            readProjectSource(
                "feature",
                "transactionslist",
                "src",
                "main",
                "java",
                "com",
                "kshavrin",
                "mymoney",
                "feature",
                "transactionslist",
                "detail",
                "TransactionDetailScreen.kt",
            )

        assertTrue(expenseSource.contains("onNavigateToCreateCategory: () -> Unit"))
        assertTrue(expenseSource.contains("onNavigateToIncome: () -> Unit"))
        assertTrue(incomeSource.contains("onNavigateToCreateCategory: () -> Unit"))
        assertTrue(incomeSource.contains("onNavigateToExpense: () -> Unit"))
        assertTrue(transferSource.contains("onNavigateToRateSetup: (fromId: Long, toId: Long) -> Unit"))
        assertTrue(detailSource.contains("onNavigateToCreateCategory: (String) -> Unit"))
        listOf(expenseSource, incomeSource, transferSource, detailSource).forEach { source ->
            assertFalse(
                "Feature route must delegate navigation instead of constructing a string route",
                source.contains("navController.navigate("),
            )
        }
    }

    @Test
    fun `app shortcuts retain typed dashboard to form outcome`() {
        val activitySource =
            readProjectSource("app", "src", "main", "java", "com", "kshavrin", "mymoney", "MainActivity.kt")
        val navHostSource =
            readProjectSource("app", "src", "main", "java", "com", "kshavrin", "mymoney", "navigation", "MyMoneyNavHost.kt")

        assertTrue(activitySource.contains("val shortcutDestination = resolveShortcutDestination(intent)"))
        assertTrue(activitySource.contains("MyMoneyNavHost(shortcutDestination = shortcutDestination)"))
        assertTrue(activitySource.contains("SHORTCUT_ADD_EXPENSE -> ShortcutDestination.AddExpense"))
        assertTrue(activitySource.contains("SHORTCUT_ADD_INCOME -> ShortcutDestination.AddIncome"))
        assertTrue(activitySource.contains("SHORTCUT_TRANSFER -> ShortcutDestination.Transfer"))
        assertTrue(activitySource.contains("LockOverlay(onUnlocked = lockController::markUnlocked)"))
        assertTrue(navHostSource.contains("navController.navigate(Destinations.Dashboard)"))
        assertTrue(navHostSource.contains("ShortcutDestination.AddExpense -> navController.navigate(Destinations.AddExpense)"))
        assertTrue(navHostSource.contains("ShortcutDestination.AddIncome -> navController.navigate(Destinations.AddIncome)"))
        assertTrue(navHostSource.contains("ShortcutDestination.Transfer -> navController.navigate(Destinations.Transfer)"))
    }

    private fun invokeTransactionsListDestination(
        action: DashboardAction.NavigateToTransactionsList,
    ): Destinations.TransactionsList {
        val method =
            Class
                .forName("com.kshavrin.mymoney.navigation.MyMoneyNavHostKt")
                .getDeclaredMethod(
                    "transactionsListDestination",
                    DashboardAction.NavigateToTransactionsList::class.java,
                )
        method.isAccessible = true
        return method.invoke(null, action) as Destinations.TransactionsList
    }

    private fun readProjectSource(vararg segments: String): String {
        val relativePath = segments.fold(Path.of("")) { path, segment -> path.resolve(segment) }
        val workingDirectory = Path.of("").toAbsolutePath()
        val source =
            generateSequence(workingDirectory) { it.parent }
                .take(3)
                .map { it.resolve(relativePath) }
                .firstOrNull(Files::exists)
                ?: error("Unable to locate $relativePath from $workingDirectory")
        return source.toFile().readText()
    }
}
