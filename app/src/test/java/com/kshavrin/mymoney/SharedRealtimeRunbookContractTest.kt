package com.kshavrin.mymoney

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SharedRealtimeRunbookContractTest {
    private val runbook = File(findRepositoryRoot(), "docs/SHARED_SYNC_REALTIME_RUNBOOK.md")

    @Test
    fun `security runbook records member and non-member denial evidence`() {
        val text = runbook.readText().replace("\r\n", "\n")

        assertTrue(text.contains("owner A, member B, and non-member C"))
        assertTrue(text.contains("pull_operations"))
        assertTrue(text.contains("push_operation"))
        assertTrue(text.contains("list_pending_conflicts"))
        assertTrue(text.contains("resolve_conflict"))
        assertTrue(text.contains("direct REST reads and writes"))
        assertTrue(text.contains("payload.config.private=true"))
        assertTrue(text.contains("not `status=ok`"))
        assertTrue(text.contains("must not expose `author_id`"))
    }

    @Test
    fun `two-user evidence runbook covers durable recovery and all conflict races`() {
        val text = runbook.readText().replace("\r\n", "\n")

        assertTrue(text.contains("two independent Pixel 5 API 34"))
        assertTrue(text.contains("Disable network"))
        assertTrue(text.contains("killing and relaunching"))
        assertTrue(text.contains("intentional Realtime disconnect/reconnect"))
        assertTrue(text.contains("update/update, update/delete, and delete/update"))
        assertTrue(text.contains("free-tier sleeping project"))
        assertTrue(text.contains("bounded retry"))
        assertTrue(text.contains("five-member limit"))
        assertTrue(text.contains("final-owner deletion"))
    }

    private companion object {
        fun findRepositoryRoot(): File {
            val start = File(System.getProperty("user.dir")).absoluteFile
            return generateSequence(start) { it.parentFile }
                .firstOrNull { candidate ->
                    File(candidate, "settings.gradle.kts").isFile &&
                        File(candidate, "docs/SHARED_SYNC_REALTIME_RUNBOOK.md").isFile
                } ?: start
        }
    }
}
