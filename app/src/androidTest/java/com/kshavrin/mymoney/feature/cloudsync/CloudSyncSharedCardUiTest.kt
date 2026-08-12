package com.kshavrin.mymoney.feature.cloudsync

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.datastore.CloudBinding
import com.kshavrin.mymoney.core.datastore.CloudProvider
import com.kshavrin.mymoney.core.sync.shared.SharedRealtimeStatus
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Connected-device smoke test for the Shared mode card in [CloudSyncContent].
 *
 * Covers the highest-value visual state: mutual exclusivity of the Shared card
 * when another provider (Dropbox) is active — the sign-in and setup action
 * buttons must NOT appear, only the leave-first hint text, and vice versa.
 *
 * Device requirement: Pixel 5 API 34 connected emulator (serial emulator-5556).
 */
@RunWith(AndroidJUnit4::class)
class CloudSyncSharedCardUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `Shared card shows sign-in button when no provider is active and user is signed out`() {
        val events = mutableListOf<CloudSyncEvent>()
        composeTestRule.setContent {
            MyMoneyTheme {
                CloudSyncContent(
                    state = CloudSyncState(
                        shared = SharedCardState(signedIn = false, active = false),
                    ),
                    onEvent = events::add,
                )
            }
        }

        // Sign-in button is visible and tappable
        composeTestRule
            .onNodeWithTag("cloud_sync_shared_sign_in")
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                "Click must emit SharedSignInClicked",
                listOf(CloudSyncEvent.SharedSignInClicked),
                events,
            )
        }
    }

    @Test
    fun `Shared card shows leave-first hint and hides action buttons when Dropbox is the active binding`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                CloudSyncContent(
                    state = CloudSyncState(
                        binding = CloudBinding(CloudProvider.Dropbox, "acct", "user@dropbox.com"),
                        shared = SharedCardState(signedIn = true, active = false),
                    ),
                    onEvent = {},
                )
            }
        }

        // The mutual-exclusivity hint string must be shown
        composeTestRule
            .onNodeWithText(targetString(R.string.sync_shared_other_provider_active))
            .assertIsDisplayed()

        // Sign-in and setup action buttons must NOT appear when another provider is active
        composeTestRule.onNodeWithTag("cloud_sync_shared_sign_in").assertDoesNotExist()
        composeTestRule.onNodeWithTag("cloud_sync_shared_setup").assertDoesNotExist()
    }

    @Test
    fun `Shared card shows sync-now leave and optional conflicts button when Shared is active`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                CloudSyncContent(
                    state = CloudSyncState(
                        binding = CloudBinding(CloudProvider.Shared, "ws-1", "Family Budget"),
                        shared = SharedCardState(
                            signedIn = true,
                            active = true,
                            workspaceName = "Family Budget",
                            conflictCount = 2,
                        ),
                    ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("cloud_sync_shared_sync_now").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cloud_sync_shared_leave").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("cloud_sync_shared_conflicts").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("cloud_sync_shared_disconnect").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `Shared card exposes realtime error retry action on device`() {
        val events = mutableListOf<CloudSyncEvent>()
        composeTestRule.setContent {
            MyMoneyTheme {
                CloudSyncContent(
                    state =
                        CloudSyncState(
                            binding = CloudBinding(CloudProvider.Shared, "ws-1", "Family Budget"),
                            shared =
                                SharedCardState(
                                    signedIn = true,
                                    active = true,
                                    realtimeStatus = SharedRealtimeStatus.Error,
                                ),
                        ),
                    onEvent = events::add,
                )
            }
        }

        composeTestRule
            .onNodeWithTag("cloud_sync_shared_realtime_status")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.sync_shared_status_error))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("cloud_sync_shared_realtime_retry")
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(CloudSyncEvent.SharedRetryRealtimeClicked), events)
        }
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
