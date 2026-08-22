package com.kshavrin.mymoney.core.domain.supporter

data class SupporterState(
    val badgeEarned: Boolean,
    val purchaseCount: Int,
    val smallCoffeeCount: Int = 0,
    val largeCoffeeCount: Int = 0,
)
