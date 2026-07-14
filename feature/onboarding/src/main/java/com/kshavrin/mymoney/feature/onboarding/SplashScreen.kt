package com.kshavrin.mymoney.feature.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

const val SPLASH_LOGO_TAG = "splash_logo"

@Composable
fun SplashScreen(
    onNavigateToOnboarding: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.initialise()
    }

    LaunchedEffect(state.destination) {
        if (state.destination == SplashDestination.Onboarding) {
            onNavigateToOnboarding()
        }
    }

    SplashContent(
        seedFailed = state.seedFailed,
        onRetry = viewModel::retry,
    )
}

@Composable
fun SplashContent(
    seedFailed: Boolean = false,
    onRetry: () -> Unit = {},
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.onboarding_hero_1),
                contentDescription = null,
                modifier =
                    Modifier
                        .size(120.dp)
                        .testTag(SPLASH_LOGO_TAG),
            )
            if (seedFailed) {
                Text(
                    text = stringResource(id = R.string.splash_seed_error),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onRetry) {
                    Text(text = stringResource(id = R.string.splash_retry))
                }
            }
        }
    }
}
