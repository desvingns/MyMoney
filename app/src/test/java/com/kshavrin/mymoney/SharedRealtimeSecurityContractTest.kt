package com.kshavrin.mymoney

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SharedRealtimeSecurityContractTest {
    private val root = findRepositoryRoot()
    private val operationsMigration = File(root, "supabase/migrations/0002_shared_operations.sql")
    private val grantsMigration = File(root, "supabase/migrations/0003_shared_realtime_security.sql")
    private val realtimeMigration = File(root, "supabase/migrations/0004_private_workspace_realtime.sql")

    @Test
    fun `direct operation reads and writes remain membership gated`() {
        val operations = operationsMigration.readText().replace("\r\n", "\n")
        val grants = grantsMigration.readText().replace("\r\n", "\n")

        assertTrue(operations.contains("create policy operations_select_members on public.operations"))
        assertTrue(operations.contains("for select using (public.is_active_member(workspace_id, auth.uid()))"))
        assertTrue(operations.contains("if not public.is_active_member(p_workspace_id, auth.uid())"))
        assertTrue(operations.contains("raise exception 'not a workspace member' using errcode = '42501'"))
        assertTrue(grants.contains("revoke all on table public.operations from anon;"))
        assertTrue(grants.contains("revoke all on table public.operations from authenticated;"))
        assertTrue(grants.contains("revoke all on table public.conflicts from anon;"))
        assertTrue(grants.contains("revoke all on table public.conflicts from authenticated;"))
        assertFalse("author_id must not be exposed by direct operation reads", grants.contains("author_id,\n"))
        assertFalse(operations.contains("create policy operations_insert"))
        assertFalse(operations.contains("create policy operations_update"))
        assertFalse(operations.contains("create policy operations_delete"))
    }

    @Test
    fun `private realtime policy admits only active members for private broadcast topics`() {
        val realtime = realtimeMigration.readText().replace("\r\n", "\n")

        assertTrue(realtime.contains("alter publication supabase_realtime drop table public.operations;"))
        assertTrue(realtime.contains("p_topic = 'workspace:' || workspace_id::text || ':operations'"))
        assertTrue(realtime.contains("where user_id = p_user"))
        assertTrue(realtime.contains("and active"))
        assertTrue(realtime.contains("on realtime.messages"))
        assertTrue(realtime.contains("for select\n    to authenticated"))
        assertTrue(realtime.contains("realtime.messages.extension = 'broadcast'"))
        assertTrue(realtime.contains("realtime.messages.private"))
        assertTrue(realtime.contains("(select auth.uid())"))
        assertTrue(realtime.contains("revoke all on function public.can_receive_workspace_operation_notifications"))
    }

    @Test
    fun `operation trigger sends an empty private hint and never operation payload`() {
        val realtime = realtimeMigration.readText().replace("\r\n", "\n")

        assertTrue(realtime.contains("perform realtime.send(\n        '{}'::jsonb"))
        assertTrue(realtime.contains("'operation_available'"))
        assertTrue(realtime.contains("'workspace:' || new.workspace_id::text || ':operations'"))
        assertTrue(realtime.contains("        true\n    );"))
        assertTrue(realtime.contains("after insert on public.operations"))
    }

    private companion object {
        fun findRepositoryRoot(): File {
            val start = File(System.getProperty("user.dir")).absoluteFile
            return generateSequence(start) { it.parentFile }
                .firstOrNull { candidate ->
                    File(candidate, "settings.gradle.kts").isFile &&
                        File(candidate, "supabase/migrations/0004_private_workspace_realtime.sql").isFile
                } ?: start
        }
    }
}
