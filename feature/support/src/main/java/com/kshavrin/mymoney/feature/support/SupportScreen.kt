package com.kshavrin.mymoney.feature.support

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kshavrin.mymoney.core.designsystem.R as DesignSystemR
import com.kshavrin.mymoney.core.domain.billing.SupportProduct
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.supportActionLabel
import com.kshavrin.mymoney.core.ui.theme.supportBackLabel
import com.kshavrin.mymoney.core.ui.theme.supportCounterLabel
import com.kshavrin.mymoney.core.ui.theme.supportCounterValue
import com.kshavrin.mymoney.core.ui.theme.supportHeadline
import com.kshavrin.mymoney.core.ui.theme.supportHeadlineAccent
import com.kshavrin.mymoney.core.ui.theme.supportHeroIllustration
import com.kshavrin.mymoney.core.ui.theme.supportPanel
import com.kshavrin.mymoney.core.ui.theme.supportPanelContainer
import com.kshavrin.mymoney.core.ui.theme.supportPanelDivider
import com.kshavrin.mymoney.core.ui.theme.supportPanelIllustration
import com.kshavrin.mymoney.core.ui.theme.supportPanelOutline
import com.kshavrin.mymoney.core.ui.theme.supportPanelSubtitle
import com.kshavrin.mymoney.core.ui.theme.supportPanelTitle
import com.kshavrin.mymoney.core.ui.theme.supportPrimaryAction
import com.kshavrin.mymoney.core.ui.theme.supportPriceValue
import com.kshavrin.mymoney.core.ui.theme.supportProductName
import com.kshavrin.mymoney.core.ui.theme.supportProductPrice
import com.kshavrin.mymoney.core.ui.theme.supportSubtitle
import com.kshavrin.mymoney.core.ui.theme.supporterChip
import com.kshavrin.mymoney.core.ui.theme.supporterChipContainer
import com.kshavrin.mymoney.core.ui.theme.supporterChipContent
import com.kshavrin.mymoney.core.ui.theme.supporterChipLabel
import com.kshavrin.mymoney.core.ui.theme.supporterChipOutline
import com.kshavrin.mymoney.feature.support.R

@Composable
fun SupportContent(
    state: SupportState,
    onEvent: (SupportEvent) -> Unit,
    adSlot: @Composable () -> Unit = {},
    plusSlot: @Composable () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SupportBackRow(onBack = { onEvent(SupportEvent.BackClicked) })
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = Spacing.supportPanelPadding,
                        vertical = Spacing.supportPanelGap,
                    ),
            verticalArrangement = Arrangement.spacedBy(Spacing.supportPanelGap),
        ) {
            SupportHeroIntro()
            adSlot()
            SupportPurchaseSection(
                state = state,
                onEvent = onEvent,
            )
            plusSlot()
            SupporterGratitude(state = state)
        }
    }
}

@Composable
private fun SupportBackRow(onBack: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(Spacing.supportTopBarHeight)
                .padding(horizontal = Spacing.supportPanelColumnGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            modifier = Modifier.size(Spacing.supportBackTouchTarget),
            onClick = onBack,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.support_back),
            )
        }
        Text(
            text = stringResource(R.string.support_back_label),
            color = MaterialTheme.colorScheme.supportBackLabel,
            style = MaterialTheme.typography.supportBackLabel,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.support_title),
            style = MaterialTheme.typography.supportBackLabel,
        )
    }
}

@Composable
private fun SupportHeroIntro() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.supportPanelGap),
    ) {
        Image(
            painter = painterResource(DesignSystemR.drawable.support_neon_hero_cup),
            contentDescription = stringResource(R.string.support_image_hero_description),
            contentScale = ContentScale.Fit,
            modifier =
                Modifier
                    .size(Spacing.supportHeroSize)
                    .clip(MaterialTheme.shapes.supportHeroIllustration),
        )
        SupportHeadline()
    }
}

@Composable
private fun SupportHeadline() {
    val accentColor = MaterialTheme.colorScheme.supportHeadlineAccent
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.supportPanelGap),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.support_headline_lead),
                style = MaterialTheme.typography.supportHeadline,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.support_headline_accent),
                color = accentColor,
                style = MaterialTheme.typography.supportHeadline,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .padding(bottom = Spacing.supportHeadlineUnderlineThickness)
                        .drawBehind {
                            val thickness = Spacing.supportHeadlineUnderlineThickness.toPx()
                            drawRect(
                                color = accentColor,
                                topLeft = Offset(0f, size.height - thickness),
                                size = Size(size.width, thickness),
                            )
                        },
            )
        }
        Text(
            text = stringResource(R.string.support_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.supportSubtitle,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = Spacing.supportSubtitleMaxWidth),
        )
    }
}

