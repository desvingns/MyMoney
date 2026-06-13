package com.kshavrin.mymoney.core.designsystem.form

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.designsystem.R
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RunWith(AndroidJUnit4::class)
class DateHeaderUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `shows formatted date and pick date icon`() {
        val date = LocalDate.of(2026, 6, 6)

        composeTestRule.setContent {
            MyMoneyTheme {
                DateHeader(date = date, onClick = {})
            }
        }

        composeTestRule
            .onNodeWithText(dateLabel(date))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.amountfield_pick_date_cd))
            .assertIsDisplayed()
    }

    @Test
    fun `clicking the header invokes onClick`() {
        val date = LocalDate.of(2026, 6, 6)
        var clicks = 0

        composeTestRule.setContent {
            MyMoneyTheme {
                DateHeader(
                    date = date,
                    onClick = { clicks += 1 },
                )
            }
        }

        composeTestRule
            .onNodeWithText(dateLabel(date))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, clicks)
        }
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)

    private fun dateLabel(date: LocalDate): String {
        val locale =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext.resources.configuration.locales[0]
        return date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
    }
}
