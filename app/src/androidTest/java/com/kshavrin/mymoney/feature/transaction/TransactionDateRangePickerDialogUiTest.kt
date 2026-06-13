package com.kshavrin.mymoney.feature.transaction

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RunWith(AndroidJUnit4::class)
class TransactionDateRangePickerDialogUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `applying without changing the selection emits the initial date`() {
        val pickedDates = mutableListOf<LocalDate>()
        var dismissCount = 0
        val initialDate = LocalDate.of(2026, 5, 17)

        composeTestRule.setContent {
            MyMoneyTheme {
                TransactionDatePickerDialog(
                    initialDate = initialDate,
                    onDatePicked = { pickedDates += it },
                    onDismiss = { dismissCount += 1 },
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.apply))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(initialDate), pickedDates)
            assertEquals(1, dismissCount)
        }
    }

    @Test
    fun `applying a picked date emits the selected concrete date`() {
        val pickedDates = mutableListOf<LocalDate>()
        var dismissCount = 0
        val initialDate = LocalDate.of(2026, 5, 17)
        val selectedDate = initialDate.plusDays(2)

        composeTestRule.setContent {
            MyMoneyTheme {
                TransactionDatePickerDialog(
                    initialDate = initialDate,
                    onDatePicked = { pickedDates += it },
                    onDismiss = { dismissCount += 1 },
                )
            }
        }

        composeTestRule.onNodeWithText(dateLabel(selectedDate)).performClick()
        composeTestRule
            .onNodeWithText(targetString(R.string.apply))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(selectedDate), pickedDates)
            assertEquals(1, dismissCount)
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
}
