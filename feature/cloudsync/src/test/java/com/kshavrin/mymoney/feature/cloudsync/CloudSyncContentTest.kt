package com.kshavrin.mymoney.feature.cloudsync

import com.kshavrin.mymoney.core.sync.SyncTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncContentTest {
    @Test
    fun `default state starts with blank folder no peers and no transient banners`() {
        val state = CloudSyncState()

        assertEquals("", state.folderId)
        assertNull(state.lastSyncAtMs)
        assertTrue(state.peerStatuses.isEmpty())
        assertFalse(state.isSyncing)
        assertNull(state.errorBannerRes)
    }

    @Test
    fun `target cards keep their target identity and disconnected defaults`() {
        val state = CloudSyncState()

        assertEquals(SyncTarget.Dropbox, state.dropbox.target)
        assertEquals(SyncTarget.GoogleDrive, state.drive.target)
        assertFalse(state.dropbox.connected)
        assertFalse(state.drive.connected)
        assertNull(state.dropbox.accountLabel)
        assertNull(state.drive.accountLabel)
    }

    @Test
    fun `peer journal state is up to date when pulled through reaches modified time`() {
        val state = PeerJournalState(deviceId = "device-a", modifiedAtMs = 200L, pulledThroughMs = 200L)

        assertTrue(state.upToDate)
    }

    @Test
    fun `peer journal state is pending when pulled through is behind modified time`() {
        val state = PeerJournalState(deviceId = "device-a", modifiedAtMs = 200L, pulledThroughMs = 150L)

        assertFalse(state.upToDate)
    }

    @Test
    fun `target scoped events carry the selected provider back to the view model`() {
        assertEquals(
            SyncTarget.GoogleDrive,
            (CloudSyncEvent.ConnectClicked(SyncTarget.GoogleDrive)).target,
        )
        assertEquals(
            SyncTarget.GoogleDrive,
            (CloudSyncEvent.DisconnectClicked(SyncTarget.GoogleDrive)).target,
        )
    }

    @Test
    fun `sync now event is global and does not require a target`() {
        assertEquals(null, CloudSyncEvent.SyncNowClicked().target)
    }

    @Test
    fun `folder id changed event carries the raw text entry`() {
        assertEquals("shared-folder", CloudSyncEvent.FolderIdChanged("shared-folder").folderId)
    }

    @Test
    fun `cloud sync actions remain the three route level one shot commands`() {
        val actions =
            listOf(
                CloudSyncAction.NavigateBack,
                CloudSyncAction.LaunchDropboxAuth,
                CloudSyncAction.LaunchGoogleSignIn,
            )

        assertEquals(3, actions.map { it::class }.toSet().size)
    }
}
