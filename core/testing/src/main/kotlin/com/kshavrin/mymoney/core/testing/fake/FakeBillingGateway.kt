package com.kshavrin.mymoney.core.testing.fake

import com.kshavrin.mymoney.core.domain.billing.BillingAvailability
import com.kshavrin.mymoney.core.domain.billing.BillingGateway
import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import com.kshavrin.mymoney.core.domain.billing.SupportProduct
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeBillingGateway : BillingGateway {
    private val availabilityState = MutableStateFlow<BillingAvailability>(BillingAvailability.Available)
    private val productsResult = MutableStateFlow<Result<List<SupportProduct>>>(Result.success(emptyList()))
    private val pendingPurchasesResult = MutableStateFlow<Result<List<PurchaseOutcome>>>(Result.success(emptyList()))
    private val purchaseOutcomes = mutableMapOf<String, MutableStateFlow<PurchaseOutcome>>()

    override fun availability(): Flow<BillingAvailability> = availabilityState.asStateFlow()

    override fun products(): Result<List<SupportProduct>> = productsResult.value

    override fun purchase(productId: String): Flow<PurchaseOutcome> =
        purchaseOutcomes
            .getOrPut(productId) {
                MutableStateFlow(defaultPurchaseOutcome(productId))
            }.asStateFlow()

    override fun resolvePendingPurchases(): Result<List<PurchaseOutcome>> = pendingPurchasesResult.value

    fun seedAvailability(availability: BillingAvailability) {
        availabilityState.value = availability
    }

    fun seedProducts(vararg products: SupportProduct) {
        productsResult.value = Result.success(products.toList())
    }

    fun seedProductsFailure(throwable: Throwable) {
        productsResult.value = Result.failure(throwable)
    }

    fun seedPurchaseOutcome(
        productId: String,
        outcome: PurchaseOutcome,
    ) {
        purchaseOutcomes.getOrPut(productId) { MutableStateFlow(outcome) }.value = outcome
    }

    fun seedPendingPurchases(vararg outcomes: PurchaseOutcome) {
        pendingPurchasesResult.value = Result.success(outcomes.toList())
    }

    fun seedPendingPurchasesFailure(throwable: Throwable) {
        pendingPurchasesResult.value = Result.failure(throwable)
    }

    private fun defaultPurchaseOutcome(productId: String): PurchaseOutcome =
        if (
            availabilityState.value == BillingAvailability.Available &&
                productsResult.value.getOrNull()?.any { product -> product.id == productId } == true
        ) {
            PurchaseOutcome.Purchased(
                productId = productId,
                purchaseToken = "$productId-purchase-token",
                purchasedAtMillis = 0L,
            )
        } else {
            PurchaseOutcome.Unavailable(reason = productId)
        }
}
