package com.kshavrin.mymoney.core.ads.data

class AdRewardBackoff(
    val delaysMillis: List<Long> = DEFAULT_DELAYS_MILLIS,
    val maximumWaitMillis: Long = MAXIMUM_WAIT_MILLIS,
) {
    init {
        require(maximumWaitMillis > 0L)
        require(delaysMillis.all { it >= 0L })
        require(delaysMillis.zipWithNext().all { (current, next) -> current < next })
        require(delaysMillis.fold(0L, Long::plus) < maximumWaitMillis)
    }

    companion object {
        const val MAXIMUM_WAIT_MILLIS = 30_000L
        val DEFAULT_DELAYS_MILLIS = listOf(1_000L, 2_000L, 4_000L, 8_000L, 12_000L)
    }
}
