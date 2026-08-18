package com.kshavrin.mymoney.core.ads.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdRewardBackoffTest {
    @Test
    fun `default delays are strictly monotonic and fit below the thirty second budget`() {
        val backoff = AdRewardBackoff()

        assertEquals(listOf(250L, 500L, 1_000L, 2_000L, 4_000L, 8_000L, 12_000L), backoff.delaysMillis)
        assertTrue(backoff.delaysMillis.zipWithNext().all { (current, next) -> current < next })
        assertEquals(27_750L, backoff.delaysMillis.sum())
        assertEquals(30_000L, backoff.maximumWaitMillis)
        assertTrue(backoff.delaysMillis.sum() < backoff.maximumWaitMillis)
    }

    // A reward is durable on the server ~250 ms after the ad ends. The first poll runs before any
    // delay; the tightened head must then re-poll within ~1 s so a fast reward surfaces almost
    // immediately instead of waiting a full second.
    @Test
    fun `head of the schedule re-polls within one second so a fast reward surfaces quickly`() {
        val backoff = AdRewardBackoff()

        assertTrue(backoff.delaysMillis.first() <= 250L)
        assertTrue(backoff.delaysMillis.take(2).sum() <= 1_000L)
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
