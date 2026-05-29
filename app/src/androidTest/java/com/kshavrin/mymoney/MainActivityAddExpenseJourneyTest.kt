package com.kshavrin.mymoney

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.feature.dashboard.R as DashboardR
import com.kshavrin.mymoney.feature.onboarding.R as OnboardingR
import com.kshavrin.mymoney.feature.transaction.R as TransactionR
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * J1 — the highest-value end-to-end journey (AS-2, AS-4, TDD §4.6 AC6).
 *
 * Fresh in-memory DB + cleared DataStore => app starts at onboarding. Skip to the dashboard,
 * tap the expense FAB, key `1 2 + 3 =` (=> 15) on the calculator, choose a seeded category,
 * and confirm the populated dashboard balance pill reflects the new expense.
 *
 * Seeded category names are English literals in InitialDataSeeder regardless of locale, so
 * "Food" is a stable matcher; every localized control is looked up via targetString.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivityAddExpenseJourneyTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun addExpenseJourneyUpdatesDashboardBalance() {
        // Onboarding -> Dashboard
        val skip = targetString(OnboardingR.string.onboarding_skip)
        waitForText(skip)
        composeRule.onNodeWithText(skip).performClick()

        // Dashboard -> Add expense form
        val expenseFab = targetString(DashboardR.string.fab_expense)
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithContentDescription(expenseFab).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription(expenseFab).performClick()

        // Calculator: 1 2 + 3 = -> 15. Keypad keys are clickable; the running display is not,
        // so `hasText + hasClickAction` targets the key and never the amount readout.
        val chooseCategory = targetString(TransactionR.string.choose_category_cta)
        waitForText(chooseCategory)
        listOf("1", "2", "+", "3", "=").forEach { label ->
            composeRule.onNode(hasText(label) and hasClickAction()).performClick()
        }

        // Choose category -> picker -> pick a seeded category -> auto-save -> pop to dashboard
        composeRule.onNode(hasText(chooseCategory) and hasClickAction()).performClick()
        waitForText("Food")
        composeRule.onNodeWithText("Food").performClick()

        // Returning to the dashboard proves the picker return + auto-save + pop happened.
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithContentDescription(expenseFab).fetchSemanticsNodes().isNotEmpty()
        }

        // The balance pill now reflects the 15 expense (net = -15).
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithTag(BALANCE_PILL_TAG)
                .fetchSemanticsNodes()
                .any { node ->
                    node.config.getOrNull(SemanticsProperties.Text)
                        ?.any { it.text.contains("15") } == true
                }
        }
        composeRule.onNodeWithTag(BALANCE_PILL_TAG)
            .assertIsDisplayed()
            .assertTextContains("15", substring = true)
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)

    private companion object {
        const val TIMEOUT = 20_000L
        const val BALANCE_PILL_TAG = "dashboard_balance_pill"
    }
}
