package com.kshavrin.mymoney.core.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncTargetTest {
    @Test
    fun `has exactly Dropbox and GoogleDrive entries`() {
        assertEquals(
            listOf(SyncTarget.Dropbox, SyncTarget.GoogleDrive),
            SyncTarget.entries.toList(),
        )
    }
}
