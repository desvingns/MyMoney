package com.kshavrin.mymoney.navigation

import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.testing.TestNavHostController
import androidx.navigation.toRoute
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kshavrin.mymoney.core.ui.navigation.Destinations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TypedNavigationDeviceTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun typed_routes_decode_arguments_and_replace_intermediate_destination() {
        lateinit var navController: TestNavHostController
        var decodedTransactionsList: Destinations.TransactionsList? = null
        val transactionsList =
            Destinations.TransactionsList(
                accountId = 7L,
                currencyId = 2L,
                categoryId = 42L,
                from = 1_746_489_600_000L,
                to = 1_777_939_199_999L,
            )

        composeTestRule.setContent {
            val context = LocalContext.current
            navController =
                remember(context) {
                    TestNavHostController(context).apply {
                        navigatorProvider.addNavigator(ComposeNavigator())
                    }
                }
            NavHost(
                navController = navController,
                startDestination = Destinations.Dashboard,
            ) {
                composable<Destinations.Dashboard> {}
                composable<Destinations.TransactionsList> { entry ->
                    decodedTransactionsList = entry.toRoute<Destinations.TransactionsList>()
                }
                composable<Destinations.AddExpense> {}
                composable<Destinations.AddIncome> {}
            }
        }

        composeTestRule.runOnIdle {
            navController.navigate(transactionsList)
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            decodedTransactionsList == transactionsList
        }

        composeTestRule.runOnIdle {
            assertEquals(transactionsList, decodedTransactionsList)
            assertTrue(navController.popBackStack())
            assertEquals(
                Destinations.Dashboard,
                navController.currentBackStackEntry?.toRoute<Destinations.Dashboard>(),
            )

            navController.navigate(Destinations.AddExpense)
            navController.navigate(Destinations.AddIncome) {
                popUpTo<Destinations.AddExpense> { inclusive = true }
            }

            assertEquals(
                Destinations.AddIncome,
                navController.currentBackStackEntry?.toRoute<Destinations.AddIncome>(),
            )
            assertTrue(navController.popBackStack())
            assertEquals(
                Destinations.Dashboard,
                navController.currentBackStackEntry?.toRoute<Destinations.Dashboard>(),
            )
        }
    }
}
