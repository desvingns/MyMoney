package com.kshavrin.mymoney.feature.support.rewardedad

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.support.R
import org.junit.Assert.assertEquals
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
        composeTestRule
            .onNodeWithContentDescription(string(R.string.support_image_ads_description))
            .assertIsDisplayed()
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

    @Test
    fun `unauthenticated sign in action invokes sign in callback`() {
        var signInClicks = 0
        composeTestRule.setContent {
            MyMoneyTheme {
                RewardedAdContent(
                    state = RewardedAdState(status = RewardedAdStatus.Unauthenticated),
                    onWatch = {},
                    onRetry = {},
                    onSignIn = { signInClicks++ },
                )
            }
        }

        composeTestRule
            .onNodeWithText(string(R.string.support_ads_sign_in_action))
            .performClick()

        assertEquals(1, signInClicks)
    }

    @Test
    fun `SECURITY-001 unauthenticated state ignores stale reward progress`() {
        setContent(
            RewardedAdState(
                status = RewardedAdStatus.Unauthenticated,
                reward = RewardProgress(progress = 3, required = 5, plusActive = false),
            ),
        )

        composeTestRule
            .onNodeWithText(string(R.string.support_ads_sign_in_action))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(string(R.string.support_ads_progress, 3, 5))
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithContentDescription(string(R.string.support_ads_progress, 0, 5))
            .assertIsDisplayed()
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

    @Test
    fun `ready watch CTA invokes onWatch callback`() {
        var watchClicks = 0
        var retryClicks = 0
        composeTestRule.setContent {
            MyMoneyTheme {
                RewardedAdContent(
                    state = RewardedAdState(
                        status = RewardedAdStatus.Ready,
                        reward = defaultProgress(),
                    ),
                    onWatch = { watchClicks++ },
                    onRetry = { retryClicks++ },
                    onSignIn = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(string(R.string.support_ads_watch))
            .assertIsEnabled()
            .performClick()

        assertEquals(1, watchClicks)
        assertEquals(0, retryClicks)
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

    @Test
    fun `unavailable state shows unavailable status and no actionable watch CTA`() {
        setContent(
            RewardedAdState(
                status = RewardedAdStatus.Unavailable,
                reward = RewardProgress(progress = 3, required = 5, plusActive = false),
            ),
        )

        composeTestRule.onNodeWithText(string(R.string.support_ads_unavailable)).assertIsDisplayed()
        assertNoAlternativeRewardStatus(R.string.support_ads_unavailable)
        composeTestRule
            .onNodeWithText(string(R.string.support_ads_watch))
            .assertIsDisplayed()
            .assertIsNotEnabled()
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

    @Test
    fun `offline and confirmation timeout retry CTAs invoke onRetry callback`() {
        var watchClicks = 0
        var retryClicks = 0
        val state =
            mutableStateOf(
                RewardedAdState(
                    status = RewardedAdStatus.Offline,
                    reward = defaultProgress(),
                ),
            )
        composeTestRule.setContent {
            MyMoneyTheme {
                RewardedAdContent(
                    state = state.value,
                    onWatch = { watchClicks++ },
                    onRetry = { retryClicks++ },
                    onSignIn = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(string(R.string.support_ads_retry))
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            state.value =
                RewardedAdState(
                    status = RewardedAdStatus.ConfirmationTimeout,
                    reward = defaultProgress(),
                )
        }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(string(R.string.support_ads_retry))
            .assertIsEnabled()
            .performClick()

        assertEquals(0, watchClicks)
        assertEquals(2, retryClicks)
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
    fun `loading state shows one disabled watch button and loading status inside the card`() {
        setContent(RewardedAdState(status = RewardedAdStatus.Loading, reward = null))

        composeTestRule
            .onAllNodesWithText(string(R.string.support_ads_watch))
            .assertCountEquals(1)
        composeTestRule
            .onNodeWithText(string(R.string.support_ads_watch))
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeTestRule
            .onNodeWithContentDescription(string(R.string.support_ads_progress, 0, 5))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.support_ads_loading)).assertIsDisplayed()
    }

    @Test
    fun `SECURITY-002 loading state ignores stale reward progress and keeps disabled watch CTA`() {
        setContent(
            RewardedAdState(
                status = RewardedAdStatus.Loading,
                reward = RewardProgress(progress = 3, required = 5, plusActive = true),
            ),
        )

        composeTestRule
            .onNodeWithContentDescription(string(R.string.support_ads_progress, 3, 5))
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithContentDescription(string(R.string.support_ads_progress, 0, 5))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.support_ads_loading)).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.support_ads_watch))
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun `disabled watch CTAs in no fill and other disabled statuses ignore clicks`() {
        var watchClicks = 0
        var retryClicks = 0
        val state =
            mutableStateOf(
                RewardedAdState(
                    status = RewardedAdStatus.NoFill,
                    reward = defaultProgress(),
                ),
            )
        composeTestRule.setContent {
            MyMoneyTheme {
                RewardedAdContent(
                    state = state.value,
                    onWatch = { watchClicks++ },
                    onRetry = { retryClicks++ },
                    onSignIn = {},
                )
            }
        }

        listOf(
            RewardedAdStatus.NoFill,
            RewardedAdStatus.Loading,
            RewardedAdStatus.RegionUnavailable,
            RewardedAdStatus.AwaitingConfirmation,
            RewardedAdStatus.Rearming,
            RewardedAdStatus.Unavailable,
        ).forEach { status ->
            composeTestRule.runOnIdle {
                state.value = RewardedAdState(status = status, reward = defaultProgress())
            }
            composeTestRule.waitForIdle()
            composeTestRule
                .onNodeWithText(string(R.string.support_ads_watch))
                .assertIsNotEnabled()
                .performClick()
        }

        assertEquals(0, watchClicks)
        assertEquals(0, retryClicks)
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

    @Test
    fun `all ten ad statuses cover both plus inactive and active cells`() {
        val state =
            mutableStateOf(
                RewardedAdState(
                    status = RewardedAdStatus.Loading,
                    reward = RewardProgress(progress = 3, required = 5, plusActive = false),
                ),
            )
        composeTestRule.setContent {
            MyMoneyTheme {
                RewardedAdContent(
                    state = state.value,
                    onWatch = {},
                    onRetry = {},
                    onSignIn = {},
                )
            }
        }

        val frozenStatuses =
            listOf<Pair<RewardedAdStatus, Int?>>(
                RewardedAdStatus.Loading to R.string.support_ads_loading,
                RewardedAdStatus.Unauthenticated to R.string.support_ads_sign_in_required,
                RewardedAdStatus.Ready to null,
                RewardedAdStatus.NoFill to R.string.support_ads_no_fill,
                RewardedAdStatus.RegionUnavailable to R.string.support_ads_region_unavailable,
                RewardedAdStatus.Offline to R.string.support_ads_offline,
                RewardedAdStatus.AwaitingConfirmation to R.string.support_ads_awaiting_confirmation,
                RewardedAdStatus.ConfirmationTimeout to R.string.support_ads_confirmation_timeout,
                RewardedAdStatus.Rearming to R.string.support_ads_loading,
                RewardedAdStatus.Unavailable to R.string.support_ads_unavailable,
            )

        frozenStatuses.forEach { (status, inactiveStatusResource) ->
            listOf(false, true).forEach { plusActive ->
                composeTestRule.runOnIdle {
                    state.value =
                        RewardedAdState(
                            status = status,
                            reward = RewardProgress(progress = 3, required = 5, plusActive = plusActive),
                        )
                }
                composeTestRule.waitForIdle()

                val expectedProgress =
                    if (status == RewardedAdStatus.Loading || status == RewardedAdStatus.Unauthenticated) {
                        0
                    } else {
                        3
                    }
                composeTestRule
                    .onNodeWithContentDescription(string(R.string.support_ads_progress, expectedProgress, 5))
                    .assertIsDisplayed()

                val expectedStatusResource =
                    if (status == RewardedAdStatus.Ready && plusActive) {
                        R.string.support_ads_plus_active
                    } else {
                        inactiveStatusResource
                    }
                assertOnlyRewardStatus(expectedStatusResource)

                val expectedActionResource =
                    when (status) {
                        RewardedAdStatus.Unauthenticated -> R.string.support_ads_sign_in_action
                        RewardedAdStatus.Offline,
                        RewardedAdStatus.ConfirmationTimeout,
                        -> R.string.support_ads_retry
                        else -> R.string.support_ads_watch
                    }
                val expectedActionEnabled =
                    status == RewardedAdStatus.Unauthenticated ||
                        status == RewardedAdStatus.Offline ||
                        status == RewardedAdStatus.ConfirmationTimeout ||
                        status == RewardedAdStatus.Ready

                composeTestRule
                    .onNodeWithText(string(expectedActionResource))
                    .assertIsDisplayed()
                    .let { action ->
                        if (expectedActionEnabled) {
                            action.assertIsEnabled()
                        } else {
                            action.assertIsNotEnabled()
                        }
                    }
                listOf(
                    R.string.support_ads_sign_in_action,
                    R.string.support_ads_retry,
                    R.string.support_ads_watch,
                ).filterNot { it == expectedActionResource }.forEach { resourceId ->
                    composeTestRule.onNodeWithText(string(resourceId)).assertDoesNotExist()
                }
            }
        }
    }

    private fun assertOnlyRewardStatus(expectedResourceId: Int?) {
        val statusResources =
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
            )
        statusResources.forEach { resourceId ->
            if (resourceId == expectedResourceId) {
                composeTestRule.onNodeWithText(string(resourceId)).assertIsDisplayed()
            } else {
                composeTestRule.onNodeWithText(string(resourceId)).assertDoesNotExist()
            }
        }
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
