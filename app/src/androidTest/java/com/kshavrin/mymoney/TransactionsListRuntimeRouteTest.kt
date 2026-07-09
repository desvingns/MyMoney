package com.kshavrin.mymoney

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
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
import com.kshavrin.mymoney.feature.dashboard.DASHBOARD_TOP_BAR_PERIOD_TAG
import com.kshavrin.mymoney.feature.dashboard.DASHBOARD_TREND_CHART_TAG
import com.kshavrin.mymoney.feature.dashboard.components.OPERATIONS_SUMMARY_SHEET_TAG
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.kshavrin.mymoney.feature.dashboard.R as DashboardR
import com.kshavrin.mymoney.feature.onboarding.R as OnboardingR
import com.kshavrin.mymoney.feature.transactionslist.R as TransactionsListR

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TransactionsListRuntimeRouteTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun dashboardOperationsSummaryOpensTransactionsListAndBackReturnsToDashboard() {
        dismissOnboardingIfPresent()
        waitForDashboard()

        composeRule
            .onNodeWithTag(DASHBOARD_TREND_CHART_TAG)
            .performClick()

        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithTag(OPERATIONS_SUMMARY_SHEET_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule
            .onNodeWithTag(OPERATIONS_SUMMARY_SHEET_TAG)
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(targetString(DashboardR.string.operations_summary_open_transactions))
            .performClick()

        composeRule.waitUntil(TIMEOUT) {
            composeRule
                .onAllNodesWithContentDescription(targetString(TransactionsListR.string.transactions_list_search))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule
            .onNodeWithText(targetString(TransactionsListR.string.transactions_list_title))
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(targetString(TransactionsListR.string.transactions_list_back))
            .assertIsDisplayed()
            .performClick()

        composeRule.waitUntil(TIMEOUT) {
            composeRule
                .onAllNodesWithTag(DASHBOARD_TOP_BAR_PERIOD_TAG)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule
            .onNodeWithTag(DASHBOARD_TOP_BAR_PERIOD_TAG)
            .assertIsDisplayed()
        composeRule
            .onAllNodesWithTag(OPERATIONS_SUMMARY_SHEET_TAG)
            .assertCountEquals(0)
    }

    private fun dismissOnboardingIfPresent() {
        val skip = targetString(OnboardingR.string.onboarding_skip)
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithText(skip).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag(DASHBOARD_TOP_BAR_PERIOD_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithText(skip).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText(skip).performClick()
        }
    }

    private fun waitForDashboard() {
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithTag(DASHBOARD_TREND_CHART_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule
            .onNodeWithTag(DASHBOARD_TOP_BAR_PERIOD_TAG)
            .assertIsDisplayed()
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)

    private companion object {
        const val TIMEOUT = 20_000L
    }
}
