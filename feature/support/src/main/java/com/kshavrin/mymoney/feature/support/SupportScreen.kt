package com.kshavrin.mymoney.feature.support

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kshavrin.mymoney.core.domain.billing.SupportProduct
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.supportCard
import com.kshavrin.mymoney.core.ui.theme.supportCardTitle
import com.kshavrin.mymoney.core.ui.theme.supportDescription
import com.kshavrin.mymoney.core.ui.theme.supporterBadge
import com.kshavrin.mymoney.core.ui.theme.supporterBadgeLabel
import com.kshavrin.mymoney.core.ui.theme.supporterGratitudeCount
import com.kshavrin.mymoney.core.ui.theme.supportPurchaseCardContainer
import com.kshavrin.mymoney.core.ui.theme.supportPurchaseCardContent
import com.kshavrin.mymoney.core.ui.theme.supportPurchaseCardOutline
import com.kshavrin.mymoney.core.ui.theme.supportStatusMessage
import com.kshavrin.mymoney.core.ui.theme.supportUnavailableContainer
import com.kshavrin.mymoney.core.ui.theme.supportUnavailableContent
import com.kshavrin.mymoney.core.ui.theme.supportNetworkErrorContainer
import com.kshavrin.mymoney.core.ui.theme.supportNetworkErrorContent
import com.kshavrin.mymoney.core.ui.theme.supportPendingContainer
import com.kshavrin.mymoney.core.ui.theme.supportPendingContent
import com.kshavrin.mymoney.core.ui.theme.supporterBadgeContainer
import com.kshavrin.mymoney.core.ui.theme.supporterBadgeContent
import com.kshavrin.mymoney.core.ui.theme.supporterGratitudeContainer
import com.kshavrin.mymoney.core.ui.theme.supporterGratitudeContent
import com.kshavrin.mymoney.feature.support.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportContent(
    state: SupportState,
    onEvent: (SupportEvent) -> Unit,
    videosWatchedSlot: @Composable () -> Unit = {},
    adSlot: @Composable () -> Unit = {},
    plusSlot: @Composable () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.support_title)) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(SupportEvent.BackClicked) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.support_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = Spacing.supportContentHorizontalPadding,
                        vertical = Spacing.supportSectionGap,
                    ),
            verticalArrangement = Arrangement.spacedBy(Spacing.supportSectionGap),
        ) {
            videosWatchedSlot()
            Text(
                text = stringResource(R.string.support_description),
                style = MaterialTheme.typography.supportDescription,
            )
            adSlot()
            plusSlot()
            SupportPurchaseSection(
                state = state,
                onEvent = onEvent,
            )
            if (state.supporterState.badgeEarned) {
                SupporterBadge()
                SupporterGratitude(purchaseCount = state.supporterState.purchaseCount)
            }
        }
    }
}

@Composable
private fun SupportPurchaseSection(
    state: SupportState,
    onEvent: (SupportEvent) -> Unit,
) {
    when (val billingState = state.billingState) {
        SupportBillingState.Loading -> Unit
        SupportBillingState.Available ->
            CoffeePurchaseCard(
                products = state.products,
                isPurchaseInProgress = state.isPurchaseInProgress,
                onPurchase = { productId -> onEvent(SupportEvent.PurchaseClicked(productId)) },
            )

        SupportBillingState.Pending ->
            SupportStatusCard(
                message = stringResource(R.string.support_pending),
                containerColor = MaterialTheme.colorScheme.supportPendingContainer,
                contentColor = MaterialTheme.colorScheme.supportPendingContent,
            )

        SupportBillingState.NetworkError ->
            SupportStatusCard(
                message = stringResource(R.string.support_network_error),
                containerColor = MaterialTheme.colorScheme.supportNetworkErrorContainer,
                contentColor = MaterialTheme.colorScheme.supportNetworkErrorContent,
                action = {
                    TextButton(
                        modifier = Modifier.heightIn(min = Spacing.supportActionMinHeight),
                        onClick = { onEvent(SupportEvent.RetryClicked) },
                    ) {
                        Text(stringResource(R.string.support_retry))
                    }
                },
            )

        is SupportBillingState.Unavailable ->
            SupportStatusCard(
                message = stringResource(billingState.reason.messageRes),
                containerColor = MaterialTheme.colorScheme.supportUnavailableContainer,
                contentColor = MaterialTheme.colorScheme.supportUnavailableContent,
            )
    }
}

@Composable
private fun CoffeePurchaseCard(
    products: List<SupportProduct>,
    isPurchaseInProgress: Boolean,
    onPurchase: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.supportCard,
        color = MaterialTheme.colorScheme.supportPurchaseCardContainer,
        contentColor = MaterialTheme.colorScheme.supportPurchaseCardContent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.supportPurchaseCardOutline),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.supportCardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.supportStatusGap),
        ) {
            Text(
                text = stringResource(R.string.support_coffee_title),
                style = MaterialTheme.typography.supportCardTitle,
            )
            products.forEach { product ->
                Button(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Spacing.supportActionMinHeight),
                    onClick = { onPurchase(product.id) },
                    enabled = !isPurchaseInProgress,
                ) {
                    Text(coffeeLabel(product))
                }
            }
        }
    }
}

@Composable
private fun SupportStatusCard(
    message: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    action: @Composable (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.supportCard,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.supportCardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.supportStatusGap),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.supportStatusMessage,
            )
            action?.invoke()
        }
    }
}

@Composable
private fun SupporterBadge() {
    Surface(
        shape = MaterialTheme.shapes.supporterBadge,
        color = MaterialTheme.colorScheme.supporterBadgeContainer,
        contentColor = MaterialTheme.colorScheme.supporterBadgeContent,
    ) {
        Text(
            text = stringResource(R.string.support_badge),
            style = MaterialTheme.typography.supporterBadgeLabel,
            modifier = Modifier.padding(Spacing.supportCardPadding),
        )
    }
}

@Composable
private fun SupporterGratitude(purchaseCount: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.supportCard,
        color = MaterialTheme.colorScheme.supporterGratitudeContainer,
        contentColor = MaterialTheme.colorScheme.supporterGratitudeContent,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.supportCardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.supportStatusGap),
        ) {
            Text(
                text = stringResource(R.string.support_gratitude),
                style = MaterialTheme.typography.supportDescription,
            )
            Text(
                text = stringResource(R.string.support_gratitude_count, purchaseCount),
                style = MaterialTheme.typography.supporterGratitudeCount,
            )
        }
    }
}

@Composable
private fun coffeeLabel(product: SupportProduct): String =
    stringResource(
        when (product.id) {
            COFFEE_SMALL_PRODUCT_ID -> R.string.support_coffee_small
            COFFEE_LARGE_PRODUCT_ID -> R.string.support_coffee_large
            else -> R.string.support_coffee_price
        },
        product.formattedPrice,
    )

private val SupportUnavailableReason.messageRes: Int
    get() =
        when (this) {
            SupportUnavailableReason.DisabledInBuild -> R.string.support_unavailable_build
            SupportUnavailableReason.UnavailableOnDevice -> R.string.support_unavailable_device
            SupportUnavailableReason.UnavailableInRegion -> R.string.support_unavailable_region
            SupportUnavailableReason.Unavailable -> R.string.support_unavailable
        }
