package com.kshavrin.mymoney.core.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncTargetTest {
    @Test
    fun `has exactly Dropbox, GoogleDrive and Shared entries`() {
        assertEquals(
            listOf(SyncTarget.Dropbox, SyncTarget.GoogleDrive, SyncTarget.Shared),
            SyncTarget.entries.toList(),
        )
    }
}
