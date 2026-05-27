package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.dashboard.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@RunWith(AndroidJUnit4::class)
class PeriodStripUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `pick a date emits a custom range after selecting two dates`() {
        var selectedPeriod: Period? = null
        val firstDay = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1)
        val secondDay = firstDay.plusDays(1)

        composeTestRule.setContent {
            MyMoneyTheme {
                PeriodStrip(
                    currentPeriod = Period.All,
                    onPeriodChange = { selectedPeriod = it },
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.period_pick_a_date))
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithText(dateLabel(firstDay)).performClick()
        composeTestRule.onNodeWithText(dateLabel(secondDay)).performClick()
        composeTestRule.onNodeWithText(targetString(R.string.period_apply)).performClick()

        composeTestRule.runOnIdle {
            assertTrue(selectedPeriod is Period.CustomRange)
            val range = selectedPeriod as Period.CustomRange
            assertEquals(firstDay, range.start)
            assertEquals(secondDay, range.end)
        }
    }

    @Test
    fun `ordinary period chips emit each corresponding period in order`() {
        val currentDate = LocalDate.now()
        val selectedPeriods = mutableListOf<Period>()
        val expectedSelections = listOf(
            R.string.period_today to Period.Day(currentDate),
            R.string.period_week to Period.Week(currentDate.with(DayOfWeek.MONDAY)),
            R.string.period_month to Period.Month(YearMonth.from(currentDate)),
            R.string.period_year to Period.Year(currentDate.year),
            R.string.period_all to Period.All,
        )

        composeTestRule.setContent {
            MyMoneyTheme {
                PeriodStrip(
                    currentPeriod = Period.All,
                    onPeriodChange = { selectedPeriods += it },
                )
            }
        }

        expectedSelections.forEach { (resourceId, _) ->
            composeTestRule
                .onNodeWithText(targetString(resourceId))
                .performScrollTo()
                .performClick()
        }

        composeTestRule.runOnIdle {
            assertEquals(expectedSelections.map { it.second }, selectedPeriods)
        }
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)

    private fun dateLabel(date: LocalDate): String {
        val locale = InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.configuration.locales[0]
        return date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", locale))
    }
}
