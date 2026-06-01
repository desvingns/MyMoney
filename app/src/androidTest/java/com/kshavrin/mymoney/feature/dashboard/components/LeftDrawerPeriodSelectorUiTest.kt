package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.dashboard.DashboardEvent
import com.kshavrin.mymoney.feature.dashboard.DashboardState
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
class LeftDrawerPeriodSelectorUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `pick a date emits a custom range after selecting two dates`() {
        var selectedEvent: DashboardEvent? = null
        val firstDay = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1)
        val secondDay = firstDay.plusDays(1)

        composeTestRule.setContent {
            MyMoneyTheme {
                LeftDrawerContent(
                    state = DashboardState(period = Period.All, isLoading = false),
                    onEvent = { selectedEvent = it },
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.period_pick_a_date))
            .performClick()
        composeTestRule.onNodeWithText(dateLabel(firstDay)).performClick()
        composeTestRule.onNodeWithText(dateLabel(secondDay)).performClick()
        composeTestRule.onNodeWithText(targetString(R.string.period_apply)).performClick()

        composeTestRule.runOnIdle {
            assertTrue(selectedEvent is DashboardEvent.PeriodChanged)
            val range = (selectedEvent as DashboardEvent.PeriodChanged).period as Period.CustomRange
            assertEquals(firstDay, range.start)
            assertEquals(secondDay, range.end)
        }
    }

    @Test
    fun `ordinary period buttons emit each corresponding period in order`() {
        val currentDate = LocalDate.now()
        val selectedEvents = mutableListOf<DashboardEvent>()
        val expectedSelections = listOf(
            R.string.period_day to DashboardEvent.PeriodChanged(Period.Day(currentDate)),
            R.string.period_week to DashboardEvent.PeriodChanged(Period.Week(currentDate.with(DayOfWeek.MONDAY))),
            R.string.period_month to DashboardEvent.PeriodChanged(Period.Month(YearMonth.from(currentDate))),
            R.string.period_year to DashboardEvent.PeriodChanged(Period.Year(currentDate.year)),
            R.string.period_all to DashboardEvent.PeriodChanged(Period.All),
        )

        composeTestRule.setContent {
            MyMoneyTheme {
                LeftDrawerContent(
                    state = DashboardState(period = Period.All, isLoading = false),
                    onEvent = { selectedEvents += it },
                )
            }
        }

        expectedSelections.forEach { (resourceId, _) ->
            composeTestRule
                .onNodeWithText(targetString(resourceId))
                .performClick()
        }

        composeTestRule.runOnIdle {
            assertEquals(expectedSelections.map { it.second }, selectedEvents)
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
