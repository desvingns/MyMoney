package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
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

@RunWith(AndroidJUnit4::class)
class PeriodStripUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `pick a date emits a custom range after selecting two dates`() {
        var selectedPeriod: Period? = null

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
        composeTestRule.onNode(hasText("1") and hasClickAction()).performClick()
        composeTestRule.onNode(hasText("2") and hasClickAction()).performClick()
        composeTestRule.onNodeWithText(targetString(R.string.period_apply)).performClick()

        composeTestRule.runOnIdle {
            assertTrue(selectedPeriod is Period.CustomRange)
            val range = selectedPeriod as Period.CustomRange
            assertEquals(1, range.start.dayOfMonth)
            assertEquals(2, range.end.dayOfMonth)
        }
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
