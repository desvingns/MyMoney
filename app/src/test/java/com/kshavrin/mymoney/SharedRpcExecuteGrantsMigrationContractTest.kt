package com.kshavrin.mymoney

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SharedRpcExecuteGrantsMigrationContractTest {
    private val migration = File(findRepositoryRoot(), MIGRATION_PATH)

    @Test
    fun `shared rpc grants revoke public and anon before granting authenticated`() {
        val statements = migration.readLines().map(String::trim).filter(String::isNotEmpty)
        val revokes = statements.filter { it.startsWith("revoke all on function public.") }
        val grants = statements.filter { it.startsWith("grant execute on function public.") }

        assertEquals(rpcSignatures.map(::revokeStatement), revokes)
        assertEquals(rpcSignatures.map(::grantStatement), grants)
        assertTrue(
            "All public and anon revocations must precede authenticated grants",
            statements.indexOf(revokes.last()) < statements.indexOf(grants.first()),
        )
    }

    @Test
    fun `shared rpc grants migration contains no service role credential`() {
        assertFalse(
            "The shared RPC grants migration must not contain service-role credentials",
            Regex("(?i)service[_-]?role").containsMatchIn(migration.readText()),
        )
    }

    private fun revokeStatement(signature: String) =
        "revoke all on function public.$signature from public, anon;"

    private fun grantStatement(signature: String) =
        "grant execute on function public.$signature to authenticated;"

    private companion object {
        const val MIGRATION_PATH =
            "supabase/migrations/20260812020758_harden_shared_rpc_execute_grants.sql"

        val rpcSignatures =
            listOf(
                "is_active_member(uuid, uuid)",
                "create_workspace(text)",
                "create_invite(uuid, text)",
                "revoke_invite(uuid)",
                "join_workspace(text)",
                "leave_workspace(uuid)",
                "delete_workspace(uuid)",
                "push_operation(uuid, text, bigint, text, public.entity_kind, uuid, jsonb, boolean)",
                "pull_operations(uuid, bigint, integer)",
                "list_pending_conflicts(uuid)",
                "resolve_conflict(uuid, uuid)",
            )

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
