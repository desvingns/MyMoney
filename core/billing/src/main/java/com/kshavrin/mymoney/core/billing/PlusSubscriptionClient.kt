package com.kshavrin.mymoney.core.billing

import android.app.Activity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import com.kshavrin.mymoney.core.domain.billing.SupportProduct
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class PlusSubscriptionClient
    @Inject
    constructor() {
        private val processingMutex = Mutex()

        suspend fun queryProductDetails(billingClient: BillingClient): List<ProductDetails> {
            val result = billingClient.queryPlusProductDetails()
            if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                throw PlayBillingGateway.BillingOperationException(result.billingResult)
            }
            return result.productDetailsList.filter { it.productId in PlusSku.productIds }
        }

        fun toSupportProduct(productDetails: ProductDetails): SupportProduct? =
            productDetails
                .subscriptionOfferDetails
                ?.let { offers ->
                    val sku = PlusSku.fromProductId(productDetails.productId) ?: return null
                    offers
                        .firstOrNull { offer -> offer.hasRequiredPricePhase(sku) }
                        ?.let { offer ->
                            SupportProduct(
                                id = productDetails.productId,
                                formattedPrice = offer.pricingPhases.pricingPhaseList.last().formattedPrice,
                                title = productDetails.title,
                            )
                        }
                }

        fun offerFor(productDetails: ProductDetails): ProductDetails.SubscriptionOfferDetails? {
            val sku = PlusSku.fromProductId(productDetails.productId) ?: return null
            return productDetails.subscriptionOfferDetails.orEmpty().firstOrNull { offer ->
                offer.hasRequiredPricePhase(sku)
            }
        }

        fun launchBillingFlow(
            billingClient: BillingClient,
            activity: Activity,
            productDetails: ProductDetails,
            offer: ProductDetails.SubscriptionOfferDetails,
        ): BillingResult =
            billingClient.launchBillingFlow(
                activity,
                BillingFlowParams
                    .newBuilder()
                    .setProductDetailsParamsList(
                        listOf(
                            BillingFlowParams.ProductDetailsParams
                                .newBuilder()
                                .setProductDetails(productDetails)
                                .setOfferToken(offer.offerToken)
                                .build(),
                        ),
                    ).build(),
            )

        suspend fun processPurchase(
            billingClient: BillingClient,
            purchase: Purchase,
        ): PurchaseOutcome =
            processingMutex.withLock {
                val productId = purchase.products.firstOrNull { it in PlusSku.productIds }
                    ?: return@withLock PurchaseOutcome.Unavailable(PlayBillingGateway.UNSUPPORTED_PURCHASE_STATE_REASON)
                when (purchase.purchaseState) {
                    Purchase.PurchaseState.PENDING -> PurchaseOutcome.Pending
                    Purchase.PurchaseState.PURCHASED -> {
                        if (!purchase.isAcknowledged) {
                            val acknowledgement = acknowledgePurchase(billingClient, purchase.purchaseToken)
                            if (acknowledgement.responseCode != BillingClient.BillingResponseCode.OK) {
                                return@withLock purchaseOutcomeForResponseCode(acknowledgement.responseCode)
                            }
                        }
                        PurchaseOutcome.Purchased(
                            productId = productId,
                            purchaseToken = purchase.purchaseToken,
                            purchasedAtMillis = purchase.purchaseTime,
                        )
                    }

                    else -> PurchaseOutcome.Unavailable(PlayBillingGateway.UNSUPPORTED_PURCHASE_STATE_REASON)
                }
            }

        suspend fun acknowledge(
            billingClient: BillingClient,
            purchaseToken: String,
        ): Result<Unit> =
            runCatching {
                val result = acknowledgePurchase(billingClient, purchaseToken)
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    throw PlayBillingGateway.BillingOperationException(result)
                }
            }

        suspend fun queryPurchases(billingClient: BillingClient): List<Purchase> {
            val result = billingClient.querySubscriptionPurchases()
            if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                throw PlayBillingGateway.BillingOperationException(result.billingResult)
            }
            return result.purchasesList.filter { purchase -> purchase.products.any { it in PlusSku.productIds } }
        }
    }

private fun ProductDetails.SubscriptionOfferDetails.hasRequiredPricePhase(sku: PlusSku): Boolean =
    pricingPhases.pricingPhaseList.any { phase -> phase.priceAmountMicros == 0L } == sku.requiresFreeOffer

private suspend fun BillingClient.queryPlusProductDetails(): PlayBillingGateway.ProductDetailsResult =
    suspendCancellableCoroutine { continuation ->
        val productList =
            PlusSku.productIds.map { productId ->
                QueryProductDetailsParams.Product
                    .newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            }
        queryProductDetailsAsync(
            QueryProductDetailsParams
                .newBuilder()
                .setProductList(productList)
                .build(),
        ) { billingResult, productDetailsResult ->
            if (continuation.isActive) {
                continuation.resume(
                    PlayBillingGateway.ProductDetailsResult(
                        billingResult = billingResult,
                        productDetailsList = productDetailsResult.productDetailsList,
                    ),
                )
            }
        }
    }

private suspend fun BillingClient.querySubscriptionPurchases(): PlayBillingGateway.PurchasesResult =
    suspendCancellableCoroutine { continuation ->
        queryPurchasesAsync(
            QueryPurchasesParams
                .newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
        ) { billingResult, purchasesList ->
            if (continuation.isActive) {
                continuation.resume(
                    PlayBillingGateway.PurchasesResult(
                        billingResult = billingResult,
                        purchasesList = purchasesList,
                    ),
                )
            }
        }
    }

private suspend fun acknowledgePurchase(
    billingClient: BillingClient,
    purchaseToken: String,
): BillingResult =
    suspendCancellableCoroutine { continuation ->
        billingClient.acknowledgePurchase(
            AcknowledgePurchaseParams
                .newBuilder()
                .setPurchaseToken(purchaseToken)
                .build(),
        ) { billingResult ->
            if (continuation.isActive) {
                continuation.resume(billingResult)
            }
        }
    }
