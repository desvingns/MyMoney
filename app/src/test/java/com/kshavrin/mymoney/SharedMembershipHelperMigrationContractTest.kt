package com.kshavrin.mymoney

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SharedMembershipHelperMigrationContractTest {
    private val migration = File(findRepositoryRoot(), MIGRATION_PATH)
    private val apiMigration = File(findRepositoryRoot(), API_MIGRATION_PATH)
    private val inviteResponseMigration = File(findRepositoryRoot(), INVITE_RESPONSE_MIGRATION_PATH)

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
        val text = apiMigration.readText().replace("\r\n", "\n")

        assertContainsAll(
            text,
            listOf(
                "revoke all on table public.workspaces from public, anon, authenticated;",
                "revoke all on table public.workspace_members from public, anon, authenticated;",
                "revoke all on table public.workspace_invites from public, anon, authenticated;",
                "grant select (id, name, owner_id, created_at)",
                "grant select (workspace_id, user_id, role, joined_at, active)",
            ),
        )
        assertFalse(
            "Invite hashes must not be readable through the table API",
            Regex("(?i)grant\\s+select\\s*\\([^)]*token_hash").containsMatchIn(text),
        )
        assertFalse(
            "Shared tables must not regain direct client writes",
            Regex("(?i)grant\\s+(all|insert|update|delete|truncate)").containsMatchIn(text),
        )
    }

    @Test
    fun `create invite returns only the client response without the token hash`() {
        val text = inviteResponseMigration.readText().replace("\r\n", "\n")
        val response = text.substringAfter("return jsonb_build_object(").substringBefore(");")

        assertContainsAll(
            text,
            listOf(
                "returns jsonb",
                "set search_path = ''",
                "if not public.is_active_member(p_workspace_id, v_user)",
                "revoke all on function public.create_invite(uuid, text) from public, anon;",
                "grant execute on function public.create_invite(uuid, text) to authenticated;",
            ),
        )
        assertContainsAll(response, listOf("'id'", "'workspace_id'", "'role'", "'expires_at'"))
        assertFalse("Invite hashes must not be returned to clients", response.contains("token_hash"))
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
        const val API_MIGRATION_PATH =
            "supabase/migrations/20260812023710_finalize_shared_api_least_privilege.sql"
        const val INVITE_RESPONSE_MIGRATION_PATH =
            "supabase/migrations/20260812023818_preserve_create_invite_json_response.sql"

        fun findRepositoryRoot(): File {
            val start = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
            return generateSequence(start) { it.parentFile }
                .firstOrNull { candidate ->
                    File(candidate, "settings.gradle.kts").isFile &&
                        File(candidate, MIGRATION_PATH).isFile &&
                        File(candidate, API_MIGRATION_PATH).isFile &&
                        File(candidate, INVITE_RESPONSE_MIGRATION_PATH).isFile
                } ?: start
        }
    }
}
