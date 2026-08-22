package com.kshavrin.mymoney.feature.support.rewardedad

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.support.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RewardedAdScreenContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    // ─── Test helpers ────────────────────────────────────────────────────────────

    private fun string(
        id: Int,
        vararg args: Any,
    ): String = context.getString(id, *args)

    private fun defaultProgress(): RewardProgress = RewardProgress(progress = 3, required = 5, plusActive = false)

    private fun setContent(state: RewardedAdState) {
        composeTestRule.setContent {
            MyMoneyTheme {
                RewardedAdContent(
                    state = state,
                    onWatch = {},
                    onRetry = {},
                    onSignIn = {},
                )
            }
        }
    }

    // ─── Always-visible header ───────────────────────────────────────────────────

    @Test
    fun `title and rule are displayed in every state`() {
        setContent(RewardedAdState(status = RewardedAdStatus.Loading))

        composeTestRule.onNodeWithText(string(R.string.support_ads_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.support_ads_rule, 5)).assertIsDisplayed()
    }

    // ─── Acceptance matrix: scenario 1 — Unauthenticated ────────────────────────

    @Test
    fun `unauthenticated state shows sign in prompt and sign in action instead of watch button`() {
        setContent(RewardedAdState(status = RewardedAdStatus.Unauthenticated, reward = null))

        composeTestRule
            .onNodeWithText(string(R.string.support_ads_sign_in_required))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.support_ads_sign_in_action))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.support_ads_watch)).assertDoesNotExist()
    }

    // ─── Acceptance matrix: scenario 2 — Progress shown from server ──────────────

    @Test
    fun `ready state with progress shows progress text and enabled watch button`() {
        setContent(
            RewardedAdState(
                status = RewardedAdStatus.Ready,
                reward = defaultProgress(),
            ),
        )

        // Progress row is exposed as a contentDescription (clearAndSetSemantics).
        composeTestRule
            .onNodeWithContentDescription(string(R.string.support_ads_progress, 3, 5))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.support_ads_watch))
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    // ─── Acceptance matrix: scenario 3 — Region unavailable ──────────────────────

    @Test
    fun `region unavailable state shows unavailability explanation without loading or error labels`() {
        setContent(
            RewardedAdState(
                status = RewardedAdStatus.RegionUnavailable,
                reward = defaultProgress(),
            ),
        )

        composeTestRule
            .onNodeWithText(string(R.string.support_ads_region_unavailable))
            .assertIsDisplayed()
        assertNoAlternativeRewardStatus(R.string.support_ads_region_unavailable)
        composeTestRule.onNodeWithText(string(R.string.support_ads_loading)).assertDoesNotExist()
    }

    // ─── Acceptance matrix: scenario 4 — No fill ────────────────────────────────

    @Test
    fun `no fill state disables watch button and shows no fill explanation without error phrasing`() {
        setContent(
            RewardedAdState(
                status = RewardedAdStatus.NoFill,
                reward = defaultProgress(),
            ),
        )

        // Button must be present but NOT enabled.
        composeTestRule
            .onNodeWithText(string(R.string.support_ads_watch))
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeTestRule.onNodeWithText(string(R.string.support_ads_no_fill)).assertIsDisplayed()
        assertNoAlternativeRewardStatus(R.string.support_ads_no_fill)
    }

    // ─── Acceptance matrix: scenario 5 — Plus already active ─────────────────────

    @Test
    fun `plus active state keeps block visible with watch button enabled and plus explanation`() {
        setContent(
            RewardedAdState(
                status = RewardedAdStatus.Ready,
                reward = RewardProgress(progress = 3, required = 5, plusActive = true),
            ),
        )

        // Block must be visible — it is never hidden for Plus subscribers.
        composeTestRule
            .onNodeWithText(string(R.string.support_ads_plus_active))
            .assertIsDisplayed()
        // Watch button stays enabled; the flow is not silently blocked.
        composeTestRule
            .onNodeWithText(string(R.string.support_ads_watch))
            .assertIsDisplayed()
            .assertIsEnabled()
        // Progress row is also visible.
        composeTestRule
            .onNodeWithContentDescription(string(R.string.support_ads_progress, 3, 5))
            .assertIsDisplayed()
    }

    // ─── Acceptance matrix: scenario 6a — Awaiting confirmation ─────────────────

    @Test
    fun `awaiting confirmation state shows server wait message and never mentions credited or earned`() {
        setContent(
            RewardedAdState(
                status = RewardedAdStatus.AwaitingConfirmation,
                reward = defaultProgress(),
            ),
        )

        composeTestRule
            .onNodeWithText(string(R.string.support_ads_awaiting_confirmation))
            .assertIsDisplayed()
        assertNoAlternativeRewardStatus(R.string.support_ads_awaiting_confirmation)
    }

    // ─── Acceptance matrix: scenario 6b — Confirmation timeout ──────────────────

    @Test
    fun `confirmation timeout state shows timeout message and retry action`() {
        setContent(
            RewardedAdState(
                status = RewardedAdStatus.ConfirmationTimeout,
                reward = defaultProgress(),
            ),
        )

        composeTestRule
            .onNodeWithText(string(R.string.support_ads_confirmation_timeout))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.support_ads_retry))
            .assertIsDisplayed()
    }

    // ─── Acceptance matrix: scenario 7 — Interrupted ─────────────────────────────

    @Test
    fun `no fill state after interrupted view keeps progress intact and shows no error`() {
        // Represents the post-dismissal state: status re-resolved to NoFill, reward unchanged.
        setContent(
            RewardedAdState(
                status = RewardedAdStatus.NoFill,
                reward = defaultProgress(),
            ),
        )

        composeTestRule
            .onNodeWithContentDescription(string(R.string.support_ads_progress, 3, 5))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.support_ads_no_fill)).assertIsDisplayed()
        assertNoAlternativeRewardStatus(R.string.support_ads_no_fill)
    }

    // ─── Acceptance matrix: scenario 8 — Offline ─────────────────────────────────

    @Test
    fun `offline state shows network explanation and retry action`() {
        setContent(
            RewardedAdState(
                status = RewardedAdStatus.Offline,
                reward = defaultProgress(),
            ),
        )

        composeTestRule.onNodeWithText(string(R.string.support_ads_offline)).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.support_ads_retry))
            .assertIsDisplayed()
    }

    // ─── A11y: progress row semantics ────────────────────────────────────────────

    @Test
    fun `progress row content description equals the visible watched N of M text`() {
        // The RewardProgressRow uses clearAndSetSemantics so the LinearProgressIndicator
        // and Text are read as a single content-described node, not just a bar.
        setContent(
            RewardedAdState(
                status = RewardedAdStatus.Ready,
                reward = RewardProgress(progress = 2, required = 5, plusActive = false),
            ),
        )

        val expectedDescription = string(R.string.support_ads_progress, 2, 5)
        composeTestRule.onNodeWithContentDescription(expectedDescription).assertIsDisplayed()
    }

    // ─── STATE-001: re-arming after a confirmed view ─────────────────────────────
    // After a view is confirmed the block reloads the next ad (Rearming). It must keep the progress
    // row visible so the freshly-published counter is never hidden, show a neutral loading message,
    // and stop claiming the server has not yet confirmed the view.
    @Test
    fun `rearming state keeps the progress row and drops the awaiting-server message`() {
        setContent(
            RewardedAdState(
                status = RewardedAdStatus.Rearming,
                reward = defaultProgress(),
            ),
        )

        composeTestRule
            .onNodeWithContentDescription(string(R.string.support_ads_progress, 3, 5))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.support_ads_loading)).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.support_ads_awaiting_confirmation))
            .assertDoesNotExist()
    }

    // ─── Cross-state guard: Loading never gets stuck ──────────────────────────────

    @Test
    fun `loading state shows indicator text and no watch button`() {
        setContent(RewardedAdState(status = RewardedAdStatus.Loading, reward = null))

        composeTestRule.onNodeWithText(string(R.string.support_ads_loading)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.support_ads_watch)).assertDoesNotExist()
    }

    @Test
    fun `ready state does not surface the removed lifetime total counter`() {
        setContent(
            RewardedAdState(
                status = RewardedAdStatus.Ready,
                reward = RewardProgress(progress = 3, required = 5, plusActive = false, totalWatched = 42),
            ),
        )

        composeTestRule
            .onNodeWithText(string(R.string.support_ads_total_watched, 42))
            .assertDoesNotExist()
    }

    @Test
    fun `loading state without reward does not create a lifetime total placeholder`() {
        setContent(RewardedAdState(status = RewardedAdStatus.Loading, reward = null))

        composeTestRule.onNodeWithText(string(R.string.support_ads_total_watched, 0)).assertDoesNotExist()
    }

    private fun assertNoAlternativeRewardStatus(expectedResourceId: Int) {
        listOf(
            R.string.support_ads_loading,
            R.string.support_ads_sign_in_required,
            R.string.support_ads_plus_active,
            R.string.support_ads_no_fill,
            R.string.support_ads_region_unavailable,
            R.string.support_ads_offline,
            R.string.support_ads_awaiting_confirmation,
            R.string.support_ads_confirmation_timeout,
            R.string.support_ads_unavailable,
        ).filterNot { it == expectedResourceId }.forEach { resourceId ->
            composeTestRule.onNodeWithText(string(resourceId)).assertDoesNotExist()
        }
    }
}
