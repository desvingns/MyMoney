package com.kshavrin.mymoney.feature.support.paywall

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kshavrin.mymoney.core.designsystem.R as DesignSystemR
import com.kshavrin.mymoney.core.domain.model.EntitlementSource
import com.kshavrin.mymoney.core.domain.model.EntitlementState
import com.kshavrin.mymoney.core.domain.model.UserEntitlement
import com.kshavrin.mymoney.core.ui.flow.CollectActions
import com.kshavrin.mymoney.core.ui.navigation.PaywallEntryPoint
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.supportActionLabel
import com.kshavrin.mymoney.core.ui.theme.supportCard
import com.kshavrin.mymoney.core.ui.theme.supportCardTitle
import com.kshavrin.mymoney.core.ui.theme.supportDescription
import com.kshavrin.mymoney.core.ui.theme.supportPanel
import com.kshavrin.mymoney.core.ui.theme.supportPanelContainer
import com.kshavrin.mymoney.core.ui.theme.supportPanelDivider
import com.kshavrin.mymoney.core.ui.theme.supportPanelIllustration
import com.kshavrin.mymoney.core.ui.theme.supportPanelOutline
import com.kshavrin.mymoney.core.ui.theme.supportPanelSubtitle
import com.kshavrin.mymoney.core.ui.theme.supportPriceValue
import com.kshavrin.mymoney.core.ui.theme.supportPrimaryAction
import com.kshavrin.mymoney.core.ui.theme.supportProductName
import com.kshavrin.mymoney.core.ui.theme.supportProductPrice
import com.kshavrin.mymoney.feature.support.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun PaywallRoute(
    entryPoint: PaywallEntryPoint,
    onBack: () -> Unit,
    viewModel: PaywallViewModel =
        androidx.hilt.navigation.compose
            .hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    CollectActions(flow = viewModel.actions, key = viewModel) { action ->
        when (action) {
            PaywallAction.NavigateBack -> onBack()
            PaywallAction.RequestNotificationPermission -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
    PaywallScreen(
        entryPoint = entryPoint,
        state = state,
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    entryPoint: PaywallEntryPoint,
    state: PaywallState,
    onEvent: (PaywallEvent) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(entryPoint.titleRes)) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(PaywallEvent.BackClicked) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.paywall_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        PaywallContent(
            entryPoint = entryPoint,
            state = state,
            onEvent = onEvent,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
fun PaywallContent(
    entryPoint: PaywallEntryPoint,
    state: PaywallState,
    onEvent: (PaywallEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = Spacing.supportContentHorizontalPadding,
                    vertical = Spacing.supportSectionGap,
                ),
        verticalArrangement = Arrangement.spacedBy(Spacing.supportSectionGap),
    ) {
        Text(
            text = stringResource(entryPoint.introRes),
            style = MaterialTheme.typography.supportDescription,
        )
        PaywallBenefitsCard()
        FreeForeverCard()
        WorkspacePayerCard()
        if (state.entitlement.hasActivePlus()) {
            PlusStatusCard(entitlement = state.entitlement as UserEntitlement.Plus)
        } else {
            PaywallCatalog(
                state = state,
                onEvent = onEvent,
            )
        }
    }
}

@Composable
private fun PaywallBenefitsCard() {
    PaywallCard {
        Text(
            text = stringResource(R.string.paywall_benefits_title),
            style = MaterialTheme.typography.supportCardTitle,
        )
        Text(
            text = stringResource(R.string.paywall_benefit_shared_workspace),
            style = MaterialTheme.typography.supportDescription,
        )
        Text(
            text = stringResource(R.string.paywall_benefit_backup_history),
            style = MaterialTheme.typography.supportDescription,
        )
    }
}

@Composable
private fun FreeForeverCard() {
    PaywallCard(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = stringResource(R.string.paywall_free_forever_title),
            style = MaterialTheme.typography.supportCardTitle,
        )
        Text(
            text = stringResource(R.string.paywall_free_forever_description),
            style = MaterialTheme.typography.supportDescription,
        )
    }
}

@Composable
private fun WorkspacePayerCard() {
    PaywallCard(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Text(
            text = stringResource(R.string.paywall_workspace_payer_title),
            style = MaterialTheme.typography.supportCardTitle,
        )
        Text(
            text = stringResource(R.string.paywall_workspace_payer_description),
            style = MaterialTheme.typography.supportDescription,
        )
    }
}

@Composable
internal fun PaywallCatalog(
    state: PaywallState,
    onEvent: (PaywallEvent) -> Unit,
) {
    when (state.catalogState) {
        PaywallCatalogState.Loading ->
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
                        text = stringResource(R.string.paywall_prices_loading),
                        style = MaterialTheme.typography.supportDescription,
                    )
                }
            }

        PaywallCatalogState.Available ->
            PaywallPlans(
                plans = state.plans,
                purchaseState = state.purchaseState,
                onEvent = onEvent,
            )

        PaywallCatalogState.UnavailableInRegion -> {
            PaywallMessageCard(message = stringResource(R.string.paywall_region_unavailable))
            PaywallPlans(plans = state.plans)
        }

        PaywallCatalogState.Unavailable -> {
            PaywallMessageCard(message = stringResource(R.string.paywall_billing_unavailable))
            PaywallPlans(plans = state.plans)
        }

        PaywallCatalogState.Error -> {
            PaywallMessageCard(
                message = stringResource(R.string.paywall_prices_error),
                action = {
                    TextButton(
                        modifier = Modifier.heightIn(min = Spacing.supportActionMinHeight),
                        onClick = { onEvent(PaywallEvent.RetryClicked) },
                    ) {
                        Text(stringResource(R.string.paywall_retry))
                    }
                },
            )
            PaywallPlans(plans = state.plans)
        }
    }
    state.errorMessageRes?.let { messageRes ->
        PaywallMessageCard(message = stringResource(messageRes))
    }
}

