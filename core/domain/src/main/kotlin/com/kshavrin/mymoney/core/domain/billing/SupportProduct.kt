package com.kshavrin.mymoney.core.domain.billing

const val COFFEE_SMALL_PRODUCT_ID = "coffee_small"
const val COFFEE_LARGE_PRODUCT_ID = "coffee_large"

data class SupportProduct(
    val id: String,
    val formattedPrice: String,
    val title: String,
)
