package com.kshavrin.mymoney.core.sync.fake

import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.domain.sync.EntityKind
import com.kshavrin.mymoney.core.domain.sync.OpType
import com.kshavrin.mymoney.core.domain.sync.Operation
import com.kshavrin.mymoney.core.sync.JournalSerializer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Tests for [FakeJournalBackend] and the [JournalBackend] contract it models.
 *
 * Covers all four acceptance scenarios from the SPEC:
 * 1. Upload writes per-device file.
 * 2. listPeerJournals excludes ownDeviceId.
 * 3. Download + parse is JSONL-equivalent to the original.
 * 4. Re-upload is an upsert — no second file is created.
 */
class FakeJournalBackendTest {
    private val ownDevice = "device-self"
    private val peerDevice = "device-peer"
    private val folderId = "folder-shared"

    private val serializer = JournalSerializer()

    private fun backend(ownDeviceId: String? = ownDevice) = FakeJournalBackend(ownDeviceId)

    private fun op(
        opId: String = "op-1",
        deviceId: String = ownDevice,
        entityUuid: String = "uuid-1",
    ) = Operation(
        opId = opId,
        deviceId = deviceId,
        entityKind = EntityKind.Transaction,
        entityUuid = entityUuid,
        opType = OpType.Upsert,
        payload = """{"amount":50}""",
        updatedAt = Instant.ofEpochMilli(1_700_000_000_000L),
    )

    // -----------------------------------------------------------------------
    // 1. Upload — per-device file appears in the folder
    // -----------------------------------------------------------------------

    @Test
    fun `upload records the call in uploadCalls`() =
        runTest {
            val sut = backend()
            val bytes = serializer.encode(listOf(op()))

            sut.uploadJournal(folderId, ownDevice, bytes)

            assertEquals(1, sut.uploadCalls.size)
            assertEquals(folderId, sut.uploadCalls[0].folderId)
            assertEquals(ownDevice, sut.uploadCalls[0].deviceId)
        }

    @Test
    fun `upload returns success`() =
        runTest {
            val sut = backend()
            val result = sut.uploadJournal(folderId, ownDevice, serializer.encode(listOf(op())))
            assertTrue(result.isSuccess)
        }

    @Test
    fun `uploaded file appears in listPeerJournals for a different observer`() =
        runTest {
            val uploader = backend(ownDeviceId = ownDevice)
            val bytes = serializer.encode(listOf(op()))
            uploader.uploadJournal(folderId, peerDevice, bytes)

            val observer = FakeJournalBackend(ownDeviceId = ownDevice)

            val uploaderStore = FakeJournalBackend(ownDeviceId = peerDevice)
            uploaderStore.uploadJournal(folderId, ownDevice, bytes)

            val listed = uploaderStore.listPeerJournals(folderId).getOrThrow()
            assertTrue(listed.any { it.deviceId == ownDevice })
        }

    @Test
    fun `uploaded bytes are retrievable via downloadJournal`() =
        runTest {
            val sut = backend()
            val ops = listOf(op(opId = "op-download"))
            val bytes = serializer.encode(ops)

            sut.uploadJournal(folderId, ownDevice, bytes)

            val fileId = "$folderId/$ownDevice"
            val downloaded = sut.downloadJournal(fileId).getOrThrow()

            val decoded = serializer.decode(downloaded)
            assertEquals(1, decoded.size)
            assertEquals("op-download", decoded[0].opId)
        }

    // -----------------------------------------------------------------------
    // 2. listPeerJournals excludes ownDeviceId
    // -----------------------------------------------------------------------

    @Test
    fun `listPeerJournals excludes ownDeviceId`() =
        runTest {
            val sut = backend(ownDeviceId = ownDevice)
            sut.uploadJournal(folderId, ownDevice, serializer.encode(listOf(op())))
            sut.uploadJournal(folderId, peerDevice, serializer.encode(listOf(op(deviceId = peerDevice))))

            val peers = sut.listPeerJournals(folderId).getOrThrow()

            assertFalse(
                "listPeerJournals must not include ownDeviceId",
                peers.any { it.deviceId == ownDevice },
            )
            assertTrue(peers.any { it.deviceId == peerDevice })
        }

