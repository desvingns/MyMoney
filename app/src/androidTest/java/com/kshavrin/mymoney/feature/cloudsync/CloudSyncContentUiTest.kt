package com.kshavrin.mymoney.feature.cloudsync

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
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
    fun `provider buttons emit connect and disconnect events`() {
        val events = mutableListOf<CloudSyncEvent>()

        setContent(
            state =
                CloudSyncState(
                    dropbox =
                        TargetCardState(
                            target = SyncTarget.Dropbox,
                            enabled = true,
                        ),
                    drive =
                        TargetCardState(
                            target = SyncTarget.GoogleDrive,
                            connected = true,
                            accountLabel = "drive@example.test",
                        ),
                ),
            onEvent = events::add,
        )

        composeTestRule
            .onNodeWithTag(providerControlTag(SyncTarget.Dropbox, "connect"))
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeTestRule
            .onNodeWithTag(providerControlTag(SyncTarget.GoogleDrive, "disconnect"))
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(
                    CloudSyncEvent.ConnectClicked(SyncTarget.Dropbox),
                    CloudSyncEvent.DisconnectClicked(SyncTarget.GoogleDrive),
                ),
                events,
            )
        }
    }

    @Test
    fun `shared folder picker and sync now button emit the journal sync events`() {
        val events = mutableListOf<CloudSyncEvent>()

        setContent(
            state =
                CloudSyncState(
                    folderId = "shared-folder",
                    drive = TargetCardState(SyncTarget.GoogleDrive, enabled = true),
                ),
            onEvent = events::add,
        )

        composeTestRule
            .onNodeWithText(targetString(R.string.sync_change_gdrive_folder))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onAllNodes(hasSetTextAction())
            .assertCountEquals(0)
        composeTestRule
            .onNodeWithTag(FOLDER_ID_TAG)
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeTestRule
            .onNodeWithTag(SYNC_NOW_TAG)
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(CloudSyncEvent.GoogleDriveFolderSelectionClicked, events.firstOrNull())
            assertEquals(CloudSyncEvent.SyncNowClicked(), events.lastOrNull())
        }
    }

    @Test
    fun `blank folder surface shows not configured status and disables sync now`() {
        val events = mutableListOf<CloudSyncEvent>()

        setContent(
            state =
                CloudSyncState(
                    folderId = "",
                    drive = TargetCardState(SyncTarget.GoogleDrive, enabled = true),
                ),
            onEvent = events::add,
        )

        composeTestRule
            .onNodeWithText(targetString(R.string.sync_folder_not_configured))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.sync_select_gdrive_folder))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(FOLDER_ID_TAG)
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeTestRule
            .onNodeWithTag(SYNC_NOW_TAG)
            .performScrollTo()
            .assertIsNotEnabled()

        composeTestRule.runOnIdle {
            assertEquals(listOf(CloudSyncEvent.GoogleDriveFolderSelectionClicked), events)
        }
    }

    @Test
    fun `status section shows last sync timestamp peer states and configured folder copy`() {
        val lastSyncAt = Instant.parse("2026-06-25T09:30:00Z").toEpochMilli()
        val peerModifiedAt = Instant.parse("2026-06-25T08:00:00Z").toEpochMilli()
        val peerPulledAt = Instant.parse("2026-06-25T08:30:00Z").toEpochMilli()

        setContent(
            state =
                CloudSyncState(
                    folderId = "shared-folder",
                    lastSyncAtMs = lastSyncAt,
                    peerStatuses =
                        listOf(
                            PeerJournalState(
                                deviceId = "device-a",
                                modifiedAtMs = peerModifiedAt,
                                pulledThroughMs = peerPulledAt,
                            ),
                            PeerJournalState(
                                deviceId = "device-b",
                                modifiedAtMs = peerModifiedAt,
                                pulledThroughMs = 0L,
                            ),
                        ),
                ),
        )

        composeTestRule
            .onNodeWithText(targetString(R.string.sync_last_at, formattedTimestamp(lastSyncAt)))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.sync_folder_configured))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.sync_peer_device, "device-a"))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.sync_peer_up_to_date))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.sync_peer_pulled_through, formattedTimestamp(peerPulledAt)))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.sync_peer_pending))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.sync_peer_never_pulled))
            .performScrollTo()
            .assertIsDisplayed()
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
    fun `sync now button still exists while syncing`() {
        setContent(
            state = CloudSyncState(folderId = "shared-folder", isSyncing = true),
        )

        composeTestRule
            .onNodeWithTag(SYNC_NOW_TAG)
            .performScrollTo()
            .assertExists()
            .assertIsNotEnabled()
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

    private fun targetString(
        resourceId: Int,
        vararg formatArgs: Any,
    ): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId, *formatArgs)

    private fun formattedTimestamp(epochMillis: Long): String =
        DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
            .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

    private fun providerControlTag(
        target: SyncTarget,
        control: String,
    ): String =
        when (target) {
            SyncTarget.Dropbox -> "cloud_sync_dropbox_$control"
            SyncTarget.GoogleDrive -> "cloud_sync_google_drive_$control"
        }

    private companion object {
        const val AUTO_SYNC_TAG = "cloud_sync_auto_sync"
        const val FOLDER_ID_TAG = "cloud_sync_folder_id"
        const val SYNC_NOW_TAG = "cloud_sync_sync_now"
    }
}
