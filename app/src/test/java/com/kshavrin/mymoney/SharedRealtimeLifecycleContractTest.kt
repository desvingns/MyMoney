package com.kshavrin.mymoney

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SharedRealtimeLifecycleContractTest {
    @Test
    fun `realtime heartbeat and socket are cancelled with the flow`() {
        val source = File(findRepositoryRoot(), REALTIME_SOURCE).readText().replace("\r\n", "\n")

        assertTrue(source.contains("heartbeatJob ="))
        assertTrue(source.contains("delay(HEARTBEAT_INTERVAL_MILLIS)"))
        assertTrue(source.contains("heartbeatMessage(heartbeatRef++)"))
        assertTrue(source.contains("heartbeatJob?.cancel()"))
        assertTrue(source.contains("socket.cancel()"))
    }

    @Test
    fun `realtime joins private workspace topic and never subscribes to postgres row payloads`() {
        val source = File(findRepositoryRoot(), REALTIME_SOURCE).readText().replace("\r\n", "\n")

        assertTrue(source.contains("put(\"private\", true)"))
        assertTrue(source.contains("workspace:${'$'}workspaceId:operations"))
        assertTrue(source.contains("put(\"postgres_changes\", buildJsonArray {})"))
        assertTrue(source.contains("OPERATION_AVAILABLE_EVENT"))
    }

    private companion object {
        const val REALTIME_SOURCE =
            "core/network/src/main/java/com/kshavrin/mymoney/core/network/shared/SupabaseSharedRealtime.kt"

        fun findRepositoryRoot(): File {
            val start = File(System.getProperty("user.dir")).absoluteFile
            return generateSequence(start) { it.parentFile }
                .firstOrNull { candidate ->
                    File(candidate, "settings.gradle.kts").isFile &&
                        File(candidate, REALTIME_SOURCE).isFile
                } ?: start
        }
    }
}
