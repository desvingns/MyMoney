package com.kshavrin.mymoney.core.sync

import com.kshavrin.mymoney.core.domain.sync.EntityKind
import com.kshavrin.mymoney.core.domain.sync.OpType
import com.kshavrin.mymoney.core.domain.sync.Operation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class JournalSerializerTest {
    private val serializer = JournalSerializer()

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun op(
        opId: String = "op-1",
        deviceId: String = "device-A",
        entityKind: EntityKind = EntityKind.Transaction,
        entityUuid: String = "uuid-txn-1",
        opType: OpType = OpType.Upsert,
        payload: String? = """{"amount":100}""",
        updatedAt: Instant = Instant.ofEpochMilli(1_700_000_000_000L),
    ) = Operation(
        opId = opId,
        deviceId = deviceId,
        entityKind = entityKind,
        entityUuid = entityUuid,
        opType = opType,
        payload = payload,
        updatedAt = updatedAt,
    )

    // -----------------------------------------------------------------------
    // Single-operation round-trip
    // -----------------------------------------------------------------------

    @Test
    fun `round-trip preserves opId`() {
        val original = op(opId = "unique-op-id-42")
        val result = serializer.decode(serializer.encode(listOf(original))).single()
        assertEquals("unique-op-id-42", result.opId)
    }

    @Test
    fun `round-trip preserves deviceId`() {
        val original = op(deviceId = "device-XYZ")
        val result = serializer.decode(serializer.encode(listOf(original))).single()
        assertEquals("device-XYZ", result.deviceId)
    }

    @Test
    fun `round-trip preserves entityKind Transaction`() {
        val original = op(entityKind = EntityKind.Transaction)
        val result = serializer.decode(serializer.encode(listOf(original))).single()
        assertEquals(EntityKind.Transaction, result.entityKind)
    }

    @Test
    fun `round-trip preserves entityKind Category`() {
        val original = op(entityKind = EntityKind.Category)
        val result = serializer.decode(serializer.encode(listOf(original))).single()
        assertEquals(EntityKind.Category, result.entityKind)
    }

    @Test
    fun `round-trip preserves entityKind Account`() {
        val original = op(entityKind = EntityKind.Account)
        val result = serializer.decode(serializer.encode(listOf(original))).single()
        assertEquals(EntityKind.Account, result.entityKind)
    }

    @Test
    fun `round-trip preserves entityUuid`() {
        val original = op(entityUuid = "550e8400-e29b-41d4-a716-446655440000")
        val result = serializer.decode(serializer.encode(listOf(original))).single()
        assertEquals("550e8400-e29b-41d4-a716-446655440000", result.entityUuid)
    }

    @Test
    fun `round-trip preserves opType Upsert`() {
        val original = op(opType = OpType.Upsert)
        val result = serializer.decode(serializer.encode(listOf(original))).single()
        assertEquals(OpType.Upsert, result.opType)
    }

    @Test
    fun `round-trip preserves opType Delete`() {
        val original = op(opType = OpType.Delete)
        val result = serializer.decode(serializer.encode(listOf(original))).single()
        assertEquals(OpType.Delete, result.opType)
    }

    @Test
    fun `round-trip preserves updatedAt epoch millis`() {
        val instant = Instant.ofEpochMilli(1_700_123_456_789L)
        val original = op(updatedAt = instant)
        val result = serializer.decode(serializer.encode(listOf(original))).single()
        assertEquals(instant, result.updatedAt)
    }

    @Test
    fun `round-trip preserves JSON object payload`() {
        val payload = """{"amount":100,"note":"coffee"}"""
        val original = op(payload = payload)
        val result = serializer.decode(serializer.encode(listOf(original))).single()
        assertEquals(payload, result.payload)
    }

    @Test
    fun `round-trip produces null payload when original payload is null`() {
        val original = op(payload = null)
        val result = serializer.decode(serializer.encode(listOf(original))).single()
        assertNull(result.payload)
    }

    // -----------------------------------------------------------------------
    // Batch round-trip
    // -----------------------------------------------------------------------

    @Test
    fun `encode produces one JSONL line per operation`() {
        val ops =
            listOf(
                op(opId = "op-A"),
                op(opId = "op-B"),
                op(opId = "op-C"),
            )
        val text = serializer.encode(ops).toString(Charsets.UTF_8)
        val lines = text.lines().filter { it.isNotBlank() }
        assertEquals(3, lines.size)
    }

    @Test
    fun `decode returns all operations from a multi-line JSONL batch`() {
        val ops =
            listOf(
                op(opId = "op-1", entityKind = EntityKind.Transaction),
                op(opId = "op-2", entityKind = EntityKind.Category),
                op(opId = "op-3", entityKind = EntityKind.Account),
            )
        val decoded = serializer.decode(serializer.encode(ops))
        assertEquals(3, decoded.size)
        assertEquals("op-1", decoded[0].opId)
        assertEquals("op-2", decoded[1].opId)
        assertEquals("op-3", decoded[2].opId)
    }

    @Test
    fun `batch round-trip preserves all fields for every operation`() {
        val ops =
            listOf(
                op(
                    opId = "op-X",
                    deviceId = "dev-1",
                    entityKind = EntityKind.Transaction,
                    entityUuid = "uuid-1",
                    opType = OpType.Upsert,
                    payload = """{"v":1}""",
                    updatedAt = Instant.ofEpochMilli(1_000L),
                ),
                op(
                    opId = "op-Y",
                    deviceId = "dev-2",
                    entityKind = EntityKind.Category,
                    entityUuid = "uuid-2",
                    opType = OpType.Delete,
                    payload = null,
                    updatedAt = Instant.ofEpochMilli(2_000L),
                ),
            )
        val decoded = serializer.decode(serializer.encode(ops))

        assertEquals(ops[0].opId, decoded[0].opId)
        assertEquals(ops[0].deviceId, decoded[0].deviceId)
        assertEquals(ops[0].entityKind, decoded[0].entityKind)
        assertEquals(ops[0].entityUuid, decoded[0].entityUuid)
        assertEquals(ops[0].opType, decoded[0].opType)
        assertEquals(ops[0].payload, decoded[0].payload)
        assertEquals(ops[0].updatedAt, decoded[0].updatedAt)

        assertEquals(ops[1].opId, decoded[1].opId)
        assertEquals(ops[1].deviceId, decoded[1].deviceId)
        assertEquals(ops[1].entityKind, decoded[1].entityKind)
        assertEquals(ops[1].opType, decoded[1].opType)
        assertNull(decoded[1].payload)
        assertEquals(ops[1].updatedAt, decoded[1].updatedAt)
    }

    // -----------------------------------------------------------------------
    // Edge cases
    // -----------------------------------------------------------------------

    @Test
    fun `encode and decode of empty list produces empty list`() {
        val encoded = serializer.encode(emptyList())
        val decoded = serializer.decode(encoded)
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun `decode ignores blank lines in input`() {
        val singleOp = op(opId = "op-solo")
        val rawWithBlanks =
            "\n\n" +
                serializer.encode(listOf(singleOp)).toString(Charsets.UTF_8) +
                "\n\n"
        val decoded = serializer.decode(rawWithBlanks.toByteArray(Charsets.UTF_8))
        assertEquals(1, decoded.size)
        assertEquals("op-solo", decoded[0].opId)
    }

    @Test
    fun `payload containing nested JSON object survives round-trip`() {
        val nested = """{"outer":{"inner":42},"arr":[1,2,3]}"""
        val original = op(payload = nested)
        val result = serializer.decode(serializer.encode(listOf(original))).single()
        assertEquals(nested, result.payload)
    }

    @Test
    fun `encode output is valid UTF-8 bytes`() {
        val original = op(payload = """{"note":"кофе"}""")
        val bytes = serializer.encode(listOf(original))
        val text = bytes.toString(Charsets.UTF_8)
        assertTrue(text.isNotBlank())
        val decoded = serializer.decode(bytes).single()
        assertEquals("""{"note":"кофе"}""", decoded.payload)
    }

    @Test
    fun `encode produces lines that each parse independently as JSON objects`() {
        val ops = listOf(op(opId = "op-A"), op(opId = "op-B"))
        val text = serializer.encode(ops).toString(Charsets.UTF_8)
        val lines = text.lines().filter { it.isNotBlank() }
        lines.forEach { line ->
            assertTrue(
                "Each JSONL line must start with '{' but was: $line",
                line.trimStart().startsWith("{"),
            )
        }
    }
}
