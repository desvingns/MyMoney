package com.kshavrin.mymoney.feature.support.rewardedad

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.hilt.navigation.compose.hiltViewModel
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.rewardAdProgressIndicator
import com.kshavrin.mymoney.core.ui.theme.rewardAdProgressTrack
import com.kshavrin.mymoney.core.ui.theme.supportCard
import com.kshavrin.mymoney.core.ui.theme.supportCardTitle
import com.kshavrin.mymoney.core.ui.theme.supportDescription
import com.kshavrin.mymoney.feature.support.R
import com.kshavrin.mymoney.feature.support.googlesignin.GoogleSignInDialog

@Composable
fun RewardedAdSupportEntry(
    viewModel: RewardedAdViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showSignIn by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        context.findActivity()?.let(viewModel::onBlockShown)
    }
    RewardedAdContent(
        state = state,
        onWatch = { context.findActivity()?.let(viewModel::onWatchAd) },
        onRetry = { context.findActivity()?.let(viewModel::onRetry) },
        onSignIn = { showSignIn = true },
    )
    if (showSignIn) {
        context.findActivity()?.let { activity ->
            GoogleSignInDialog(
                activity = activity,
                onDismiss = { showSignIn = false },
                onSignedIn = {
                    showSignIn = false
                    context.findActivity()?.let(viewModel::onRetry)
                },
            )
        }
    }
}

@Composable
fun RewardedAdContent(
    state: RewardedAdState,
    onWatch: () -> Unit,
    onRetry: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val required = state.reward?.required ?: DEFAULT_REQUIRED_VIEWS
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.supportCard,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.supportCardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.supportStatusGap),
        ) {
            Text(
                text = stringResource(R.string.support_ads_title),
                style = MaterialTheme.typography.supportCardTitle,
            )
            Text(
                text = stringResource(R.string.support_ads_rule, required),
                style = MaterialTheme.typography.supportDescription,
            )
            when (state.status) {
                RewardedAdStatus.Loading -> LoadingBody()
                RewardedAdStatus.Unauthenticated -> UnauthenticatedBody(onSignIn = onSignIn)
                else ->
                    AuthenticatedBody(
                        state = state,
                        onWatch = onWatch,
                        onRetry = onRetry,
                    )
            }
        }
    }
}

@Composable
private fun LoadingBody() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.supportStatusGap),
        ) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.support_ads_loading),
                style = MaterialTheme.typography.supportDescription,
            )
        }
    }
}

@Composable
private fun UnauthenticatedBody(onSignIn: () -> Unit) {
    Text(
        text = stringResource(R.string.support_ads_sign_in_required),
        style = MaterialTheme.typography.supportDescription,
    )
    Button(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.supportActionMinHeight),
        onClick = onSignIn,
    ) {
        Text(stringResource(R.string.support_ads_sign_in_action))
    }
}

@Composable
private fun AuthenticatedBody(
    state: RewardedAdState,
    onWatch: () -> Unit,
    onRetry: () -> Unit,
) {
    state.reward?.let { reward ->
        RewardProgressRow(reward = reward)
        if (reward.plusActive) {
            Text(
                text = stringResource(R.string.support_ads_plus_active),
                style = MaterialTheme.typography.supportDescription,
            )
        }
    }
    when (state.status) {
        RewardedAdStatus.Ready ->
            WatchButton(enabled = true, onWatch = onWatch)

        RewardedAdStatus.NoFill -> {
            WatchButton(enabled = false, onWatch = onWatch)
            Text(
                text = stringResource(R.string.support_ads_no_fill),
                style = MaterialTheme.typography.supportDescription,
            )
        }

        RewardedAdStatus.RegionUnavailable ->
            Text(
                text = stringResource(R.string.support_ads_region_unavailable),
                style = MaterialTheme.typography.supportDescription,
            )

        RewardedAdStatus.Offline -> {
            Text(
                text = stringResource(R.string.support_ads_offline),
                style = MaterialTheme.typography.supportDescription,
            )
            TextButton(
                modifier = Modifier.heightIn(min = Spacing.supportActionMinHeight),
                onClick = onRetry,
            ) {
                Text(stringResource(R.string.support_ads_retry))
            }
        }

        RewardedAdStatus.AwaitingConfirmation ->
            Text(
                text = stringResource(R.string.support_ads_awaiting_confirmation),
                style = MaterialTheme.typography.supportDescription,
            )

        RewardedAdStatus.Rearming ->
            Text(
                text = stringResource(R.string.support_ads_loading),
                style = MaterialTheme.typography.supportDescription,
            )

        RewardedAdStatus.ConfirmationTimeout -> {
            Text(
                text = stringResource(R.string.support_ads_confirmation_timeout),
                style = MaterialTheme.typography.supportDescription,
            )
            TextButton(
                modifier = Modifier.heightIn(min = Spacing.supportActionMinHeight),
                onClick = onRetry,
            ) {
                Text(stringResource(R.string.support_ads_retry))
            }
        }

        RewardedAdStatus.Unavailable ->
            Text(
                text = stringResource(R.string.support_ads_unavailable),
                style = MaterialTheme.typography.supportDescription,
            )

        RewardedAdStatus.Loading,
        RewardedAdStatus.Unauthenticated,
        -> Unit
    }
}

@Composable
private fun RewardProgressRow(reward: RewardProgress) {
    val progressText =
        stringResource(R.string.support_ads_progress, reward.progress, reward.required)
    val fraction =
        if (reward.required > 0) {
            (reward.progress.toFloat() / reward.required.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    Column(
        modifier = Modifier.clearAndSetSemantics { contentDescription = progressText },
        verticalArrangement = Arrangement.spacedBy(Spacing.supportStatusGap),
    ) {
        Text(
            text = progressText,
            style = MaterialTheme.typography.supportDescription,
        )
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.rewardAdProgressIndicator,
            trackColor = MaterialTheme.colorScheme.rewardAdProgressTrack,
        )
    }
}

@Composable
private fun WatchButton(
    enabled: Boolean,
    onWatch: () -> Unit,
) {
    Button(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.supportActionMinHeight),
        onClick = onWatch,
        enabled = enabled,
    ) {
        Text(stringResource(R.string.support_ads_watch))
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private const val DEFAULT_REQUIRED_VIEWS = 5
