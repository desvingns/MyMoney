package com.kshavrin.mymoney.feature.cloudsync

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.sync.SyncTarget
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class CloudSyncContentUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `back button emits back event`() {
        val events = mutableListOf<CloudSyncEvent>()

        setContent(onEvent = events::add)

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.sync_back))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(CloudSyncEvent.BackClicked), events)
        }
    }

    @Test
    fun `disconnected provider buttons emit connect and keep sync disabled`() {
        val events = mutableListOf<CloudSyncEvent>()

        setContent(
            state = CloudSyncState(
                dropbox = TargetCardState(
                    target = SyncTarget.Dropbox,
                    enabled = true,
                ),
                drive = TargetCardState(
                    target = SyncTarget.GoogleDrive,
                    enabled = false,
                ),
            ),
            onEvent = events::add,
        )

        composeTestRule
            .onNodeWithText(targetString(R.string.sync_dropbox_section))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.sync_gdrive_section))
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag(providerControlTag(SyncTarget.Dropbox, "connect"))
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeTestRule
            .onNodeWithTag(providerControlTag(SyncTarget.GoogleDrive, "connect"))
            .performScrollTo()
            .assertIsNotEnabled()

        composeTestRule
            .onNodeWithTag(providerControlTag(SyncTarget.Dropbox, "sync_now"))
            .performScrollTo()
            .assertIsNotEnabled()
        composeTestRule
            .onNodeWithTag(providerControlTag(SyncTarget.GoogleDrive, "sync_now"))
            .performScrollTo()
            .assertIsNotEnabled()

        composeTestRule.runOnIdle {
            assertEquals(listOf(CloudSyncEvent.ConnectClicked(SyncTarget.Dropbox)), events)
        }
    }

    @Test
    fun `connected provider buttons emit disconnect and sync events`() {
        val events = mutableListOf<CloudSyncEvent>()

        setContent(
            state = CloudSyncState(
                dropbox = TargetCardState(
                    target = SyncTarget.Dropbox,
                    connected = true,
                    accountLabel = "dropbox@example.test",
                ),
                drive = TargetCardState(
                    target = SyncTarget.GoogleDrive,
                    connected = true,
                    accountLabel = "drive@example.test",
                ),
            ),
            onEvent = events::add,
        )

        composeTestRule
            .onNodeWithText("dropbox@example.test")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("drive@example.test")
            .performScrollTo()
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag(providerControlTag(SyncTarget.Dropbox, "disconnect"))
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeTestRule
            .onNodeWithTag(providerControlTag(SyncTarget.Dropbox, "sync_now"))
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeTestRule
            .onNodeWithTag(providerControlTag(SyncTarget.GoogleDrive, "disconnect"))
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeTestRule
            .onNodeWithTag(providerControlTag(SyncTarget.GoogleDrive, "sync_now"))
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(
                    CloudSyncEvent.DisconnectClicked(SyncTarget.Dropbox),
                    CloudSyncEvent.SyncNowClicked(SyncTarget.Dropbox),
                    CloudSyncEvent.DisconnectClicked(SyncTarget.GoogleDrive),
                    CloudSyncEvent.SyncNowClicked(SyncTarget.GoogleDrive),
                ),
                events,
            )
        }
    }

    @Test
    fun `auto sync switch emits toggle event`() {
        val events = mutableListOf<CloudSyncEvent>()

        setContent(onEvent = events::add)

        composeTestRule
            .onNodeWithText(targetString(R.string.sync_auto_toggle))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(AUTO_SYNC_TAG)
            .assertIsOff()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(CloudSyncEvent.AutoSyncToggled(true)), events)
        }
    }

    @Test
    fun `error banner dismiss button emits dismiss event`() {
        val events = mutableListOf<CloudSyncEvent>()

        setContent(
            state = CloudSyncState(errorBannerRes = R.string.sync_err_network),
            onEvent = events::add,
        )

        composeTestRule
            .onNodeWithText(targetString(R.string.sync_err_network))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.sync_dismiss))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(CloudSyncEvent.DismissError), events)
        }
    }

    @Test
    fun `conflict dialog buttons emit keep remote and keep local events`() {
        val events = mutableListOf<CloudSyncEvent>()
        val remoteMs = Instant.parse("2026-05-28T09:30:00Z").toEpochMilli()
        val localMs = Instant.parse("2026-05-27T18:15:00Z").toEpochMilli()

        setContent(
            state = CloudSyncState(
                conflict = ConflictPrompt(
                    target = SyncTarget.Dropbox,
                    remoteMs = remoteMs,
                    localMs = localMs,
                ),
            ),
            onEvent = events::add,
        )

        composeTestRule
            .onNodeWithText(targetString(R.string.sync_conflict_title))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.sync_conflict_remote, formattedTimestamp(remoteMs)))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.sync_conflict_local, formattedTimestamp(localMs)))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.sync_keep_remote))
            .performClick()
        composeTestRule
            .onNodeWithText(targetString(R.string.sync_keep_local))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(
                    CloudSyncEvent.ConflictKeepRemote,
                    CloudSyncEvent.ConflictKeepLocal,
                ),
                events,
            )
        }
    }

    private fun setContent(
        state: CloudSyncState = CloudSyncState(),
        onEvent: (CloudSyncEvent) -> Unit = {},
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                CloudSyncContent(
                    state = state,
                    onEvent = onEvent,
                )
            }
        }
    }

    private fun targetString(resourceId: Int, vararg formatArgs: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId, *formatArgs)

    private fun formattedTimestamp(epochMillis: Long): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
            .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

    private fun providerControlTag(target: SyncTarget, control: String): String = when (target) {
        SyncTarget.Dropbox -> "cloud_sync_dropbox_$control"
        SyncTarget.GoogleDrive -> "cloud_sync_google_drive_$control"
    }

    private companion object {
        const val AUTO_SYNC_TAG = "cloud_sync_auto_sync"
    }
}
