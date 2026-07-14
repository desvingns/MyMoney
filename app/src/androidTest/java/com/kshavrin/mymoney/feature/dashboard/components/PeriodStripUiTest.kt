package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.dashboard.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.TimeZone
import com.kshavrin.mymoney.test.assertTouchHeightIsAtLeast
import com.kshavrin.mymoney.test.assertTouchWidthIsAtLeast

@RunWith(AndroidJUnit4::class)
class PeriodStripUiTest {
    @get:Rule
    val composeTestRule = createComposeRule().apply { enableAccessibilityChecks() }

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
    fun `pick a date keeps the selected custom range calendar days in america new york`() {
        var selectedPeriod: Period? = null
        val currentMonth = LocalDate.now()
        val startDate = currentMonth.withDayOfMonth(10)
        val endDate = currentMonth.withDayOfMonth(15)

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
        composeTestRule.onNodeWithText(dateLabel(startDate)).performClick()
        composeTestRule.onNodeWithText(dateLabel(endDate)).performClick()
        composeTestRule.onNodeWithText(targetString(R.string.period_apply)).performClick()

        composeTestRule.runOnIdle {
            assertEquals(Period.CustomRange(startDate, endDate), selectedPeriod)
        }
    }

    @Test
    fun `every period chip has a 48dp touch target`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                PeriodStrip(
                    currentPeriod = Period.All,
                    onPeriodChange = {},
                )
            }
        }

        listOf(
            R.string.period_today,
            R.string.period_week,
            R.string.period_month,
            R.string.period_year,
            R.string.period_all,
            R.string.period_pick_a_date,
        ).forEach { resourceId ->
            composeTestRule
                .onNodeWithText(targetString(resourceId))
                .performScrollTo()
                .assertTouchWidthIsAtLeast(48.dp)
                .assertTouchHeightIsAtLeast(48.dp)
        }
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)

    private fun dateLabel(date: LocalDate): String {
        val locale =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext.resources.configuration.locales[0]
        return date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", locale))
    }

    companion object {
        private const val TEST_TIME_ZONE_ID = "America/New_York"
    }
}
