package com.kshavrin.mymoney.core.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class OperationMergerTest {
    private val entityUuid = "entity-1"

    @Test
    fun `empty input resolves to none`() {
        assertEquals(MergeResult.None, OperationMerger.resolve(emptyList()))
    }

    @Test
    fun `latest updatedAt wins for upserts`() {
        val earlier = operation(opId = "op-1", deviceId = "X", updatedAtMs = 1_000L)
        val later = operation(opId = "op-2", deviceId = "Y", updatedAtMs = 2_000L)

        assertEquals(MergeResult.Resolved(later), OperationMerger.resolve(listOf(earlier, later)))
    }

    @Test
    fun `equal timestamps choose lexicographically larger device id regardless of input order`() {
        val smallerDevice = operation(opId = "op-1", deviceId = "aaa", updatedAtMs = 5_000L)
        val largerDevice = operation(opId = "op-2", deviceId = "bbb", updatedAtMs = 5_000L)

        val forward = OperationMerger.resolve(listOf(smallerDevice, largerDevice))
        val reversed = OperationMerger.resolve(listOf(largerDevice, smallerDevice))

        assertEquals(MergeResult.Resolved(largerDevice), forward)
        assertEquals(MergeResult.Resolved(largerDevice), reversed)
    }

    @Test
    fun `later delete resolves to tombstone`() {
        val upsert = operation(opId = "op-1", deviceId = "X", updatedAtMs = 1_000L)
        val delete =
            operation(
                opId = "op-2",
                deviceId = "Y",
                opType = OpType.Delete,
                payload = null,
                updatedAtMs = 3_000L,
            )

        assertEquals(MergeResult.Tombstone(entityUuid), OperationMerger.resolve(listOf(upsert, delete)))
    }

    @Test
    fun `later upsert after delete resolves to recreated winner`() {
        val delete =
            operation(
                opId = "op-1",
                deviceId = "X",
                opType = OpType.Delete,
                payload = null,
                updatedAtMs = 1_000L,
            )
        val recreate = operation(opId = "op-2", deviceId = "Y", updatedAtMs = 2_000L)

        assertEquals(MergeResult.Resolved(recreate), OperationMerger.resolve(listOf(delete, recreate)))
    }

    @Test
    fun `duplicate opId is idempotent`() {
        val op = operation(opId = "op-1", deviceId = "X", updatedAtMs = 1_000L)

        val single = OperationMerger.resolve(listOf(op))
        val duplicated = OperationMerger.resolve(listOf(op, op.copy()))

        assertEquals(MergeResult.Resolved(op), single)
        assertEquals(single, duplicated)
    }

    @Test
    fun `reordering equal timestamp and device id non-winners does not change the resolved result`() {
        val firstTiedLoser = operation(opId = "op-1", deviceId = "same-device", updatedAtMs = 5_000L)
        val secondTiedLoser = operation(opId = "op-2", deviceId = "same-device", updatedAtMs = 5_000L)
        val winner =
            operation(
                opId = "op-3",
                deviceId = "later-device",
                updatedAtMs = 6_000L,
            )

        val forward = OperationMerger.resolve(listOf(firstTiedLoser, secondTiedLoser, winner))
        val reversed = OperationMerger.resolve(listOf(secondTiedLoser, firstTiedLoser, winner))

        assertEquals(MergeResult.Resolved(winner), forward)
        assertEquals(forward, reversed)
    }

    private fun operation(
        opId: String,
        deviceId: String,
        updatedAtMs: Long,
        opType: OpType = OpType.Upsert,
        payload: String? = """{"id":"$entityUuid","op":"$opId"}""",
    ) = Operation(
        opId = opId,
        deviceId = deviceId,
        entityKind = EntityKind.Transaction,
        entityUuid = entityUuid,
        opType = opType,
        payload = payload,
        updatedAt = Instant.ofEpochMilli(updatedAtMs),
    )
}
