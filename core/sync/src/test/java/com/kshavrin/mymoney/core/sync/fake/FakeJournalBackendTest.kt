package com.kshavrin.mymoney.core.sync.fake

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeJournalBackendTest {
    @Test
    fun `uploads and lists peer journals without folder arguments`() = runTest {
        val backend = FakeJournalBackend(ownDeviceId = "self")
        backend.uploadJournal("self", byteArrayOf(1))
        backend.uploadJournal("peer", byteArrayOf(2))
        val peers = backend.listPeerJournals().getOrThrow()
        assertEquals(listOf("peer"), peers.map { it.deviceId })
        assertEquals(listOf(2.toByte()), backend.downloadJournal(peers.single().fileId).getOrThrow().toList())
    }

    @Test
    fun `reupload replaces a device journal`() = runTest {
        val backend = FakeJournalBackend(ownDeviceId = "self")
        backend.uploadJournal("peer", byteArrayOf(1))
        backend.uploadJournal("peer", byteArrayOf(2))
        assertEquals(1, backend.listPeerJournals().getOrThrow().size)
        assertTrue(backend.downloadJournal("file-peer").getOrThrow().contentEquals(byteArrayOf(2)))
    }
}
