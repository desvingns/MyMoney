package com.kshavrin.mymoney.core.ads.admob

const val NO_FILL_REGION_UNAVAILABLE_THRESHOLD = 3

class NoFillStreak(
    private val threshold: Int = NO_FILL_REGION_UNAVAILABLE_THRESHOLD,
) {
    private var consecutiveNoFillCount = 0

    init {
        require(threshold > 0)
    }

    fun recordNoFill(): Boolean {
        consecutiveNoFillCount += 1
        return consecutiveNoFillCount >= threshold
    }

    fun reset() {
        consecutiveNoFillCount = 0
    }
}