@Composable
internal fun PaywallPlans(
    plans: List<PaywallPlan>,
    purchaseState: PaywallPurchaseState = PaywallPurchaseState.Idle,
    onEvent: ((PaywallEvent) -> Unit)? = null,
) {
    val monthlyPlan = plans.firstOrNull { it.id == PaywallPlanId.Monthly }
    val yearlyPlan = plans.firstOrNull { it.id == PaywallPlanId.Yearly }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.supportStatusGap)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.supportPanel,
            color = MaterialTheme.colorScheme.supportPanelContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.supportPanelOutline),
        ) {
            Row(
                modifier =
                    Modifier
                        .padding(Spacing.supportPanelPadding)
                        .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Spacing.supportPanelColumnGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PaywallPlanColumn(
                    modifier = Modifier.weight(1f),
                    plan = monthlyPlan,
                    planId = PaywallPlanId.Monthly,
                    onSelect = planSelectHandler(PaywallPlanId.Monthly, purchaseState, onEvent),
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.supportPanelDivider),
                )
                PaywallPlanColumn(
                    modifier = Modifier.weight(1f),
                    plan = yearlyPlan,
                    planId = PaywallPlanId.Yearly,
                    onSelect = planSelectHandler(PaywallPlanId.Yearly, purchaseState, onEvent),
                )
            }
        }
        when (purchaseState) {
            PaywallPurchaseState.Idle -> Unit
            PaywallPurchaseState.InProgress ->
                Text(
                    text = stringResource(R.string.paywall_purchase_processing),
                    style = MaterialTheme.typography.supportDescription,
                )

            PaywallPurchaseState.ReconcilingEntitlement ->
                Text(
                    text = stringResource(R.string.paywall_purchase_confirming),
                    style = MaterialTheme.typography.supportDescription,
                )

            PaywallPurchaseState.AwaitingEntitlement ->
                Text(
                    text = stringResource(R.string.paywall_purchase_confirmation_pending),
                    style = MaterialTheme.typography.supportDescription,
                )

            PaywallPurchaseState.Pending ->
                Text(
                    text = stringResource(R.string.paywall_purchase_pending),
                    style = MaterialTheme.typography.supportDescription,
                )
        }
    }
}

internal fun planSelectHandler(
    planId: PaywallPlanId,
    purchaseState: PaywallPurchaseState,
    onEvent: ((PaywallEvent) -> Unit)?,
): (() -> Unit)? =
    if (purchaseState == PaywallPurchaseState.Idle) {
        onEvent?.let { eventHandler -> { eventHandler(PaywallEvent.PlanSelected(planId)) } }
    } else {
        null
    }

@Composable
internal fun PaywallPlanColumn(
    plan: PaywallPlan?,
    planId: PaywallPlanId,
    onSelect: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.supportPanelColumnGap),
    ) {
        Image(
            painter = painterResource(planId.illustrationRes),
            contentDescription = stringResource(planId.iconContentDescriptionRes),
            contentScale = ContentScale.Fit,
            modifier =
                Modifier
                    .size(Spacing.supportPanelIconSize)
                    .clip(MaterialTheme.shapes.supportPanelIllustration),
        )
        Text(
            text = stringResource(planId.titleRes),
            style = MaterialTheme.typography.supportProductName,
            textAlign = TextAlign.Center,
        )
        Text(
            text = plan?.formattedPrice ?: stringResource(planId.fallbackPriceRes),
            color = MaterialTheme.colorScheme.supportPriceValue,
            style = MaterialTheme.typography.supportProductPrice,
            textAlign = TextAlign.Center,
        )
        if (planId == PaywallPlanId.Yearly) {
            Text(
                text = stringResource(R.string.paywall_yearly_trial),
                style = MaterialTheme.typography.supportPanelSubtitle,
                textAlign = TextAlign.Center,
            )
        }
        Button(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.supportActionMinHeight),
            onClick = { onSelect?.invoke() },
            enabled = onSelect != null,
            shape = MaterialTheme.shapes.supportPrimaryAction,
        ) {
            Text(
                text = stringResource(R.string.paywall_select_plan),
                style = MaterialTheme.typography.supportActionLabel,
            )
        }
    }
}

