package com.kshavrin.mymoney.core.network.shared

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PrivateRealtimeAuthorizationMigrationTest {
    @Test
    fun `private Realtime read authorization does not test the synthetic message private default`() {
        val root = findRepositoryRoot()
        val migration = File(root, MIGRATION_PATH).readText().replace("\r\n", "\n")
        val helper = File(root, HELPER_MIGRATION_PATH).readText().replace("\r\n", "\n")

        assertTrue(migration.contains("drop policy if exists realtime_workspace_members_receive_operation_notifications"))
        assertTrue(migration.contains("on realtime.messages\n    for select\n    to authenticated"))
        assertTrue(migration.contains("realtime.messages.extension = 'broadcast'"))
        assertTrue(migration.contains("private.can_receive_workspace_operation_notifications("))
        assertTrue(migration.contains("(select realtime.topic())"))
        assertTrue(migration.contains("(select auth.uid())"))
        assertFalse(migration.contains("realtime.messages.private"))

        assertTrue(helper.contains("create schema if not exists private"))
        assertTrue(helper.contains("alter function public.can_receive_workspace_operation_notifications(text, uuid)\n    set schema private"))
        assertTrue(helper.contains("grant execute on function private.can_receive_workspace_operation_notifications(text, uuid)\n    to authenticated"))
    }

    private companion object {
        const val MIGRATION_PATH = "supabase/migrations/20260805130000_fix_private_realtime_read_authorization.sql"
        const val HELPER_MIGRATION_PATH = "supabase/migrations/20260802034731_private_realtime_authorization_helper.sql"

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
