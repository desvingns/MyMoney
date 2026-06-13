package com.kshavrin.mymoney.core.sync.worker

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class PruneDeletedWorkerTest {
    @Test
    fun `prune cutoff is thirty days before now`() {
        val now = Instant.parse("2026-05-20T10:00:00Z")

        val cutoff = PruneDeletedWorker.pruneCutoff(now)

        assertEquals(now.minus(30, ChronoUnit.DAYS), cutoff)
    }

    @Test
    fun `prune cutoff uses the declared retention window`() {
        val now = Instant.parse("2026-05-20T10:00:00Z")

        val cutoff = PruneDeletedWorker.pruneCutoff(now)

        assertEquals(now.minus(PruneDeletedWorker.RETENTION_DAYS, ChronoUnit.DAYS), cutoff)
    }
}
