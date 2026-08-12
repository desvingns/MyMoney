package com.kshavrin.mymoney

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SharedMembershipHelperMigrationContractTest {
    private val migration = File(findRepositoryRoot(), MIGRATION_PATH)

    @Test
    fun `membership helper only answers for the authenticated principal`() {
        val text = migration.readText().replace("\r\n", "\n")

        assertContainsAll(
            text,
            listOf(
                "create or replace function public.is_active_member(p_workspace uuid, p_user uuid)",
                "security definer",
                "set search_path = ''",
                "p_user = (select auth.uid())",
                "from public.workspace_members",
                "where workspace_id = p_workspace",
                "and user_id = p_user",
                "and active",
            ),
        )
    }

    @Test
    fun `shared table reads expose only the columns required by clients`() {
        val text = migration.readText().replace("\r\n", "\n")

        assertContainsAll(
            text,
            listOf(
                "revoke select on table public.workspaces from authenticated;",
                "revoke select on table public.workspace_members from authenticated;",
                "revoke select on table public.workspace_invites from authenticated;",
                "grant select (id, name, owner_id, created_at)",
                "grant select (workspace_id, user_id, role, joined_at, active)",
                "on table public.workspace_invites to authenticated;",
            ),
        )
        assertFalse(
            "Invite hashes must not be readable through the table API",
            Regex("(?i)grant\\s+select\\s*\\([^)]*token_hash").containsMatchIn(text),
        )
        assertFalse(
            "Shared tables must not regain direct client writes",
            Regex("(?i)grant\\s+(insert|update|delete|truncate)").containsMatchIn(text),
        )
    }

    private fun assertContainsAll(
        text: String,
        fragments: List<String>,
    ) {
        fragments.forEach { fragment ->
            assertTrue("Expected to find '$fragment'", text.contains(fragment))
        }
    }

    private companion object {
        const val MIGRATION_PATH =
            "supabase/migrations/20260812022603_bind_membership_helper_and_limit_shared_columns.sql"

        fun findRepositoryRoot(): File {
            val start = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
            return generateSequence(start) { it.parentFile }
                .firstOrNull { candidate ->
                    File(candidate, "settings.gradle.kts").isFile &&
                        File(candidate, MIGRATION_PATH).isFile
                } ?: start
        }
    }
}