@Composable
private fun SupportPurchaseSection(
    state: SupportState,
    onEvent: (SupportEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.supportPanelGap)) {
        CoffeePurchaseCard(
            products = state.products,
            isBillingAvailable = state.billingState == SupportBillingState.Available,
            isPurchaseInProgress = state.isPurchaseInProgress,
            onPurchase = { productId -> onEvent(SupportEvent.PurchaseClicked(productId)) },
        )
        SupportPurchaseStatus(
            billingState = state.billingState,
            onEvent = onEvent,
        )
    }
}

@Composable
private fun SupportPurchaseStatus(
    billingState: SupportBillingState,
    onEvent: (SupportEvent) -> Unit,
) {
    when (billingState) {
        SupportBillingState.Loading,
        SupportBillingState.Available,
        -> Unit
        SupportBillingState.Pending ->
            SupportStatusLine(
                message = stringResource(R.string.support_pending),
            )

        SupportBillingState.NetworkError ->
            SupportStatusLine(
                message = stringResource(R.string.support_network_error),
                isError = true,
                action = {
                    TextButton(
                        modifier = Modifier.heightIn(min = Spacing.supportActionMinHeight),
                        onClick = { onEvent(SupportEvent.RetryClicked) },
                    ) {
                        Text(
                            text = stringResource(R.string.support_retry),
                            style = MaterialTheme.typography.supportActionLabel,
                        )
                    }
                },
            )

        is SupportBillingState.Unavailable ->
            SupportStatusLine(
                message = stringResource(billingState.reason.messageRes),
            )
    }
}

@Composable
private fun CoffeePurchaseCard(
    products: List<SupportProduct>,
    isBillingAvailable: Boolean,
    isPurchaseInProgress: Boolean,
    onPurchase: (String) -> Unit,
) {
    val smallProduct = products.firstOrNull { it.id == COFFEE_SMALL_PRODUCT_ID }
    val largeProduct = products.firstOrNull { it.id == COFFEE_LARGE_PRODUCT_ID }
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
            CoffeeProductColumn(
                modifier = Modifier.weight(1f),
                product = smallProduct,
                productId = COFFEE_SMALL_PRODUCT_ID,
                illustrationRes = DesignSystemR.drawable.support_neon_coffee_small,
                illustrationDescriptionRes = R.string.support_image_coffee_small_description,
                illustrationModifier =
                    Modifier
                        .width(Spacing.supportCoffeeIllustrationWidthSmall)
                        .height(Spacing.supportCoffeeIllustrationHeight),
                isPurchaseEnabled =
                    isBillingAvailable &&
                        smallProduct != null &&
                        !isPurchaseInProgress,
                onPurchase = onPurchase,
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.supportPanelDivider),
            )
            CoffeeProductColumn(
                modifier = Modifier.weight(1f),
                product = largeProduct,
                productId = COFFEE_LARGE_PRODUCT_ID,
                illustrationRes = DesignSystemR.drawable.support_neon_coffee_large,
                illustrationDescriptionRes = R.string.support_image_coffee_large_description,
                illustrationModifier = Modifier.size(Spacing.supportCoffeeIllustrationHeight),
                isPurchaseEnabled =
                    isBillingAvailable &&
                        largeProduct != null &&
                        !isPurchaseInProgress,
                onPurchase = onPurchase,
            )
        }
    }
}

@Composable
private fun CoffeeProductColumn(
    product: SupportProduct?,
    productId: String,
    illustrationRes: Int,
    illustrationDescriptionRes: Int,
    illustrationModifier: Modifier,
    isPurchaseEnabled: Boolean,
    onPurchase: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.supportPanelColumnGap),
    ) {
        Image(
            painter = painterResource(illustrationRes),
            contentDescription = stringResource(illustrationDescriptionRes),
            contentScale = ContentScale.Fit,
            modifier = illustrationModifier.clip(MaterialTheme.shapes.supportPanelIllustration),
        )
        Text(
            text = coffeeLabel(productId),
            style = MaterialTheme.typography.supportProductName,
            textAlign = TextAlign.Center,
        )
        product?.let {
            Text(
                text = it.formattedPrice,
                color = MaterialTheme.colorScheme.supportPriceValue,
                style = MaterialTheme.typography.supportProductPrice,
                textAlign = TextAlign.Center,
            )
        }
        Button(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.supportActionMinHeight),
            onClick = { onPurchase(productId) },
            enabled = isPurchaseEnabled,
            shape = MaterialTheme.shapes.supportPrimaryAction,
        ) {
            Text(
                text = stringResource(R.string.support_purchase_action),
                style = MaterialTheme.typography.supportActionLabel,
            )
        }
    }
}

