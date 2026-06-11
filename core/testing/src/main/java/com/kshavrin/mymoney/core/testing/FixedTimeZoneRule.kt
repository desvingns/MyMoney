package com.kshavrin.mymoney.core.testing

import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.ZoneId
import java.util.TimeZone

class FixedTimeZoneRule(
    private val zoneId: ZoneId,
) : TestWatcher() {
    private lateinit var originalTimeZone: TimeZone

    override fun starting(description: Description) {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
    }

    override fun finished(description: Description) {
        TimeZone.setDefault(originalTimeZone)
    }
}
