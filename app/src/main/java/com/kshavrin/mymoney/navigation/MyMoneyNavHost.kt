package com.kshavrin.mymoney.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun MyMoneyNavHost(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Destinations.DECISION,
    ) {
        composable(Destinations.DECISION) {
            DecisionRouter(navController = navController)
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
            PlaceholderScreen(text = "Dashboard placeholder — PHASE_08")
        }
    }
}

@Composable
private fun DecisionRouter(navController: NavHostController) {
    val viewModel: DecisionRouterViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state) {
        when (state) {
            DecisionDestination.Pending -> Unit
            DecisionDestination.Splash -> navController.navigate(Destinations.SPLASH) {
                popUpTo(Destinations.DECISION) { inclusive = true }
            }
            DecisionDestination.Dashboard -> navController.navigate(Destinations.DASHBOARD) {
                popUpTo(Destinations.DECISION) { inclusive = true }
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize())
}

@Composable
private fun PlaceholderScreen(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.titleLarge)
    }
}
