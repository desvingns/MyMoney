package com.kshavrin.mymoney.core.sync

import com.kshavrin.mymoney.core.datastore.CloudBinding
import com.kshavrin.mymoney.core.datastore.CloudProvider
import com.kshavrin.mymoney.core.sync.fake.FakeJournalBackend
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalSyncImplTest {
    @Test
    fun `active binding maps to exactly one provider backend`() {
        val binding = CloudBinding(CloudProvider.Dropbox, "acct", "A")
        assertEquals(SyncTarget.Dropbox, binding.provider.toSyncTarget())
        assertEquals(SyncTarget.GoogleDrive, CloudProvider.GoogleDrive.toSyncTarget())
    }

    @Test
    fun `journal backend upload is cumulative for an empty remote`() = runTest {
        val backend = FakeJournalBackend(ownDeviceId = "local")
        backend.uploadJournal("local", "first".toByteArray())
        assertTrue(backend.listPeerJournals().getOrThrow().isEmpty())
        backend.uploadJournal("local", "second".toByteArray())
        assertEquals("second", backend.downloadJournal("file-local").getOrThrow().decodeToString())
    }

    @Test
    fun `peer listing is performed before the caller uploads`() = runTest {
        val backend = OrderedBackend(SyncTarget.Dropbox)
        backend.listPeerJournals()
        backend.uploadJournal("local", byteArrayOf())
        assertEquals(listOf("list", "upload"), backend.calls)
    }

    private class OrderedBackend(override val target: SyncTarget) : JournalBackend {
        val calls = mutableListOf<String>()
        override suspend fun uploadJournal(deviceId: String, bytes: ByteArray): Result<Unit> {
            calls += "upload"
            return Result.success(Unit)
        }
        override suspend fun listPeerJournals(): Result<List<RemoteJournalFile>> {
            calls += "list"
            return Result.success(emptyList())
        }
        override suspend fun downloadJournal(fileId: String) = Result.success(byteArrayOf())
    }
}
