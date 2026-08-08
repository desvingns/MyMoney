package com.kshavrin.mymoney

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AuthenticatedAccountDeletionContractTest {
    private val migration =
        File(
            findRepositoryRoot(),
            "supabase/migrations/20260807120000_delete_authenticated_account.sql",
        )

    @Test
    fun `account deletion function is security definer and explicitly authenticated`() {
        val text = migration.readText().replace("\r\n", "\n")

        assertContainsAll(
            text,
            listOf(
                "create or replace function public.delete_my_account()",
                "returns void",
                "language plpgsql",
                "security definer",
                "set search_path = ''",
                "revoke all on function public.delete_my_account() from public, anon;",
                "grant execute on function public.delete_my_account() to authenticated;",
            ),
        )
    }

    @Test
    fun `account deletion requires auth uid and remains scoped to the authenticated user`() {
        val text = migration.readText().replace("\r\n", "\n")

        assertContainsAll(
            text,
            listOf(
                "v_user uuid := auth.uid();",
                "if v_user is null then",
                "raise exception 'authentication required' using errcode = '28000';",
                "where owner_id = v_user",
                "delete from auth.users where id = v_user;",
            ),
        )
    }

    @Test
    fun `account deletion refuses active workspace members and non sole owners`() {
        val text = migration.readText().replace("\r\n", "\n")

        assertContainsAll(
            text,
            listOf(
                "account deletion requires leaving or deleting the active workspace: leave it first",
                "account deletion requires leaving or deleting the active workspace: delete it only after removing other active members",
                "v_active_member_count <> 1",
                "where workspace_id = v_workspace_id",
                "and user_id = v_user",
                "and role = 'owner'",
                "for update",
                "delete from public.workspaces where id = v_workspace_id;",
            ),
        )
    }

    @Test
    fun `account deletion migration contains no service role secret`() {
        val text = migration.readText()

        assertFalse(
            "The account-deletion migration must not contain service-role credentials",
            Regex("(?i)service[_-]?role").containsMatchIn(text),
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
        fun findRepositoryRoot(): File {
            val start = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
            return generateSequence(start) { it.parentFile }
                .firstOrNull { candidate ->
                    File(candidate, "settings.gradle.kts").isFile &&
                        File(candidate, "supabase/migrations/20260807120000_delete_authenticated_account.sql").isFile
                } ?: start
        }
    }
}
