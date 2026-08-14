package com.kshavrin.mymoney.core.ads.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdRewardBackoffTest {
    @Test
    fun `default delays are strictly monotonic and fit below the thirty second budget`() {
        val backoff = AdRewardBackoff()

        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 12_000L), backoff.delaysMillis)
        assertTrue(backoff.delaysMillis.zipWithNext().all { (current, next) -> current < next })
        assertEquals(27_000L, backoff.delaysMillis.sum())
        assertEquals(30_000L, backoff.maximumWaitMillis)
        assertTrue(backoff.delaysMillis.sum() < backoff.maximumWaitMillis)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a non monotonic schedule`() {
        AdRewardBackoff(delaysMillis = listOf(1_000L, 1_000L), maximumWaitMillis = 3_000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a schedule whose total delay reaches the budget`() {
        AdRewardBackoff(delaysMillis = listOf(1_000L, 2_000L), maximumWaitMillis = 3_000L)
    }
}
