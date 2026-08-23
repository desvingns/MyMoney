package com.kshavrin.mymoney.feature.support.rewardedad

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kshavrin.mymoney.core.designsystem.R as DesignSystemR
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.rewardAdProgressIndicator
import com.kshavrin.mymoney.core.ui.theme.rewardAdProgressTrack
import com.kshavrin.mymoney.core.ui.theme.supportActionLabel
import com.kshavrin.mymoney.core.ui.theme.supportPanel
import com.kshavrin.mymoney.core.ui.theme.supportPanelContainer
import com.kshavrin.mymoney.core.ui.theme.supportPanelIllustration
import com.kshavrin.mymoney.core.ui.theme.supportPanelOutline
import com.kshavrin.mymoney.core.ui.theme.supportPanelSubtitle
import com.kshavrin.mymoney.core.ui.theme.supportPanelTitle
import com.kshavrin.mymoney.core.ui.theme.supportPrimaryAction
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
    val visibleReward =
        state.reward.takeUnless {
            state.status == RewardedAdStatus.Loading ||
                state.status == RewardedAdStatus.Unauthenticated
        }
    val required = visibleReward?.required ?: DEFAULT_REQUIRED_VIEWS
    val progress = visibleReward?.progress?.coerceIn(0, DEFAULT_REQUIRED_VIEWS) ?: 0
    val action =
        when (state.status) {
            RewardedAdStatus.Unauthenticated ->
                RewardedAdAction(
                    labelRes = R.string.support_ads_sign_in_action,
                    enabled = true,
                    onClick = onSignIn,
                )

            RewardedAdStatus.Offline,
            RewardedAdStatus.ConfirmationTimeout,
            ->
                RewardedAdAction(
                    labelRes = R.string.support_ads_retry,
                    enabled = true,
                    onClick = onRetry,
                )

            else ->
                RewardedAdAction(
                    labelRes = R.string.support_ads_watch,
                    enabled = state.status == RewardedAdStatus.Ready,
                    onClick = onWatch,
                )
        }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.supportPanel,
        color = MaterialTheme.colorScheme.supportPanelContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.supportPanelOutline),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.supportPanelPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.supportPanelGap),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.supportPanelColumnGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(DesignSystemR.drawable.support_neon_ads),
                    contentDescription = stringResource(R.string.support_image_ads_description),
                    contentScale = ContentScale.Fit,
                    modifier =
                        Modifier
                            .size(Spacing.supportPanelIconSize)
                            .clip(MaterialTheme.shapes.supportPanelIllustration),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.supportPanelColumnGap),
                ) {
                    Text(
                        text = stringResource(R.string.support_ads_title),
                        style = MaterialTheme.typography.supportPanelTitle,
                    )
                    Text(
                        text = stringResource(R.string.support_ads_rule, required),
                        style = MaterialTheme.typography.supportPanelSubtitle,
                    )
                }
            }
            RewardProgressRow(
                progress = progress,
                required = required,
            )
            RewardedAdActionButton(action = action)
            RewardedAdStatusLine(
                messageRes = state.statusMessageRes(),
                showLoadingIndicator =
                    state.status == RewardedAdStatus.Loading ||
                        state.status == RewardedAdStatus.Rearming,
            )
        }
    }
}

@Composable
private fun RewardProgressRow(
    progress: Int,
    required: Int,
) {
    val completedCells = progress.coerceIn(0, DEFAULT_REQUIRED_VIEWS)
    val progressText =
        stringResource(R.string.support_ads_progress, completedCells, required)
    Column(
        modifier = Modifier.clearAndSetSemantics { contentDescription = progressText },
        verticalArrangement = Arrangement.spacedBy(Spacing.supportPanelColumnGap),
    ) {
        Text(
            text = progressText,
            style = MaterialTheme.typography.supportPanelSubtitle,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.supportPanelColumnGap),
        ) {
            repeat(DEFAULT_REQUIRED_VIEWS) { index ->
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(Spacing.supportRewardAdProgressCellHeight)
                            .clip(MaterialTheme.shapes.supportPanelIllustration)
                            .background(
                                if (index < completedCells) {
                                    MaterialTheme.colorScheme.rewardAdProgressIndicator
                                } else {
                                    MaterialTheme.colorScheme.rewardAdProgressTrack
                                },
                            ),
                )
            }
        }
    }
}

@Composable
private fun RewardedAdActionButton(
    action: RewardedAdAction,
) {
    Button(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.supportActionMinHeight),
        onClick = action.onClick,
        enabled = action.enabled,
        shape = MaterialTheme.shapes.supportPrimaryAction,
    ) {
        Text(
            text = stringResource(action.labelRes),
            style = MaterialTheme.typography.supportActionLabel,
        )
    }
}

@Composable
private fun RewardedAdStatusLine(
    messageRes: Int?,
    showLoadingIndicator: Boolean,
) {
    if (messageRes == null) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.supportPanelColumnGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showLoadingIndicator) {
            CircularProgressIndicator()
        }
        Text(
            text = stringResource(messageRes),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.supportPanelSubtitle,
        )
    }
}

private fun RewardedAdState.statusMessageRes(): Int? =
    if (status == RewardedAdStatus.Ready && reward?.plusActive == true) {
        R.string.support_ads_plus_active
    } else {
        status.statusMessageRes()
    }

private fun RewardedAdStatus.statusMessageRes(): Int? =
    when (this) {
        RewardedAdStatus.Loading,
        RewardedAdStatus.Rearming,
        -> R.string.support_ads_loading

        RewardedAdStatus.Unauthenticated -> R.string.support_ads_sign_in_required
        RewardedAdStatus.Ready -> null
        RewardedAdStatus.NoFill -> R.string.support_ads_no_fill
        RewardedAdStatus.RegionUnavailable -> R.string.support_ads_region_unavailable
        RewardedAdStatus.Offline -> R.string.support_ads_offline
        RewardedAdStatus.AwaitingConfirmation -> R.string.support_ads_awaiting_confirmation
        RewardedAdStatus.ConfirmationTimeout -> R.string.support_ads_confirmation_timeout
        RewardedAdStatus.Unavailable -> R.string.support_ads_unavailable
    }

private data class RewardedAdAction(
    val labelRes: Int,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private const val DEFAULT_REQUIRED_VIEWS = 5
