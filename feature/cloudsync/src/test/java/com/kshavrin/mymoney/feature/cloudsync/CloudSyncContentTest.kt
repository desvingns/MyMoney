package com.kshavrin.mymoney.feature.cloudsync

import com.google.api.services.drive.DriveScopes
import com.kshavrin.mymoney.core.datastore.CloudBinding
import com.kshavrin.mymoney.core.datastore.CloudProvider
import com.kshavrin.mymoney.core.sync.MigrationResolution
import com.kshavrin.mymoney.core.sync.SyncTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
}
