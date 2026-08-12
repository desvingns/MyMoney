package com.kshavrin.mymoney

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SharedTableGrantsMigrationContractTest {
    private val migration =
        File(
            findRepositoryRoot(),
            "supabase/migrations/20260812021144_harden_shared_table_grants.sql",
        )

    @Test
    fun `shared tables expose member reads without direct client writes`() {
        val text = migration.readText().replace("\r\n", "\n")

        listOf("workspaces", "workspace_members", "workspace_invites").forEach { table ->
            assertTrue(
                text.contains("revoke all on table public.$table from anon, authenticated;"),
            )
            assertTrue(
                text.contains("grant select on table public.$table to authenticated;"),
            )
        }
    }

    @Test
    fun `shared table grants migration contains no service role credential`() {
        assertFalse(
            "The shared table grants migration must not contain service-role credentials",
            Regex("(?i)service[_-]?role").containsMatchIn(migration.readText()),
        )
    }

    private companion object {
        fun findRepositoryRoot(): File {
            val start = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
            return generateSequence(start) { it.parentFile }
                .firstOrNull { candidate ->
                    File(candidate, "settings.gradle.kts").isFile &&
                        File(
                            candidate,
                            "supabase/migrations/20260812021144_harden_shared_table_grants.sql",
                        ).isFile
                } ?: start
        }
    }
}
