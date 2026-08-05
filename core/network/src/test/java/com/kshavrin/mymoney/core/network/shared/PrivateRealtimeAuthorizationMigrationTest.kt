package com.kshavrin.mymoney.core.network.shared

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PrivateRealtimeAuthorizationMigrationTest {
    @Test
    fun `private Realtime read authorization does not test the synthetic message private default`() {
        val migration = File(findRepositoryRoot(), MIGRATION_PATH).readText().replace("\r\n", "\n")

        assertTrue(migration.contains("drop policy if exists realtime_workspace_members_receive_operation_notifications"))
        assertTrue(migration.contains("on realtime.messages\n    for select\n    to authenticated"))
        assertTrue(migration.contains("realtime.messages.extension = 'broadcast'"))
        assertTrue(migration.contains("private.can_receive_workspace_operation_notifications("))
        assertTrue(migration.contains("(select realtime.topic())"))
        assertTrue(migration.contains("(select auth.uid())"))
        assertFalse(migration.contains("realtime.messages.private"))
    }

    private companion object {
        const val MIGRATION_PATH = "supabase/migrations/20260805130000_fix_private_realtime_read_authorization.sql"

        fun findRepositoryRoot(): File {
            val start = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
            return generateSequence(start) { current -> current.parentFile ?: current }
                .firstOrNull { candidate ->
                    File(candidate, "settings.gradle.kts").isFile &&
                        File(candidate, MIGRATION_PATH).isFile
                } ?: start
        }
    }
}
