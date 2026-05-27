package com.kshavrin.mymoney.feature.dashboard

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardContentUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `expense fab stays enabled in empty dashboard and emits minus event`() {
        val capturedEvents = mutableListOf<DashboardEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(isLoading = false),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.fab_expense))
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(DashboardEvent.MinusFabClicked), capturedEvents)
        }
    }

    @Test
    fun `income fab stays enabled in empty dashboard and emits plus event`() {
        val capturedEvents = mutableListOf<DashboardEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(isLoading = false),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.fab_income))
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(DashboardEvent.PlusFabClicked), capturedEvents)
        }
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
