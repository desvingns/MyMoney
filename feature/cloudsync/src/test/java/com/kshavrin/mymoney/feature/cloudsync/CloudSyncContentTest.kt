package com.kshavrin.mymoney.feature.cloudsync

import com.google.api.services.drive.DriveScopes
import com.kshavrin.mymoney.core.datastore.CloudBinding
import com.kshavrin.mymoney.core.datastore.CloudProvider
import com.kshavrin.mymoney.core.sync.MigrationResolution
import com.kshavrin.mymoney.core.sync.SyncTarget
import com.kshavrin.mymoney.core.sync.shared.SharedRealtimeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncContentTest {
    @Test
    fun `state exposes one active binding without folder or filename fields`() {
        val state = CloudSyncState(binding = CloudBinding(CloudProvider.GoogleDrive, "id", "me@example.com"))
        assertEquals(CloudProvider.GoogleDrive, state.binding?.provider)
        assertEquals("me@example.com", state.binding?.accountLabel)
        assertNull(state.errorBannerRes)
    }

    @Test
    fun `legacy provider selection and migration events carry target and resolution`() {
        assertEquals(SyncTarget.Dropbox, CloudSyncEvent.UseConnectedProviderClicked(SyncTarget.Dropbox).target)
        assertEquals(SyncTarget.GoogleDrive, CloudSyncEvent.SwitchClicked(SyncTarget.GoogleDrive).target)
        assertEquals(MigrationResolution.KeepLocal, CloudSyncEvent.ConfirmMigration(MigrationResolution.KeepLocal).resolution)
    }

    @Test
    fun `target cards default to disconnected`() {
        assertFalse(TargetCardState(SyncTarget.Dropbox).connected)
    }

    @Test
    fun `Google authorization policy retains selected account and app data scope without consent prompt`() {
        val policy = googleDriveAuthorizationPolicy("person@example.com")

        assertEquals("person@example.com", policy.accountName)
        assertEquals("com.google", policy.accountType)
        assertEquals(listOf(DriveScopes.DRIVE_APPDATA), policy.scopeUris)
        assertFalse(policy.requestsConsentPrompt)
    }

    // ── SharedCardState ────────────────────────────────────────────────────

    @Test
    fun `SharedCardState defaults to enabled true signed-out and inactive`() {
        val s = SharedCardState()
        assertTrue(s.enabled)
        assertFalse(s.signedIn)
        assertFalse(s.active)
        assertEquals(0, s.conflictCount)
        assertNull(s.accountEmail)
        assertNull(s.workspaceName)
        assertEquals(SharedRealtimeStatus.Inactive, s.realtimeStatus)
    }

    @Test
    fun `SharedDialog sealed values cover Setup Conflicts and ConfirmLeave`() {
        val setup: SharedDialog = SharedDialog.Setup
        val conflicts: SharedDialog = SharedDialog.Conflicts
        val leave: SharedDialog = SharedDialog.ConfirmLeave
        // All three must be distinct
        assertFalse(setup == conflicts)
        assertFalse(conflicts == leave)
        assertFalse(setup == leave)
    }

    @Test
    fun `CloudSyncState importLocalData defaults to false enforcing no-import as the default`() {
        assertFalse(CloudSyncState().importLocalData)
    }

    @Test
    fun `ConflictUi carries both author ids and operation ids`() {
        val conflict =
            ConflictUi(
                conflictId = "c-1",
                entityKind = "Account",
                localOperationId = "op-local",
                localAuthorId = "user-a",
                localSummary = "local data",
                remoteOperationId = "op-remote",
                remoteAuthorId = "user-b",
                remoteSummary = "remote data",
            )
        assertEquals("c-1", conflict.conflictId)
        assertEquals("user-a", conflict.localAuthorId)
        assertEquals("user-b", conflict.remoteAuthorId)
        assertEquals("op-local", conflict.localOperationId)
        assertEquals("op-remote", conflict.remoteOperationId)
    }

    @Test
    fun `SharedCreateWorkspace and SharedJoinWorkspace events carry their payloads`() {
        assertEquals("My Budget", CloudSyncEvent.SharedCreateWorkspace("My Budget").name)
        assertEquals("invite-abc", CloudSyncEvent.SharedJoinWorkspace("invite-abc").inviteToken)
    }

    @Test
    fun `SharedResolveConflict event carries conflictId and winnerOperationId`() {
        val ev = CloudSyncEvent.SharedResolveConflict("c-1", "op-winner")
        assertEquals("c-1", ev.conflictId)
        assertEquals("op-winner", ev.winnerOperationId)
    }

    @Test
    fun `foreground realtime lifecycle events remain distinct`() {
        assertFalse(CloudSyncEvent.SharedRealtimeForegroundStarted == CloudSyncEvent.SharedRealtimeForegroundStopped)
        assertFalse(CloudSyncEvent.SharedRealtimeForegroundStopped == CloudSyncEvent.SharedRetryRealtimeClicked)
    }
}
