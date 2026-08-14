package com.kshavrin.mymoney.core.ads.admob

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoFillStreakTest {
    @Test
    fun `reaches the region threshold only after consecutive no fill results`() {
        val streak = NoFillStreak(threshold = 3)

        assertFalse(streak.recordNoFill())
        assertFalse(streak.recordNoFill())
        assertTrue(streak.recordNoFill())
        assertTrue(streak.recordNoFill())

        streak.reset()

        assertFalse(streak.recordNoFill())

        val restarted = NoFillStreak(threshold = 3)

        assertFalse(restarted.recordNoFill())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a non-positive threshold`() {
        NoFillStreak(threshold = 0)
    }
}