@Composable
internal fun PlusStatusCard(entitlement: UserEntitlement.Plus) {
    PaywallCard(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = stringResource(entitlement.statusRes),
            style = MaterialTheme.typography.supportCardTitle,
        )
        entitlement.statusDate()?.let { (labelRes, instant) ->
            Text(
                text = stringResource(labelRes, formatRenewalDate(instant)),
                style = MaterialTheme.typography.supportDescription,
            )
        }
    }
}

@Composable
private fun PaywallMessageCard(
    message: String,
    action: @Composable (() -> Unit)? = null,
) {
    PaywallCard(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.supportDescription,
        )
        action?.invoke()
    }
}

@Composable
private fun PaywallCard(
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.supportCard,
        color = color,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.supportCardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.supportStatusGap),
            content = { content() },
        )
    }
}

private val PaywallEntryPoint.titleRes: Int
    get() =
        when (this) {
            PaywallEntryPoint.SupportSection -> R.string.paywall_title_support
            PaywallEntryPoint.SharedSyncGate -> R.string.paywall_title_shared_sync
        }

private val PaywallEntryPoint.introRes: Int
    get() =
        when (this) {
            PaywallEntryPoint.SupportSection -> R.string.paywall_intro_support
            PaywallEntryPoint.SharedSyncGate -> R.string.paywall_intro_shared_sync
        }

private val PaywallPlanId.titleRes: Int
    get() =
        when (this) {
            PaywallPlanId.Monthly -> R.string.paywall_monthly_title
            PaywallPlanId.Yearly -> R.string.paywall_yearly_title
        }

private val PaywallPlanId.fallbackPriceRes: Int
    get() =
        when (this) {
            PaywallPlanId.Monthly -> R.string.paywall_monthly_fallback_price
            PaywallPlanId.Yearly -> R.string.paywall_yearly_fallback_price
        }

private val PaywallPlanId.iconContentDescriptionRes: Int
    get() =
        when (this) {
            PaywallPlanId.Monthly -> R.string.paywall_monthly_icon_content_description
            PaywallPlanId.Yearly -> R.string.paywall_yearly_icon_content_description
        }

private val PaywallPlanId.illustrationRes: Int
    get() =
        when (this) {
            PaywallPlanId.Monthly -> DesignSystemR.drawable.support_neon_plus_monthly
            PaywallPlanId.Yearly -> DesignSystemR.drawable.support_neon_plus_yearly
        }

private val UserEntitlement.Plus.statusRes: Int
    get() =
        when {
            source == EntitlementSource.AD_REWARD -> R.string.paywall_status_reward_active
            source == EntitlementSource.WHITELIST -> R.string.paywall_status_whitelist_active
            state == EntitlementState.TRIAL -> R.string.paywall_status_trial
            state == EntitlementState.LOCAL_ONLY -> R.string.paywall_status_local_only
            state == EntitlementState.GRACE -> R.string.paywall_status_grace
            else -> R.string.paywall_status_active
        }

private fun UserEntitlement.Plus.statusDate(): Pair<Int, Instant>? =
    when {
        state == EntitlementState.GRACE ->
            graceEndsAt?.let { instant -> R.string.paywall_access_ends_on to instant }

        source.isSubscription() && (state == EntitlementState.TRIAL || state == EntitlementState.ACTIVE) ->
            expiresAt?.let { instant -> R.string.paywall_renews_on to instant }

        else -> expiresAt?.let { instant -> R.string.paywall_access_ends_on to instant }
    }

private fun EntitlementSource.isSubscription(): Boolean =
    this == EntitlementSource.SUBSCRIPTION_MONTHLY || this == EntitlementSource.SUBSCRIPTION_YEARLY

internal fun UserEntitlement.hasActivePlus(): Boolean =
    this is UserEntitlement.Plus && state != EntitlementState.NONE && state != EntitlementState.EXPIRED

private fun formatRenewalDate(instant: Instant): String =
    instant
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))
