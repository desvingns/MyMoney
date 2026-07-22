package com.kshavrin.mymoney.core.domain.repository

import com.kshavrin.mymoney.core.domain.model.BackupFile
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BackupRotationPropertyTest {
    @Test
    fun `backupsToDelete never deletes any of the newest three files`() =
        runTest {
            checkPropertyWithSeed(
                seed = 2026072608L,
                arb = Arb.list(Arb.int(0..10_000), 0..15),
            ) { values ->
                val files = filesFrom(values)
                val newestThree =
                    files
                        .sortedByDescending { it.lastModifiedEpochMs }
                        .take(BackupRepository.KEEP_NEWEST)
                        .toSet()
                val deleted = BackupRepository.backupsToDelete(files).toSet()

                assertTrue(deleted.intersect(newestThree).isEmpty())
                assertEquals(maxOf(0, files.size - BackupRepository.KEEP_NEWEST), deleted.size)
            }
        }

    @Test
    fun `backupsToDelete grows monotonically when older backups are added`() =
        runTest {
            checkPropertyWithSeed(
                seed = 2026072609L,
                arb = Arb.list(Arb.int(0..10_000), 0..15),
            ) { values ->
                var files = filesFrom(values)
                var deleted = BackupRepository.backupsToDelete(files).toSet()

                repeat(4) { offset ->
                    val older =
                        BackupFile(
                            name = "older-$offset",
                            uriString = "content://backups/older-$offset",
                            lastModifiedEpochMs = BASE_EPOCH_MS - (offset + 1L) * DAY_MILLIS,
                        )
                    files += older
                    val nextDeleted = BackupRepository.backupsToDelete(files).toSet()

                    assertTrue(deleted.all { it in nextDeleted })
                    deleted = nextDeleted
                }
            }
        }

    private fun filesFrom(values: List<Int>): List<BackupFile> =
        values.mapIndexed { index, value ->
            BackupFile(
                name = "backup-$index",
                uriString = "content://backups/$index",
                lastModifiedEpochMs = BASE_EPOCH_MS + index * DAY_MILLIS + value,
            )
        }

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
        val BASE_EPOCH_MS = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()
        const val DAY_MILLIS = 86_400_000L
    }
}
