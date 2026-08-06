package com.kshavrin.mymoney

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SharedWorkspaceJoinMigrationContractTest {
    @Test
    fun `authoritative and forward join migrations qualify the pgcrypto digest call`() {
        val root = findRepositoryRoot()
        val authoritative = File(root, AUTHORITATIVE_MIGRATION_PATH).readText()
        val forward = File(root, FORWARD_MIGRATION_PATH).readText()

        assertDigestCallIsSchemaQualified(authoritative)
        assertDigestCallIsSchemaQualified(forward)
    }

    @Test
    fun `forward migration replaces join_workspace exactly once`() {
        val forward = File(findRepositoryRoot(), FORWARD_MIGRATION_PATH).readText()

        assertEquals(
            1,
            Regex(
                "(?i)\\bcreate\\s+or\\s+replace\\s+function\\s+public\\s*\\.\\s*join_workspace\\s*\\("
            ).findAll(forward).count()
        )
    }

    private fun assertDigestCallIsSchemaQualified(migration: String) {
        val joinWorkspace = requireNotNull(JOIN_WORKSPACE_FUNCTION.find(migration)?.value) {
            "join_workspace function was not found"
        }

        assertTrue(
            "join_workspace must call extensions.digest with a text sha256 argument",
            Regex("(?i)extensions\\s*\\.\\s*digest\\s*\\(\\s*p_token\\s*,\\s*'sha256'::text\\s*\\)")
                .containsMatchIn(joinWorkspace)
        )
        assertFalse(
            "join_workspace must not call an unqualified digest function",
            Regex("(?i)(?<![.\\w])digest\\s*\\(").containsMatchIn(joinWorkspace)
        )
    }

    private companion object {
        const val AUTHORITATIVE_MIGRATION_PATH = "supabase/migrations/0001_shared_workspaces.sql"
        const val FORWARD_MIGRATION_PATH =
            "supabase/migrations/20260806120000_fix_join_workspace_pgcrypto_digest.sql"
        val JOIN_WORKSPACE_FUNCTION = Regex(
            "(?is)create\\s+or\\s+replace\\s+function\\s+public\\s*\\.\\s*join_workspace\\s*\\([^)]*\\).*?\\$\\$(?:.*?)\\$\\$;"
        )

        fun findRepositoryRoot(): File {
            val start = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
            return generateSequence(start) { current -> current.parentFile }
                .firstOrNull { candidate ->
                    File(candidate, "settings.gradle.kts").isFile &&
                        File(candidate, AUTHORITATIVE_MIGRATION_PATH).isFile &&
                        File(candidate, FORWARD_MIGRATION_PATH).isFile
                } ?: start
        }
    }
}