@Composable
private fun SupportStatusLine(
    message: String,
    isError: Boolean = false,
    action: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.supportPanelColumnGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color =
                if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            style = MaterialTheme.typography.supportPanelSubtitle,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

@Composable
private fun SupporterGratitude(state: SupportState) {
    val isSupporter = state.supporterState.badgeEarned
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.supportPanel,
        color = MaterialTheme.colorScheme.supportPanelContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.supportPanelOutline),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.supportPanelPadding),
            horizontalArrangement = Arrangement.spacedBy(Spacing.supportPanelGap),
            verticalAlignment = Alignment.Top,
        ) {
            Image(
                painter = painterResource(DesignSystemR.drawable.support_neon_avatar),
                contentDescription = stringResource(R.string.support_image_avatar_description),
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(Spacing.supportAvatarSize).clip(CircleShape),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.supportPanelColumnGap),
            ) {
                Text(
                    text =
                        stringResource(
                            if (isSupporter) {
                                R.string.support_gratitude_title_supporter
                            } else {
                                R.string.support_gratitude_title_prospect
                            },
                        ),
                    style = MaterialTheme.typography.supportPanelTitle,
                )
                Text(
                    text =
                        stringResource(
                            if (isSupporter) {
                                R.string.support_gratitude_body_supporter
                            } else {
                                R.string.support_gratitude_body_prospect
                            },
                        ),
                    style = MaterialTheme.typography.supportPanelSubtitle,
                )
                if (isSupporter) {
                    Surface(
                        modifier = Modifier.height(Spacing.supporterChipHeight),
                        shape = MaterialTheme.shapes.supporterChip,
                        color = MaterialTheme.colorScheme.supporterChipContainer,
                        contentColor = MaterialTheme.colorScheme.supporterChipContent,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.supporterChipOutline),
                    ) {
                        Text(
                            text = stringResource(R.string.support_badge),
                            style = MaterialTheme.typography.supporterChipLabel,
                            modifier = Modifier.padding(horizontal = Spacing.supportStatusGap),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.supportPanelColumnGap),
                ) {
                    SupportCounter(
                        value = state.adsWatchedTotal,
                        labelRes = R.string.support_counter_ads_label,
                        modifier = Modifier.weight(1f),
                    )
                    SupportCounter(
                        value = state.supporterState.smallCoffeeCount,
                        labelRes = R.string.support_counter_coffee_small_label,
                        modifier = Modifier.weight(1f),
                    )
                    SupportCounter(
                        value = state.supporterState.largeCoffeeCount,
                        labelRes = R.string.support_counter_coffee_large_label,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SupportCounter(
    value: Int,
    labelRes: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.supportPanelColumnGap),
    ) {
        Text(
            text = value.toString(),
            color = MaterialTheme.colorScheme.supportCounterValue,
            style = MaterialTheme.typography.supportCounterValue,
        )
        Text(
            text = stringResource(labelRes),
            color = MaterialTheme.colorScheme.supportCounterLabel,
            style = MaterialTheme.typography.supportCounterLabel,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun coffeeLabel(productId: String): String =
    stringResource(
        when (productId) {
            COFFEE_SMALL_PRODUCT_ID -> R.string.support_coffee_small_name
            COFFEE_LARGE_PRODUCT_ID -> R.string.support_coffee_large_name
            else -> R.string.support_coffee_generic_name
        },
    )

private val SupportUnavailableReason.messageRes: Int
    get() =
        when (this) {
            SupportUnavailableReason.DisabledInBuild -> R.string.support_unavailable_build
            SupportUnavailableReason.UnavailableOnDevice -> R.string.support_unavailable_device
            SupportUnavailableReason.UnavailableInRegion -> R.string.support_unavailable_region
            SupportUnavailableReason.Unavailable -> R.string.support_unavailable
        }
