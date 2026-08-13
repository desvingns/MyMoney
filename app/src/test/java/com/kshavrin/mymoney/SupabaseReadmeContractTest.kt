package com.kshavrin.mymoney

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SupabaseReadmeContractTest {
    private val readme = File(findRepositoryRoot(), "supabase/README.md")

    @Test
    fun `runbook documents payer based workspace billing states`() {
        val text = readme.readText().replace("\r\n", "\n")

        assertContainsAll(
            text,
            listOf(
                "`workspaces.payer_user_id` is the account whose entitlement controls the entire workspace",
                "`active` permits reads and writes",
                "`grace` permits reads only",
                "`expired` blocks shared RPCs",
            ),
        )
    }

    @Test
    fun `runbook directs clients to the entitlement RPC instead of the table`() {
        val text = readme.readText().replace("\r\n", "\n")

        assertContainsAll(
            text,
            listOf(
                "Clients read their own effective entitlement only through `get_my_entitlement()`",
                "direct access to",
                "`entitlements` is intentionally unavailable",
                "select public.get_my_entitlement();",
                "set revoked_at = now()",
            ),
        )
        assertFalse(
            "The client runbook must not instruct a direct entitlement SELECT",
            Regex("(?is)select\\s+.*?from\\s+public\\.entitlements").containsMatchIn(text),
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
            return generateSequence(start) { current -> current.parentFile }
                .firstOrNull { candidate ->
                    File(candidate, "settings.gradle.kts").isFile &&
                        File(candidate, "supabase/README.md").isFile
                } ?: start
        }
    }
}
