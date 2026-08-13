package com.kshavrin.mymoney.core.billing

enum class PlusSku(
    val productId: String,
    val requiresFreeOffer: Boolean,
) {
    MONTHLY(productId = "plus_monthly", requiresFreeOffer = false),
    YEARLY(productId = "plus_yearly", requiresFreeOffer = true),
    ;

    companion object {
        val productIds: Set<String> = entries.mapTo(linkedSetOf()) { it.productId }

        fun fromProductId(productId: String): PlusSku? = entries.firstOrNull { it.productId == productId }
    }
}
