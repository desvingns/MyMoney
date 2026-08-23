package com.kshavrin.mymoney.feature.support.plus

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.kshavrin.mymoney.core.designsystem.R as DesignSystemR
import com.kshavrin.mymoney.core.domain.model.UserEntitlement
import com.kshavrin.mymoney.core.ui.flow.CollectActions
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.supportPanel
import com.kshavrin.mymoney.core.ui.theme.supportPanelContainer
import com.kshavrin.mymoney.core.ui.theme.supportPanelIllustration
import com.kshavrin.mymoney.core.ui.theme.supportPanelOutline
import com.kshavrin.mymoney.core.ui.theme.supportPanelSubtitle
import com.kshavrin.mymoney.core.ui.theme.supportPanelTitle
import com.kshavrin.mymoney.feature.support.R
import com.kshavrin.mymoney.feature.support.paywall.PaywallCatalog
import com.kshavrin.mymoney.feature.support.paywall.PaywallEvent
import com.kshavrin.mymoney.feature.support.paywall.PaywallPlanId
import com.kshavrin.mymoney.feature.support.paywall.PaywallState
import com.kshavrin.mymoney.feature.support.paywall.PlusStatusCard
import com.kshavrin.mymoney.feature.support.paywall.hasActivePlus
import kotlinx.coroutines.launch

@Composable
fun SupportPlusEntry(
    viewModel: SupportPlusViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    CollectActions(flow = viewModel.actions, key = viewModel) { action ->
        when (action) {
            SupportPlusAction.RequestNotificationPermission -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
    SupportPlusContent(
        state = state,
        onPlanSelected = viewModel::onPlanSelected,
        onRetry = viewModel::onRetryClicked,
    )
}

@Composable
fun SupportPlusContent(
    state: SupportPlusState,
    onPlanSelected: (PaywallPlanId) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.supportPanel,
        color = MaterialTheme.colorScheme.supportPanelContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.supportPanelOutline),
    ) {
        Box {
            Column(
                modifier = Modifier.padding(Spacing.supportPanelPadding),
                verticalArrangement = Arrangement.spacedBy(Spacing.supportPanelGap),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.supportPanelColumnGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(DesignSystemR.drawable.support_neon_plus),
                        contentDescription = stringResource(R.string.support_image_plus_description),
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
                            text = stringResource(R.string.paywall_support_entry_title),
                            style = MaterialTheme.typography.supportPanelTitle,
                        )
                        Text(
                            text = stringResource(R.string.paywall_support_entry_description),
                            style = MaterialTheme.typography.supportPanelSubtitle,
                        )
                    }
                }
                if (state.entitlement.hasActivePlus()) {
                    PlusStatusCard(entitlement = state.entitlement as UserEntitlement.Plus)
                } else {
                    PaywallCatalog(
                        state = state.toPaywallState(),
                        onEvent = { event ->
                            when (event) {
                                is PaywallEvent.PlanSelected -> onPlanSelected(event.planId)
                                PaywallEvent.RetryClicked -> onRetry()
                                PaywallEvent.BackClicked -> Unit
                            }
                        },
                    )
                }
            }
            SupportPlusInfoIcon(modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupportPlusInfoIcon(modifier: Modifier = Modifier) {
    val tooltipState = rememberTooltipState(isPersistent = false)
    val scope = rememberCoroutineScope()
    val description = stringResource(R.string.support_plus_info_tooltip)
    TooltipBox(
        modifier = modifier,
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(text = description)
            }
        },
        state = tooltipState,
    ) {
        IconButton(onClick = { scope.launch { tooltipState.show() } }) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = description,
            )
        }
    }
}

private fun SupportPlusState.toPaywallState(): PaywallState =
    PaywallState(
        catalogState = catalogState,
        plans = plans,
        purchaseState = purchaseState,
        entitlement = entitlement,
        errorMessageRes = errorMessageRes,
    )
