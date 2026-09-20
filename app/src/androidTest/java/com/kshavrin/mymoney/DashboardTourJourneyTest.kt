package com.kshavrin.mymoney

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.navigation.OnboardingFlagModule
import com.kshavrin.mymoney.navigation.ShowOnboarding
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.kshavrin.mymoney.feature.dashboard.R as DashboardR

/**
 * SPEC-05 — end-to-end spotlight tour journey on the real dashboard.
 *
 * The debug build ships SHOW_ONBOARDING = false, so the tour is enabled here by uninstalling
 * OnboardingFlagModule and binding @ShowOnboarding = true. A fresh test DataStore
 * (TestDataStoreModule, UUID file) means onboardingCompletedAt starts null, so the tour is raised.
 */
@HiltAndroidTest
@UninstallModules(OnboardingFlagModule::class)
@RunWith(AndroidJUnit4::class)
class DashboardTourJourneyTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @BindValue
    @JvmField
    @ShowOnboarding
    val showOnboarding: Boolean = true

    @Test
    fun fullTourNextThroughDoneClosesTour() {
        hiltRule.inject()

        // Step 1 — actions row.
        waitForText(str(DashboardR.string.dashboard_tour_actions_title))
        composeRule.onNodeWithText(str(DashboardR.string.dashboard_tour_next)).performClick()

        // Step 2 — left panel (auto-opens after the panel-open delay).
        waitForText(str(DashboardR.string.dashboard_tour_left_title))
        composeRule.onNodeWithText(str(DashboardR.string.dashboard_tour_next)).performClick()

        // Step 3 — categories.
        waitForText(str(DashboardR.string.dashboard_tour_categories_title))
        composeRule.onNodeWithText(str(DashboardR.string.dashboard_tour_next)).performClick()

        // Step 4 — support; last step shows "Done".
        waitForText(str(DashboardR.string.dashboard_tour_support_title))
        composeRule.onNodeWithText(str(DashboardR.string.dashboard_tour_done)).performClick()

        // Tour gone: the "Skip all" control no longer exists.
        waitUntilGone(str(DashboardR.string.dashboard_tour_skip_all))
        composeRule.onNodeWithText(str(DashboardR.string.dashboard_tour_actions_title)).assertDoesNotExist()
    }

    @Test
    fun skipAllClosesTour() {
        hiltRule.inject()

        waitForText(str(DashboardR.string.dashboard_tour_actions_title))
        composeRule.onNodeWithText(str(DashboardR.string.dashboard_tour_next)).performClick()
        waitForText(str(DashboardR.string.dashboard_tour_left_title))

        composeRule.onNodeWithText(str(DashboardR.string.dashboard_tour_skip_all)).performClick()
        waitUntilGone(str(DashboardR.string.dashboard_tour_skip_all))
    }

    @Test
    fun systemBackActsAsSkipAll() {
        hiltRule.inject()

        waitForText(str(DashboardR.string.dashboard_tour_actions_title))
        Espresso.pressBack()
        waitUntilGone(str(DashboardR.string.dashboard_tour_skip_all))
    }

    @Test
    fun realIncomeTapPausesTourThenResumeAdvances() {
        hiltRule.inject()

        waitForText(str(DashboardR.string.dashboard_tour_actions_title))

        // Tap the highlighted income FAB — its real action navigates away and pauses the tour.
        composeRule.onNodeWithContentDescription(str(DashboardR.string.fab_income_content_description)).performClick()
        waitUntilGone(str(DashboardR.string.dashboard_tour_actions_title))

        // Return to the dashboard: ON_RESUME resumes the tour on the next step (left panel).
        Espresso.pressBack()
        waitForText(str(DashboardR.string.dashboard_tour_left_title))
    }

    private fun str(resId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)

    private fun waitForText(text: String) {
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitUntilGone(text: String) {
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
        }
    }

    private companion object {
        const val TIMEOUT = 20_000L
    }
}
