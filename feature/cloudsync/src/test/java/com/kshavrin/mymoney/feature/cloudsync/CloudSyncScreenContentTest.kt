package com.kshavrin.mymoney.feature.cloudsync

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.kshavrin.mymoney.core.datastore.CloudBinding
import com.kshavrin.mymoney.core.datastore.CloudProvider
import com.kshavrin.mymoney.core.domain.model.BackupFile
import com.kshavrin.mymoney.core.sync.SyncTarget
import com.kshavrin.mymoney.core.sync.shared.SharedRealtimeStatus
import com.kshavrin.mymoney.core.sync.shared.SharedWorkspaceSummary
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Compose-UI tests for the Shared mode card and its dialogs within [CloudSyncContent].
 *
 * The screen already exposes a public [CloudSyncContent] composable so these tests
 * render directly against state without needing a real ViewModel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CloudSyncScreenContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Shared card — signed-out state ─────────────────────────────────────

    @Test
    fun `Shared card shows sign-in button when user is not signed in`() {
        setContent(CloudSyncState())
        composeTestRule.onNodeWithTag("cloud_sync_shared_sign_in").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `Shared card sign-in button click emits SharedSignInClicked`() {
        val events = mutableListOf<CloudSyncEvent>()
        setContent(CloudSyncState(), events::add)
        composeTestRule.onNodeWithTag("cloud_sync_shared_sign_in").performScrollTo().performClick()
        composeTestRule.runOnIdle {
            assertEquals(listOf(CloudSyncEvent.SharedSignInClicked), events)
        }
    }

    // ── Shared card — signed-in, not yet in workspace ──────────────────────

    @Test
    fun `Shared card shows setup button when signed in but no active workspace`() {
        setContent(CloudSyncState(shared = SharedCardState(signedIn = true, active = false)))
        composeTestRule.onNodeWithTag("cloud_sync_shared_setup").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `Shared card shows other-provider-active hint when Dropbox is the active binding`() {
        setContent(
            CloudSyncState(
                binding = CloudBinding(CloudProvider.Dropbox, "id", "dropbox@example.com"),
                shared = SharedCardState(signedIn = true),
            ),
        )
        // The hint string is shown instead of the sign-in/setup action buttons
        composeTestRule.onNodeWithTag("cloud_sync_shared_sign_in").assertDoesNotExist()
        composeTestRule.onNodeWithTag("cloud_sync_shared_setup").assertDoesNotExist()
    }

    // ── Shared card — active workspace ─────────────────────────────────────

    @Test
    fun `non-owner Shared card shows sync-now Disconnect and Leave buttons when workspace is active`() {
        setContent(
            CloudSyncState(
                binding = CloudBinding(CloudProvider.Shared, "ws-1", "Budget"),
                shared = SharedCardState(signedIn = true, active = true, workspaceName = "Budget"),
            ),
        )
        composeTestRule.onNodeWithTag("cloud_sync_shared_sync_now").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("cloud_sync_shared_disconnect").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("cloud_sync_shared_leave").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `sole-owner Shared card shows disconnect and hides leave`() {
        setContent(
            CloudSyncState(
                binding = CloudBinding(CloudProvider.Shared, "ws-1", "Budget"),
                shared = SharedCardState(signedIn = true, active = true, isSoleOwner = true),
            ),
        )

        composeTestRule.onNodeWithTag("cloud_sync_shared_disconnect").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("cloud_sync_shared_leave").assertDoesNotExist()
    }

    @Test
    fun `active Shared card renders sleeping realtime status`() {
        setContent(
            CloudSyncState(
                binding = CloudBinding(CloudProvider.Shared, "ws-1", "Budget"),
                shared =
                    SharedCardState(
                        signedIn = true,
                        active = true,
                        workspaceName = "Budget",
                        realtimeStatus = SharedRealtimeStatus.Sleeping(retryAttempt = 2),
                    ),
            ),
        )

        composeTestRule.onNodeWithTag("cloud_sync_shared_realtime_status").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.sync_shared_status_sleeping)).assertIsDisplayed()
    }

    @Test
    fun `realtime error status exposes retry action`() {
        val events = mutableListOf<CloudSyncEvent>()
        setContent(
            CloudSyncState(
                binding = CloudBinding(CloudProvider.Shared, "ws-1", "Budget"),
                shared =
                    SharedCardState(
                        signedIn = true,
                        active = true,
                        realtimeStatus = SharedRealtimeStatus.Error,
                    ),
            ),
            events::add,
        )

        composeTestRule.onNodeWithTag("cloud_sync_shared_realtime_retry").performScrollTo().performClick()
        composeTestRule.runOnIdle {
            assertEquals(listOf(CloudSyncEvent.SharedRetryRealtimeClicked), events)
        }
    }

    @Test
    fun `inactive realtime status does not render a status card`() {
        setContent(
            CloudSyncState(
                binding = CloudBinding(CloudProvider.Shared, "ws-1", "Budget"),
                shared = SharedCardState(signedIn = true, active = true, realtimeStatus = SharedRealtimeStatus.Inactive),
            ),
        )

        composeTestRule.onNodeWithTag("cloud_sync_shared_realtime_status").assertDoesNotExist()
    }

    @Test
    fun `Shared card shows create-invite button only for an active workspace`() {
        setContent(
            CloudSyncState(
                binding = CloudBinding(CloudProvider.Shared, "ws-1", "Budget"),
                shared = SharedCardState(signedIn = true, active = true, workspaceName = "Budget"),
            ),
        )
        composeTestRule.onNodeWithTag("cloud_sync_shared_create_invite").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.sync_shared_create_invite)).assertIsDisplayed()
    }

    @Test
    fun `Shared card hides create-invite button until a workspace is active`() {
        setContent(CloudSyncState(shared = SharedCardState(signedIn = true, active = false)))

        composeTestRule.onNodeWithTag("cloud_sync_shared_create_invite").assertDoesNotExist()
    }

    @Test
    fun `create-invite button emits SharedCreateInviteClicked`() {
        val events = mutableListOf<CloudSyncEvent>()
        setContent(
            CloudSyncState(
                binding = CloudBinding(CloudProvider.Shared, "ws-1", "Budget"),
                shared = SharedCardState(signedIn = true, active = true),
            ),
            events::add,
        )

        composeTestRule.onNodeWithTag("cloud_sync_shared_create_invite").performScrollTo().performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(CloudSyncEvent.SharedCreateInviteClicked), events)
        }
    }

    @Test
    fun `Shared card backup button emits SharedInternalBackupsClicked`() {
        val events = mutableListOf<CloudSyncEvent>()
        setContent(
            CloudSyncState(
                binding = CloudBinding(CloudProvider.Shared, "ws-1", "Budget"),
                shared = SharedCardState(signedIn = true, active = true),
            ),
            events::add,
        )

        composeTestRule.onNodeWithTag("cloud_sync_shared_backups").performScrollTo().performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(CloudSyncEvent.SharedInternalBackupsClicked), events)
        }
    }

    @Test
    fun `internal backup list renders a backup and emits restore selection`() {
        val events = mutableListOf<CloudSyncEvent>()
        val backup = BackupFile("shared-1.db", "/internal/shared-1.db", 1_700_000_000_000L)
        setContent(
            CloudSyncState(
                sharedDialog = SharedDialog.InternalBackups,
                internalBackups = listOf(backup),
            ),
            events::add,
        )

        composeTestRule
            .onNodeWithTag("cloud_sync_shared_backup_${backup.lastModifiedEpochMs}")
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(CloudSyncEvent.SharedInternalBackupRestoreClicked(backup)), events)
        }
    }

    @Test
    fun `restore confirmation dialog emits confirmation event`() {
        val events = mutableListOf<CloudSyncEvent>()
        setContent(
            CloudSyncState(
                sharedDialog =
                    SharedDialog.ConfirmInternalBackupRestore(
                        BackupFile("shared-1.db", "/internal/shared-1.db", 1_700_000_000_000L),
                    ),
            ),
            events::add,
        )

        composeTestRule
            .onNodeWithText(str(R.string.sync_shared_restore_backup_confirm))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(CloudSyncEvent.SharedConfirmInternalBackupRestore), events)
        }
    }

    @Test
    fun `invite dialog shows token and emits copy and dismiss events`() {
        val events = mutableListOf<CloudSyncEvent>()
        setContent(
            CloudSyncState(sharedDialog = SharedDialog.Invite("invite-token")),
            events::add,
        )

        composeTestRule.onNodeWithTag("cloud_sync_shared_invite_token").assertIsDisplayed()
        composeTestRule.onNodeWithText("invite-token").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cloud_sync_shared_copy_invite").performClick()
        composeTestRule.onNodeWithText(str(R.string.sync_dismiss)).performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(
                    CloudSyncEvent.SharedCopyInviteClicked,
                    CloudSyncEvent.SharedDialogDismissed,
                ),
                events,
            )
        }
    }

    @Test
    fun `Shared card shows review-conflicts button only when conflictCount is positive`() {
        setContent(
            CloudSyncState(
                binding = CloudBinding(CloudProvider.Shared, "ws-1", "Budget"),
                shared = SharedCardState(signedIn = true, active = true, conflictCount = 3),
            ),
        )
        composeTestRule.onNodeWithTag("cloud_sync_shared_conflicts").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `Shared card does not show conflicts button when conflictCount is zero`() {
        setContent(
            CloudSyncState(
                binding = CloudBinding(CloudProvider.Shared, "ws-1", "Budget"),
                shared = SharedCardState(signedIn = true, active = true, conflictCount = 0),
            ),
        )
        composeTestRule.onNodeWithTag("cloud_sync_shared_conflicts").assertDoesNotExist()
    }

    @Test
    fun `leave button click emits SharedLeaveClicked`() {
        val events = mutableListOf<CloudSyncEvent>()
        setContent(
            CloudSyncState(
                binding = CloudBinding(CloudProvider.Shared, "ws-1", "Budget"),
                shared = SharedCardState(signedIn = true, active = true),
            ),
            events::add,
        )
        composeTestRule.onNodeWithTag("cloud_sync_shared_leave").performScrollTo().performClick()
        composeTestRule.runOnIdle {
            assertEquals(listOf(CloudSyncEvent.SharedLeaveClicked), events)
        }
    }

    @Test
    fun `active Shared card disconnect button emits SharedDisconnectClicked`() {
        val events = mutableListOf<CloudSyncEvent>()
        setContent(
            CloudSyncState(
                binding = CloudBinding(CloudProvider.Shared, "ws-1", "Budget"),
                shared = SharedCardState(signedIn = true, active = true),
            ),
            events::add,
        )

        composeTestRule.onNodeWithTag("cloud_sync_shared_disconnect").performScrollTo().performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(CloudSyncEvent.SharedDisconnectClicked), events)
        }
    }

    // ── SharedSetupDialog ──────────────────────────────────────────────────

    @Test
    fun `setup dialog shows name and token fields with no-import radio pre-selected by default`() {
        setContent(
            CloudSyncState(
                shared = SharedCardState(signedIn = true),
                sharedDialog = SharedDialog.Setup,
                importLocalData = false,
            ),
        )
        // "no-import" must be pre-selected (importLocalData=false)
        composeTestRule.onNodeWithTag("cloud_sync_shared_import_none").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cloud_sync_shared_import_publish").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cloud_sync_shared_name").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cloud_sync_shared_token").assertIsDisplayed()
    }

    @Test
    fun `setup dialog import-choice rows emit SharedImportChoiceChanged`() {
        val events = mutableListOf<CloudSyncEvent>()
        setContent(
            CloudSyncState(
                shared = SharedCardState(signedIn = true),
                sharedDialog = SharedDialog.Setup,
                importLocalData = false,
            ),
            events::add,
        )
        composeTestRule.onNodeWithTag("cloud_sync_shared_import_publish").performClick()
        composeTestRule.runOnIdle {
            assertEquals(listOf(CloudSyncEvent.SharedImportChoiceChanged(true)), events)
        }
    }

    @Test
    fun `create button is disabled when workspace name is blank`() {
        setContent(
            CloudSyncState(
                shared = SharedCardState(signedIn = true),
                sharedDialog = SharedDialog.Setup,
            ),
        )
        // With an empty name TextField the "Create" button should be disabled
        composeTestRule.onNodeWithTag("cloud_sync_shared_create").assertExists()
    }

    @Test
    fun `remote workspace recovery dialog keeps import choice explicit and emits confirmation`() {
        val events = mutableListOf<CloudSyncEvent>()
        setContent(
            CloudSyncState(
                sharedDialog =
                    SharedDialog.RecoverRemoteWorkspace(
                        SharedWorkspaceSummary("ws-1", "Family budget"),
                    ),
                importLocalData = false,
            ),
            events::add,
        )

        composeTestRule.onNodeWithText("Family budget", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("cloud_sync_shared_recovery_import_none").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cloud_sync_shared_recovery_import_publish").performClick()
        composeTestRule.onNodeWithTag("cloud_sync_shared_recovery_confirm").performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(
                    CloudSyncEvent.SharedImportChoiceChanged(true),
                    CloudSyncEvent.SharedConfirmRemoteWorkspaceRecovery,
                ),
                events,
            )
        }
    }

    // ── SharedConflictsDialog ──────────────────────────────────────────────

    @Test
    fun `conflicts dialog shows conflict entity kind and author attribution`() {
        setContent(
            CloudSyncState(
                binding = CloudBinding(CloudProvider.Shared, "ws-1", "Budget"),
                shared = SharedCardState(signedIn = true, active = true),
                sharedDialog = SharedDialog.Conflicts,
                conflicts =
                    listOf(
                        ConflictUi(
                            conflictId = "c-1",
                            entityKind = "Account",
                            localOperationId = "op-local",
                            localAuthorId = "author-local-id",
                            localSummary = "Local version",
                            remoteOperationId = "op-remote",
                            remoteAuthorId = "author-remote-id",
                            remoteSummary = "Remote version",
                        ),
                    ),
            ),
        )
        // Entity kind label shown
        composeTestRule.onNodeWithText("Account").assertIsDisplayed()
        // Author attribution — ONLY shown in the conflict dialog panels, not on the main screen
        composeTestRule.onNodeWithText("author-local-id", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("author-remote-id", substring = true).assertIsDisplayed()
        // Keep-local / keep-remote resolve buttons
        composeTestRule.onNodeWithTag("cloud_sync_conflict_keep_local").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cloud_sync_conflict_keep_remote").assertIsDisplayed()
    }

    @Test
    fun `conflicts dialog resolve button emits SharedResolveConflict with correct ids`() {
        val events = mutableListOf<CloudSyncEvent>()
        setContent(
            CloudSyncState(
                binding = CloudBinding(CloudProvider.Shared, "ws-1", "Budget"),
                shared = SharedCardState(signedIn = true, active = true),
                sharedDialog = SharedDialog.Conflicts,
                conflicts =
                    listOf(
                        ConflictUi(
                            conflictId = "c-1",
                            entityKind = "Transaction",
                            localOperationId = "op-local",
                            localAuthorId = "user-a",
                            localSummary = "local data",
                            remoteOperationId = "op-remote",
                            remoteAuthorId = "user-b",
                            remoteSummary = "remote data",
                        ),
                    ),
            ),
            events::add,
        )
        composeTestRule.onNodeWithTag("cloud_sync_conflict_keep_local").performClick()
        composeTestRule.runOnIdle {
            assertEquals(
                listOf(CloudSyncEvent.SharedResolveConflict("c-1", "op-local")),
                events,
            )
        }
    }

    @Test
    fun `conflicts dialog shows empty-state text when conflict list is empty`() {
        setContent(
            CloudSyncState(
                binding = CloudBinding(CloudProvider.Shared, "ws-1", "Budget"),
                sharedDialog = SharedDialog.Conflicts,
                conflicts = emptyList(),
            ),
        )
        composeTestRule
            .onNodeWithText(str(R.string.sync_shared_no_conflicts))
            .assertIsDisplayed()
    }

    // ── ConfirmLeave dialog ────────────────────────────────────────────────

    @Test
    fun `confirm-leave dialog shows title and confirm emits SharedConfirmLeave`() {
        val events = mutableListOf<CloudSyncEvent>()
        setContent(
            CloudSyncState(sharedDialog = SharedDialog.ConfirmLeave),
            events::add,
        )
        composeTestRule.onNodeWithText(str(R.string.sync_shared_leave_title)).assertIsDisplayed()
        // Click the leave confirmation button
        composeTestRule.onNodeWithText(str(R.string.sync_shared_leave)).assertIsEnabled().performClick()
        composeTestRule.runOnIdle {
            assertEquals(listOf(CloudSyncEvent.SharedConfirmLeave), events)
        }
    }

    @Test
    fun `ConfirmDisconnect dialog shows keep and delete controls and emits events`() {
        val events = mutableListOf<CloudSyncEvent>()
        setContent(
            CloudSyncState(sharedDialog = SharedDialog.ConfirmDisconnect),
            events::add,
        )

        composeTestRule.onNodeWithText(str(R.string.sync_shared_disconnect_title)).assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("cloud_sync_shared_disconnect_keep_server_data")
            .assertIsDisplayed()
            .performClick()
        composeTestRule
            .onNodeWithTag("cloud_sync_shared_disconnect_delete_workspace")
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        composeTestRule.onNodeWithText(str(R.string.sync_shared_delete_workspace)).assertIsDisplayed()

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(
                    CloudSyncEvent.SharedConfirmDisconnectKeepServerData,
                    CloudSyncEvent.SharedConfirmDisconnectDeleteWorkspace,
                ),
                events,
            )
        }
    }

    // ── Mutual exclusivity ─────────────────────────────────────────────────

    @Test
    fun `Dropbox and Drive cards show switch button when Shared is the active binding`() {
        setContent(
            CloudSyncState(
                binding = CloudBinding(CloudProvider.Shared, "ws-1", "Budget"),
                dropbox = TargetCardState(SyncTarget.Dropbox, enabled = true, connected = true),
                drive = TargetCardState(SyncTarget.GoogleDrive, enabled = true, connected = true),
                shared = SharedCardState(signedIn = true, active = true),
            ),
        )
        // Switch buttons appear (not connect/use-connected) because Shared is active
        composeTestRule.onNodeWithTag("cloud_sync_dropbox_switch").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cloud_sync_google_drive_switch").assertIsDisplayed()
    }

    // ── Author attribution isolation ───────────────────────────────────────

    @Test
    fun `main screen content does not show author identity strings outside the conflicts dialog`() {
        // Author attribution must appear ONLY in ConflictPanel — not on the main CloudSyncContent
        // when the dialog is closed.
        setContent(
            CloudSyncState(
                binding = CloudBinding(CloudProvider.Shared, "ws-1", "Budget"),
                shared = SharedCardState(signedIn = true, active = true),
                sharedDialog = null,
                conflicts = emptyList(),
            ),
        )
        // No author attribution strings should appear on the base screen
        composeTestRule.onNodeWithText("author-local-id", substring = true).assertDoesNotExist()
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun setContent(
        state: CloudSyncState = CloudSyncState(),
        onEvent: (CloudSyncEvent) -> Unit = {},
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                CloudSyncContent(state = state, onEvent = onEvent)
            }
        }
    }

    private fun str(
        resId: Int,
        vararg args: Any,
    ): String =
        androidx.test.core.app.ApplicationProvider
            .getApplicationContext<android.content.Context>()
            .getString(resId, *args)
}
