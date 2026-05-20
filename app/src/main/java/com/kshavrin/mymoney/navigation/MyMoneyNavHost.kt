package com.kshavrin.mymoney.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

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
            PlaceholderScreen(text = "Splash placeholder — PHASE_07 SPEC B")
        }
        composable(Destinations.ONBOARDING) {
            PlaceholderScreen(text = "Onboarding placeholder — PHASE_07 SPEC B")
        }
        composable(Destinations.DASHBOARD) {
            PlaceholderScreen(text = "Dashboard placeholder — PHASE_08")
        }
    }
}

@Composable
private fun DecisionRouter(navController: NavHostController) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val appSettings = EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppSettingsRepositoryEntryPoint::class.java,
        ).appSettingsRepository()
        val onboardingCompletedAt = appSettings.settings.first().onboardingCompletedAt
        val next = if (onboardingCompletedAt == null) Destinations.SPLASH else Destinations.DASHBOARD
        navController.navigate(next) {
            popUpTo(Destinations.DECISION) { inclusive = true }
        }
    }
    PlaceholderScreen(text = "")
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

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppSettingsRepositoryEntryPoint {
    fun appSettingsRepository(): AppSettingsRepository
}
