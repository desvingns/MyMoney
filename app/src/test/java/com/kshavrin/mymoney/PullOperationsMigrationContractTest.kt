package com.kshavrin.mymoney

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PullOperationsMigrationContractTest {
    @Test
    fun `pull operations qualifies workspace id in the billing gate`() {
        val migration =
            File(findRepositoryRoot(), MIGRATION_PATH)
                .readText()
                .replace("\r\n", "\n")
                .lowercase()

        assertTrue(migration.contains("from public.workspaces as workspace"))
        assertTrue(migration.contains("where workspace.id = p_workspace_id"))
        assertFalse(Regex("where\\s+id\\s*=\\s*p_workspace_id").containsMatchIn(migration))
    }

    private fun findRepositoryRoot(): File {
        val start = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(start) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, MIGRATION_PATH).isFile }
            ?: start
    }

    private companion object {
        const val MIGRATION_PATH =
            "supabase/migrations/20260821170000_fix_pull_operations_workspace_id_qualification.sql"
    }
}