    @Test
    fun `listPeerJournals returns empty when only own device has uploaded`() =
        runTest {
            val sut = backend(ownDeviceId = ownDevice)
            sut.uploadJournal(folderId, ownDevice, serializer.encode(listOf(op())))

            val peers = sut.listPeerJournals(folderId).getOrThrow()

            assertTrue(peers.isEmpty())
        }

    @Test
    fun `listPeerJournals returns empty when no device has uploaded`() =
        runTest {
            val sut = backend()
            val peers = sut.listPeerJournals(folderId).getOrThrow()
            assertTrue(peers.isEmpty())
        }

    @Test
    fun `listPeerJournals includes all peer devices`() =
        runTest {
            val sut = backend(ownDeviceId = ownDevice)
            val peer1 = "device-peer-1"
            val peer2 = "device-peer-2"
            val peer3 = "device-peer-3"
            sut.uploadJournal(folderId, peer1, byteArrayOf())
            sut.uploadJournal(folderId, peer2, byteArrayOf())
            sut.uploadJournal(folderId, peer3, byteArrayOf())

            val peers = sut.listPeerJournals(folderId).getOrThrow()

            assertEquals(3, peers.size)
            assertTrue(peers.any { it.deviceId == peer1 })
            assertTrue(peers.any { it.deviceId == peer2 })
            assertTrue(peers.any { it.deviceId == peer3 })
        }

    @Test
    fun `listPeerJournals RemoteJournalFile carries correct deviceId and fileId`() =
        runTest {
            val sut = backend(ownDeviceId = ownDevice)
            sut.uploadJournal(folderId, peerDevice, byteArrayOf())

            val listed = sut.listPeerJournals(folderId).getOrThrow()
            val file = listed.single()

            assertEquals(peerDevice, file.deviceId)
            assertEquals("$folderId/$peerDevice", file.fileId)
        }

    // -----------------------------------------------------------------------
    // 3. JSONL round-trip via upload + download
    // -----------------------------------------------------------------------

    @Test
    fun `download and parse round-trip is equivalent to original operations`() =
        runTest {
            val sut = backend()
            val original =
                listOf(
                    op(opId = "op-rt-1", entityUuid = "uuid-rt-1"),
                    op(opId = "op-rt-2", entityUuid = "uuid-rt-2"),
                )
            sut.uploadJournal(folderId, ownDevice, serializer.encode(original))

            val fileId = "$folderId/$ownDevice"
            val bytes = sut.downloadJournal(fileId).getOrThrow()
            val decoded = serializer.decode(bytes)

            assertEquals(original.size, decoded.size)
            original.forEachIndexed { i, expected ->
                assertEquals(expected.opId, decoded[i].opId)
                assertEquals(expected.deviceId, decoded[i].deviceId)
                assertEquals(expected.entityKind, decoded[i].entityKind)
                assertEquals(expected.entityUuid, decoded[i].entityUuid)
                assertEquals(expected.opType, decoded[i].opType)
                assertEquals(expected.payload, decoded[i].payload)
                assertEquals(expected.updatedAt, decoded[i].updatedAt)
            }
        }

    @Test
    fun `round-trip preserves null payload through upload-download cycle`() =
        runTest {
            val sut = backend()
            val original = op(opId = "op-null-payload").copy(payload = null)
            sut.uploadJournal(folderId, ownDevice, serializer.encode(listOf(original)))

            val bytes = sut.downloadJournal("$folderId/$ownDevice").getOrThrow()
            val decoded = serializer.decode(bytes).single()

            assertEquals(null, decoded.payload)
        }

    // -----------------------------------------------------------------------
    // 4. Re-upload is an upsert — no second file created
    // -----------------------------------------------------------------------

