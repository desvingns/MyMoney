package com.kshavrin.mymoney.core.domain.sync

import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class OperationMergerPropertyTest {
    private val entityUuid = "00000000-0000-0000-0000-000000000001"
    private val baseInstant = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `resolve is idempotent after reducing input to its winning operation`() =
        runTest {
            checkPropertyWithSeed(
                seed = 2026072601L,
                arb = Arb.list(Arb.int(0..10_000), 0..8),
            ) { values ->
                val operations = operationsFrom(values)
                val winner = winnerOf(operations)
                val canonicalInput = winner?.let(::listOf) ?: emptyList()
                val result = OperationMerger.resolve(operations)

                assertEquals(result, OperationMerger.resolve(canonicalInput))
                assertEquals(result, OperationMerger.resolve(operations + canonicalInput))
            }
        }

    @Test
    fun `resolve is independent of input permutation`() =
        runTest {
            checkPropertyWithSeed(
                seed = 2026072602L,
                arb = Arb.list(Arb.int(0..10_000), 0..8),
            ) { values ->
                val operations = operationsFrom(values)
                val expected = OperationMerger.resolve(operations)

                permutationVariants(operations).forEach { permutation ->
                    assertEquals(expected, OperationMerger.resolve(permutation))
                }
            }
        }

    @Test
    fun `newest tombstone dominates older upserts`() =
        runTest {
            checkPropertyWithSeed(
                seed = 2026072603L,
                arb = Arb.list(Arb.int(0..10_000), 0..8),
            ) { values ->
                val olderUpserts =
                    values.mapIndexed { index, value ->
                        operation(
                            opId = "upsert-$index",
                            deviceId = "device-${index.toString().padStart(2, '0')}",
                            opType = OpType.Upsert,
                            payload = payload("upsert-$index"),
                            updatedAt = baseInstant.plusMillis(value.toLong()),
                        )
                    }
                val tombstone =
                    operation(
                        opId = "delete-00000000-0000-0000-0000-000000000099",
                        deviceId = "device-z",
                        opType = OpType.Delete,
                        payload = null,
                        updatedAt = baseInstant.plusMillis(20_000L),
                    )
                val operations = olderUpserts + tombstone

                permutationVariants(operations).forEach { permutation ->
                    assertEquals(
                        MergeResult.Tombstone(entityUuid),
                        OperationMerger.resolve(permutation),
                    )
                }
            }
        }

    @Test
    fun `resolve follows timestamp then device ordering`() =
        runTest {
            checkPropertyWithSeed(
                seed = 2026072604L,
                arb = Arb.int(0..10_000),
            ) { offset ->
                val timestamp = baseInstant.plusMillis(offset.toLong())
                val earlier =
                    operation(
                        opId = "earlier",
                        deviceId = "device-z",
                        opType = OpType.Upsert,
                        payload = payload("earlier"),
                        updatedAt = timestamp,
                    )
                val later =
                    operation(
                        opId = "later",
                        deviceId = "device-a",
                        opType = OpType.Delete,
                        payload = null,
                        updatedAt = timestamp.plusMillis(1L),
                    )
                val equalTimestampLowerDevice = earlier.copy(
                    opId = "equal-lower",
                    deviceId = "device-a",
                )
                val equalTimestampHigherDevice = earlier.copy(
                    opId = "equal-higher",
                    deviceId = "device-z",
                )

                assertEquals(
                    MergeResult.Tombstone(entityUuid),
                    OperationMerger.resolve(listOf(earlier, later)),
                )
                assertEquals(
                    MergeResult.Resolved(equalTimestampHigherDevice),
                    OperationMerger.resolve(
                        listOf(equalTimestampHigherDevice, equalTimestampLowerDevice),
                    ),
                )
                assertEquals(
                    MergeResult.Resolved(equalTimestampHigherDevice),
                    OperationMerger.resolve(
                        listOf(equalTimestampLowerDevice, equalTimestampHigherDevice),
                    ),
                )
            }
        }

    @Test
    fun `duplicating operations with the same opId does not change the result`() =
        runTest {
            checkPropertyWithSeed(
                seed = 2026072605L,
                arb = Arb.list(Arb.int(0..10_000), 0..8),
            ) { values ->
                val operations = operationsFrom(values)
                val duplicated = operations.flatMap { operation -> listOf(operation, operation.copy()) }

                assertEquals(
                    OperationMerger.resolve(operations),
                    OperationMerger.resolve(duplicated),
                )
            }
        }

    private fun operationsFrom(values: List<Int>): List<Operation> =
        values.mapIndexed { index, value ->
            operation(
                opId = "op-$index",
                deviceId = "device-${index.toString().padStart(2, '0')}",
                opType = if (index % 3 == 0) OpType.Delete else OpType.Upsert,
                payload = if (index % 3 == 0) null else payload("op-$index"),
                updatedAt = baseInstant.plusMillis(value.toLong()),
            )
        }

    private fun permutationVariants(operations: List<Operation>): List<List<Operation>> {
        if (operations.isEmpty()) return listOf(emptyList())
        return listOf(
            operations,
            operations.asReversed(),
            operations.sortedBy { it.updatedAt },
            operations.sortedByDescending { it.updatedAt },
            operations.drop(1) + operations.take(1),
        )
    }

    private fun winnerOf(operations: List<Operation>): Operation? =
        operations
            .distinctBy { it.opId }
            .maxWithOrNull(
                compareBy<Operation> { it.updatedAt.toEpochMilli() }
                    .thenBy { it.deviceId },
            )

    private fun operation(
        opId: String,
        deviceId: String,
        opType: OpType,
        payload: String?,
        updatedAt: Instant,
    ) =
        Operation(
            opId = opId,
            deviceId = deviceId,
            entityKind = EntityKind.Transaction,
            entityUuid = entityUuid,
            opType = opType,
            payload = payload,
            updatedAt = updatedAt,
        )

    private fun payload(opId: String) = "{\"id\":\"$entityUuid\",\"opId\":\"$opId\"}"

    private suspend fun <A> checkPropertyWithSeed(
        seed: Long,
        arb: Arb<A>,
        assertion: suspend (A) -> Unit,
    ) {
        try {
            checkAll(
                PropTestConfig(seed = seed, iterations = PROPERTY_ITERATIONS),
                arb,
            ) { value -> assertion(value) }
        } catch (failure: Throwable) {
            val error = AssertionError("Property failed; seed=$seed")
            error.initCause(failure)
            throw error
        }
    }

    private companion object {
        const val PROPERTY_ITERATIONS = 100
    }
}
