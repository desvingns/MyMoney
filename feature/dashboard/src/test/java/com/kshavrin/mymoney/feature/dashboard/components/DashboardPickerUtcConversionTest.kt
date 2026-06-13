package com.kshavrin.mymoney.feature.dashboard.components

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

class DashboardPickerUtcConversionTest {
    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(TEST_TIME_ZONE_ID))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `utc midnight single date millis stay on june 10 in america new york`() {
        val selectedDate = LocalDate.of(2026, 6, 10)
        val pickerMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val convertedDate = materialPickerUtcMillisToLocalDate(pickerMillis)

        val legacySystemDefaultDate =
            Instant
                .ofEpochMilli(pickerMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()

        assertEquals(LocalDate.of(2026, 6, 10), convertedDate)
        assertEquals(LocalDate.of(2026, 6, 9), legacySystemDefaultDate)
    }

    @Test
    fun `utc midnight range millis stay on june 10 through june 15 in america new york`() {
        val startDate = LocalDate.of(2026, 6, 10)
        val endDate = LocalDate.of(2026, 6, 15)
        val startMillis = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val endMillis = endDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val convertedStart = materialPickerUtcMillisToLocalDate(startMillis)
        val convertedEnd = materialPickerUtcMillisToLocalDate(endMillis)

        val legacySystemDefaultStart =
            Instant
                .ofEpochMilli(startMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        val legacySystemDefaultEnd =
            Instant
                .ofEpochMilli(endMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()

        assertEquals(LocalDate.of(2026, 6, 10), convertedStart)
        assertEquals(LocalDate.of(2026, 6, 15), convertedEnd)
        assertEquals(LocalDate.of(2026, 6, 9), legacySystemDefaultStart)
        assertEquals(LocalDate.of(2026, 6, 14), legacySystemDefaultEnd)
    }

    companion object {
        private const val TEST_TIME_ZONE_ID = "America/New_York"
    }
}
