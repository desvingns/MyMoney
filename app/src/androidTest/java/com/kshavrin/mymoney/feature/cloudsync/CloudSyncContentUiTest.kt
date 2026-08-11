package com.kshavrin.mymoney.feature.cloudsync

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.CloudBinding
import com.kshavrin.mymoney.core.datastore.CloudProvider
import com.kshavrin.mymoney.core.datastore.JournalSyncConfigStore
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.domain.model.BackupFile
import com.kshavrin.mymoney.core.domain.repository.BackupRepository
import com.kshavrin.mymoney.core.domain.repository.RemoteConfigRepository
import com.kshavrin.mymoney.core.domain.sync.SharedConflict
import com.kshavrin.mymoney.core.sync.CloudAccountIdentity
import com.kshavrin.mymoney.core.sync.JournalSync
import com.kshavrin.mymoney.core.sync.MigrationResolution
import com.kshavrin.mymoney.core.sync.SnapshotSync
import com.kshavrin.mymoney.core.sync.SyncScheduler
import com.kshavrin.mymoney.core.sync.SyncTarget
import com.kshavrin.mymoney.core.sync.shared.SharedSyncCoordinator
import com.kshavrin.mymoney.core.sync.shared.SharedWorkspaceInvite
import com.kshavrin.mymoney.core.sync.shared.SharedWorkspaceSummary
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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

    @Test
    fun `active Shared card exposes invite code copy and dismiss controls`() {
        val events = mutableListOf<CloudSyncEvent>()
        val renderedState =
            mutableStateOf(
                CloudSyncState(
                    binding = CloudBinding(CloudProvider.Shared, "ws-1", "Budget"),
                    shared = SharedCardState(signedIn = true, active = true, workspaceName = "Budget"),
                ),
            )
        val onEvent: (CloudSyncEvent) -> Unit = { event ->
            events += event
            when (event) {
                CloudSyncEvent.SharedCreateInviteClicked ->
                    renderedState.value = CloudSyncState(sharedDialog = SharedDialog.Invite("invite-token"))
                CloudSyncEvent.SharedDialogDismissed -> renderedState.value = CloudSyncState()
                else -> Unit
            }
        }

        composeTestRule.setContent {
            MyMoneyTheme {
                CloudSyncContent(state = renderedState.value, onEvent = onEvent)
            }
        }
        composeTestRule
            .onNodeWithTag("cloud_sync_shared_create_invite")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.runOnIdle {
            assertEquals(listOf(CloudSyncEvent.SharedCreateInviteClicked), events)
        }

        events.clear()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("invite-token").assertIsDisplayed()
        composeTestRule.onNodeWithText(targetString(R.string.sync_shared_copy_invite)).performClick()
        composeTestRule.onNodeWithText(targetString(R.string.sync_dismiss)).performClick()
        composeTestRule.runOnIdle {
            assertEquals(
                listOf(CloudSyncEvent.SharedCopyInviteClicked, CloudSyncEvent.SharedDialogDismissed),
                events,
            )
        }
    }

    @Test
    fun `CloudSyncRoute copies invite token to the system clipboard`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val viewModel = inviteViewModel()

        composeTestRule.setContent {
            MyMoneyTheme {
                CloudSyncRoute(onBack = {}, viewModel = viewModel)
            }
        }
        viewModel.onEvent(CloudSyncEvent.SharedCreateInviteClicked)
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("cloud_sync_shared_invite_token")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("cloud_sync_shared_copy_invite")
            .performClick()

        composeTestRule.runOnIdle {
            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
            assertEquals(
                "invite-token",
                clipboard
                    ?.primaryClip
                    ?.getItemAt(0)
                    ?.text
                    ?.toString(),
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

    private fun inviteViewModel() =
        CloudSyncViewModel(
            snapshotSync = EmptySnapshotSync(),
            journalSync = EmptyJournalSync(),
            journalSyncConfig = SharedConfigStore(),
            syncScheduler = EmptySyncScheduler(),
            appSettings = EmptyAppSettingsRepository(),
            backupRepository = EmptyBackupRepository(),
            remoteConfig = EmptyRemoteConfigRepository(),
            sharedCoordinator = InviteCoordinator(),
        )

    private class EmptySnapshotSync : SnapshotSync {
        override fun isConnected(target: SyncTarget) = false

        override fun connectedTargets() = emptyList<SyncTarget>()

        override fun connect(
            target: SyncTarget,
            payload: String,
        ) = Unit

        override fun disconnect(target: SyncTarget) = Unit

        override suspend fun accountLabel(target: SyncTarget) = Result.success("unused")

        override suspend fun accountIdentity(target: SyncTarget) = Result.success(CloudAccountIdentity("unused", "unused"))
    }

    private class EmptyJournalSync : JournalSync {
        override suspend fun push() = Unit

        override suspend fun pull() = Unit

        override suspend fun syncNow() = Unit
    }

    private class EmptySyncScheduler : SyncScheduler {
        override fun enablePeriodicSync() = Unit

        override fun disablePeriodicSync() = Unit

        override fun syncNow(target: SyncTarget?) = Unit
    }

    private class EmptyAppSettingsRepository : AppSettingsRepository {
        override val settings: Flow<AppSettings> = flowOf(AppSettings())

        override suspend fun update(transform: (AppSettings) -> AppSettings) = Unit
    }

    private class EmptyBackupRepository : BackupRepository {
        override suspend fun exportDb(treeUriString: String) = Result.success(Unit)

        override suspend fun importDb(documentUriString: String) = Result.success(Unit)

        override suspend fun listLocalBackups(treeUriString: String): List<BackupFile> = emptyList()

        override suspend fun rotateBackups(treeUriString: String) = Result.success(Unit)

        override suspend fun exportToFile(destAbsolutePath: String) = Result.success(Unit)

        override suspend fun importFromFile(srcAbsolutePath: String) = Result.success(Unit)
    }

    private class EmptyRemoteConfigRepository : RemoteConfigRepository {
        override suspend fun refresh() = Result.success(Unit)

        override fun recurringTemplatesEnabled() = false

        override fun budgetModeEnabled() = false

        override fun dropboxSyncEnabled() = false

        override fun gdriveSyncEnabled() = false

        override fun minSupportedVersionCode() = 0L

        override fun aestheticSoundPack() = ""
    }

    private class SharedConfigStore : JournalSyncConfigStore {
        private var current: CloudBinding? = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")

        override suspend fun binding() = current

        override suspend fun setBinding(binding: CloudBinding) {
            current = binding
        }

        override suspend fun clearBinding() {
            current = null
        }

        override suspend fun peerHighWaterMs(fileId: String) = 0L

        override suspend fun setPeerHighWaterMs(
            fileId: String,
            modifiedAtMs: Long,
        ) = Unit

        override suspend fun isBootstrapDone() = true

        override suspend fun markBootstrapDone() = Unit

        override suspend fun clear() {
            current = null
        }
    }

    private class InviteCoordinator : SharedSyncCoordinator {
        override fun isSignedIn() = true

        override fun accountEmail() = "owner@example.com"

        override suspend fun signIn(
            googleIdToken: String,
            nonce: String,
        ) = Result.success(Unit)

        override suspend fun signOut() = Result.success(Unit)

        override suspend fun activeWorkspace() = SharedWorkspaceSummary("ws-1", "Budget")

        override suspend fun createWorkspace(
            name: String,
            importLocalData: Boolean,
        ) = Result.success(SharedWorkspaceSummary("ws-1", name))

        override suspend fun joinWorkspace(
            inviteToken: String,
            importLocalData: Boolean,
        ) = Result.success(SharedWorkspaceSummary("ws-1", "Budget"))

        override suspend fun createInvite() = Result.success(SharedWorkspaceInvite("invite-token"))

        override suspend fun syncNow() = Result.success(Unit)

        override suspend fun listConflicts(): Result<List<SharedConflict>> = Result.success(emptyList())

        override suspend fun resolveConflict(
            conflictId: String,
            winnerOperationId: String,
        ) = Result.success(Unit)

        override suspend fun leaveWorkspace() = Result.success(Unit)
    }
}
