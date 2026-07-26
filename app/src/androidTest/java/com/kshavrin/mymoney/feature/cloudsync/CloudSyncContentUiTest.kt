package com.kshavrin.mymoney.feature.cloudsync

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.datastore.CloudBinding
import com.kshavrin.mymoney.core.datastore.CloudProvider
import com.kshavrin.mymoney.core.sync.MigrationResolution
import com.kshavrin.mymoney.core.sync.SyncTarget
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloudSyncContentUiTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun `active invalid binding still shows disconnect`() {
        val events = mutableListOf<CloudSyncEvent>()
        setContent(
            CloudSyncState(
                binding = CloudBinding(CloudProvider.GoogleDrive, "invalid", "stale@example.com"),
                drive = TargetCardState(SyncTarget.GoogleDrive, enabled = true, connected = false),
            ),
            events::add,
        )
        composeTestRule
            .onNodeWithTag("cloud_sync_google_drive_disconnect")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeTestRule.runOnIdle { assertEquals(listOf(CloudSyncEvent.DisconnectClicked(SyncTarget.GoogleDrive)), events) }
    }

    @Test
    fun `switch control emits target and no folder text field exists`() {
        val events = mutableListOf<CloudSyncEvent>()
        setContent(
            CloudSyncState(
                binding = CloudBinding(CloudProvider.Dropbox, "id", "dropbox@example.com"),
                dropbox = TargetCardState(SyncTarget.Dropbox, enabled = true, connected = true),
                drive = TargetCardState(SyncTarget.GoogleDrive, enabled = true, connected = true),
            ),
            events::add,
        )
        composeTestRule.onNodeWithTag("cloud_sync_google_drive_switch").performScrollTo().performClick()
        composeTestRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        composeTestRule.runOnIdle { assertEquals(listOf(CloudSyncEvent.SwitchClicked(SyncTarget.GoogleDrive)), events) }
    }

    @Test
    fun `migration review buttons emit explicit resolutions`() {
        val events = mutableListOf<CloudSyncEvent>()
        setContent(CloudSyncState(migration = MigrationUiState.Reviewing(SyncTarget.GoogleDrive, 2)), events::add)
        composeTestRule.onNodeWithText(targetString(R.string.sync_migration_use_target)).performClick()
        composeTestRule.onNodeWithText(targetString(R.string.sync_migration_keep_local)).performClick()
        composeTestRule.runOnIdle {
            assertEquals(
                listOf(
                    CloudSyncEvent.ConfirmMigration(MigrationResolution.UseTarget),
                    CloudSyncEvent.ConfirmMigration(MigrationResolution.KeepLocal),
                ),
                events,
            )
        }
    }

    private fun setContent(
        state: CloudSyncState = CloudSyncState(),
        onEvent: (CloudSyncEvent) -> Unit = {},
    ) {
        composeTestRule.setContent { MyMoneyTheme { CloudSyncContent(state = state, onEvent = onEvent) } }
    }

    private fun targetString(resourceId: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