    @Test
    fun `re-upload for the same device replaces the existing file not creates a second one`() =
        runTest {
            val sut = backend()
            val firstOps = listOf(op(opId = "op-first"))
            val secondOps = listOf(op(opId = "op-second"))

            sut.uploadJournal(folderId, ownDevice, serializer.encode(firstOps))
            sut.uploadJournal(folderId, ownDevice, serializer.encode(secondOps))

            val peers = FakeJournalBackend(ownDeviceId = "observer")
            sut.uploadJournal(folderId, ownDevice, serializer.encode(secondOps))

            val bytes = sut.downloadJournal("$folderId/$ownDevice").getOrThrow()
            val decoded = serializer.decode(bytes)

            assertEquals(1, decoded.size)
            assertEquals("op-second", decoded[0].opId)
        }

    @Test
    fun `listPeerJournals shows only one entry per device after multiple uploads`() =
        runTest {
            val sut = backend(ownDeviceId = ownDevice)

            sut.uploadJournal(folderId, peerDevice, serializer.encode(listOf(op(opId = "op-v1"))))
            sut.uploadJournal(folderId, peerDevice, serializer.encode(listOf(op(opId = "op-v2"))))
            sut.uploadJournal(folderId, peerDevice, serializer.encode(listOf(op(opId = "op-v3"))))

            val peers = sut.listPeerJournals(folderId).getOrThrow()

            assertEquals(
                "Multiple uploads for the same device must yield exactly one entry",
                1,
                peers.filter { it.deviceId == peerDevice }.size,
            )
        }

    @Test
    fun `re-upload updates content and latest bytes win`() =
        runTest {
            val sut = backend()
            val firstBytes = serializer.encode(listOf(op(opId = "v1")))
            val secondBytes = serializer.encode(listOf(op(opId = "v1"), op(opId = "v2", entityUuid = "uuid-2")))

            sut.uploadJournal(folderId, ownDevice, firstBytes)
            sut.uploadJournal(folderId, ownDevice, secondBytes)

            val downloaded = sut.downloadJournal("$folderId/$ownDevice").getOrThrow()
            val decoded = serializer.decode(downloaded)

            assertEquals(2, decoded.size)
            assertEquals("v2", decoded[1].opId)
        }

    // -----------------------------------------------------------------------
    // Error simulation
    // -----------------------------------------------------------------------

    @Test
    fun `upload failure returns Result failure with correct SyncError`() =
        runTest {
            val sut = backend()
            sut.simulateUploadFailure(SyncError.Network)

            val result = sut.uploadJournal(folderId, ownDevice, byteArrayOf())

            assertTrue(result.isFailure)
            val ex = result.exceptionOrNull() as? SyncException
            assertEquals(SyncError.Network, ex?.syncError)
        }

    @Test
    fun `list failure returns Result failure with correct SyncError`() =
        runTest {
            val sut = backend()
            sut.simulateListFailure(SyncError.Auth)

            val result = sut.listPeerJournals(folderId)

            assertTrue(result.isFailure)
            val ex = result.exceptionOrNull() as? SyncException
            assertEquals(SyncError.Auth, ex?.syncError)
        }

    @Test
    fun `download failure returns Result failure with correct SyncError`() =
        runTest {
            val sut = backend()
            sut.simulateDownloadFailure(SyncError.Server)

            val result = sut.downloadJournal("$folderId/$ownDevice")

            assertTrue(result.isFailure)
            val ex = result.exceptionOrNull() as? SyncException
            assertEquals(SyncError.Server, ex?.syncError)
        }

    @Test
    fun `download of non-existent fileId returns failure`() =
        runTest {
            val sut = backend()
            val result = sut.downloadJournal("non-existent-file-id")
            assertTrue(result.isFailure)
        }

    // -----------------------------------------------------------------------
    // fileName contract: ops-<deviceId>.jsonl
    // -----------------------------------------------------------------------

    @Test
    fun `fileId is composed as folderId slash deviceId matching ops-deviceId-jsonl naming convention`() =
        runTest {
            val sut = backend(ownDeviceId = ownDevice)
            sut.uploadJournal(folderId, peerDevice, byteArrayOf())

            val listed = sut.listPeerJournals(folderId).getOrThrow()
            val fileId = listed.single().fileId

            assertEquals("$folderId/$peerDevice", fileId)
        }
}
