package com.kshavrin.mymoney

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SupabaseEntitlementGatingMigrationContractTest {
    private val root = findRepositoryRoot()
    private val gatingMigration = File(root, GATING_MIGRATION_PATH)

    @Test
    fun `entitlement gating migration follows all create invite prerequisites`() {
        val migrationNames =
            listOf(
                INVITE_SCHEMA_MIGRATION_PATH,
                INVITE_RESPONSE_MIGRATION_PATH,
                MONETIZATION_MIGRATION_PATH,
                ADMOB_MIGRATION_PATH,
                GATING_MIGRATION_PATH,
            ).map { it.substringAfterLast('/') }

        assertTrue(
            "The JSON response migration must precede monetization and gating migrations",
            migrationTimestamp(migrationNames[1]) < migrationTimestamp(migrationNames[2]),
        )
        assertTrue(
            "The monetization migration must precede the AdMob trigger migration",
            migrationTimestamp(migrationNames[2]) < migrationTimestamp(migrationNames[3]),
        )
        assertTrue(
            "The entitlement gating migration must be applied last",
            migrationTimestamp(migrationNames[3]) < migrationTimestamp(migrationNames[4]),
        )
    }

    @Test
    fun `create invite changes return type only after dropping the established signature`() {
        val gating = normalized(gatingMigration)
        val inviteResponse = normalized(File(root, INVITE_RESPONSE_MIGRATION_PATH))
        val drop = "drop function if exists public.create_invite(uuid, text);"
        val create = "create function public.create_invite(p_workspace_id uuid, p_token_hash text)"

        assertEquals(1, Regex("(?im)^drop function if exists public\\.create_invite\\(uuid, text\\);$").findAll(gating).count())
        assertEquals(1, Regex("(?im)^create function public\\.create_invite\\(p_workspace_id uuid, p_token_hash text\\)").findAll(gating).count())
        assertFalse(
            "A return type change cannot use CREATE OR REPLACE for the established signature",
            Regex("(?im)^create or replace function public\\.create_invite").containsMatchIn(gating),
        )
        assertTrue(gating.indexOf(drop) >= 0)
        assertTrue(gating.indexOf(create) > gating.indexOf(drop))
        assertTrue(inviteResponse.contains("drop function public.create_invite(uuid, text);"))

        val establishedResponse = responseFields(functionDefinition(inviteResponse, "public", "create_invite"))
        val gatedResponse = responseFields(functionDefinition(gating, "public", "create_invite"))
        assertEquals(establishedResponse, gatedResponse)
        assertTrue(gatedResponse.contains("'id'"))
        assertTrue(gatedResponse.contains("'workspace_id'"))
        assertTrue(gatedResponse.contains("'role'"))
        assertTrue(gatedResponse.contains("'expires_at'"))
        assertFalse("The invite token hash must never be returned", gatedResponse.contains("token_hash"))
    }

    @Test
    fun `effective entitlement keeps Google Play grace inside an exclusive seven day cutoff`() {
        val effective = functionDefinition(normalized(gatingMigration), "private", "effective_entitlement")

        assertContainsAll(
            effective,
            listOf(
                "entitlement.revoked_at is null",
                "entitlement.provider = 'google_play'",
                "entitlement.expires_at + interval '7 days' > now()",
                "when entitlement.provider = 'google_play' then interval '7 days'",
            ),
        )
        assertFalse(
            "The seven-day grace cutoff must be exclusive",
            effective.contains("interval '7 days' >= now()"),
        )
        assertFalse(
            "Non-Google-Play entitlements must not receive the Google Play grace predicate",
            Regex(
                "(?is)entitlement\\.provider\\s*(?:<>|!=)\\s*'google_play'.*?interval '7 days'",
            ).containsMatchIn(effective),
        )
    }

    @Test
    fun `direct entitlement reads are revoked while the authenticated read RPC remains callable`() {
        val monetization = normalized(File(root, MONETIZATION_MIGRATION_PATH))
        val gating = normalized(gatingMigration)
        val getMyEntitlement = functionDefinition(gating, "public", "get_my_entitlement")

        assertTrue(monetization.contains("grant select on table public.entitlements to authenticated;"))
        assertTrue(gating.contains("revoke all on table public.entitlements from public, anon, authenticated;"))
        assertContainsAll(
            getMyEntitlement,
            listOf(
                "returns json",
                "security definer",
                "set search_path = ''",
                "from private.effective_entitlement(v_user)",
                "return json_build_object(",
            ),
        )
        assertContainsAll(
            gating,
            listOf(
                "revoke all on function public.get_my_entitlement() from public, anon;",
                "grant execute on function public.get_my_entitlement() to authenticated;",
            ),
        )
        assertFalse(
            "The gating migration must not re-grant direct entitlement table reads",
            Regex("(?im)^grant select on table public\\.entitlements").containsMatchIn(gating),
        )
    }

    @Test
    fun `existing workspaces are backfilled and initial recompute fails closed`() {
        val gating = normalized(gatingMigration)
        val recompute = functionDefinition(gating, "private", "recompute_workspace_billing_state")
        val recomputeCall = "select private.recompute_workspace_billing_state();"
        val cronExtension = "create extension if not exists pg_cron;"

        assertContainsAll(
            gating,
            listOf(
                "set payer_user_id = owner_id",
                "where payer_user_id is null;",
                "alter column payer_user_id set not null;",
                "billing_state text not null default 'active'",
                "select private.recompute_workspace_billing_state();",
            ),
        )
        assertContainsAll(
            recompute,
            listOf(
                "left join lateral",
                "from public.workspaces as workspace",
                "effective_entitlement(workspace.payer_user_id)",
                "when entitlement.source is null then 'expired'",
                "when entitlement.source is null then null",
                "set billing_state = recomputed.billing_state",
                "billing_state_until = recomputed.billing_state_until",
            ),
        )
        assertTrue(gating.indexOf(recomputeCall) < gating.indexOf(cronExtension))

        val writeAllowed = functionDefinition(gating, "private", "workspace_write_allowed")
        assertContainsAll(
            writeAllowed,
            listOf(
                "select coalesce((",
                "workspace.billing_state = 'active'",
                "where workspace.id = p_workspace",
                "), false);",
            ),
        )
        assertFalse("Workspace billing lookup must not use the caller entitlement", writeAllowed.contains("auth.uid()"))
    }

    @Test
    fun `shared RPCs gate the payer workspace state and return entitlement required`() {
        val gating = normalized(gatingMigration)
        val writeRpcNames =
            listOf(
                "create_invite",
                "revoke_invite",
                "join_workspace",
                "leave_workspace",
                "delete_workspace",
                "push_operation",
                "resolve_conflict",
            )
        val readRpcNames = listOf("pull_operations", "list_pending_conflicts")

        writeRpcNames.forEach { name ->
            val body = functionDefinition(gating, "public", name)
            assertTrue(
                "$name must gate writes through the workspace billing helper",
                body.contains("private.workspace_write_allowed("),
            )
            assertTrue(
                "$name must expose the entitlement-specific error",
                body.contains("message = 'entitlement_required'"),
            )
        }
        readRpcNames.forEach { name ->
            val body = functionDefinition(gating, "public", name)
            assertContainsAll(
                body,
                listOf(
                    "billing_state in ('active', 'grace')",
                    "message = 'entitlement_required'",
                    "auth.uid()",
                ),
            )
        }

        val createWorkspace = functionDefinition(gating, "public", "create_workspace")
        assertContainsAll(
            createWorkspace,
            listOf(
                "from private.effective_entitlement(v_user)",
                "message = 'entitlement_required'",
                "insert into public.workspaces (name, owner_id, payer_user_id)",
                "values (p_name, v_user, v_user)",
            ),
        )
        assertFalse(
            "Shared RPCs must not read entitlement from the caller instead of the workspace payer",
            (writeRpcNames + readRpcNames).any { name ->
                functionDefinition(gating, "public", name).contains("effective_entitlement(")
            },
        )
    }

    @Test
    fun `Google Play RTDN recomputes payer workspace billing after entitlement changes`() {
        val rtdn = File(root, GOOGLE_PLAY_RTDN_PATH).readText().replace("\r\n", "\n")

        assertContainsAll(
            rtdn,
            listOf(
                "async function recomputeWorkspaceBillingState",
                "admin.rpc(\"recompute_workspace_billing_state_from_rtdn\")",
                "if (reconciliation.entitlementChangedForUserId)",
                "await recomputeWorkspaceBillingState(admin);",
            ),
        )
    }

    private fun responseFields(function: String): String =
        function.substringAfter("return jsonb_build_object(").substringBefore(");")

    private fun normalized(file: File): String = file.readText().replace("\r\n", "\n")

    private fun functionDefinition(
        sql: String,
        schema: String,
        name: String,
    ): String =
        requireNotNull(
            Regex(
                "(?is)create(?:\\s+or\\s+replace)?\\s+function\\s+${Regex.escape(schema)}\\s*\\.\\s*${Regex.escape(name)}\\s*\\([^)]*\\).*?\\$\\$(?:.*?)\\$\\$;",
            ).find(sql),
        ) { "Function $schema.$name was not found" }.value

    private fun migrationTimestamp(name: String): Long =
        requireNotNull(Regex("^(\\d{14})_").find(name)).groupValues[1].toLong()

    private fun assertContainsAll(
        text: String,
        fragments: List<String>,
    ) {
        fragments.forEach { fragment ->
            assertTrue("Expected to find '$fragment'", text.contains(fragment))
        }
    }

    private companion object {
        const val INVITE_SCHEMA_MIGRATION_PATH = "supabase/migrations/0001_shared_workspaces.sql"
        const val INVITE_RESPONSE_MIGRATION_PATH =
            "supabase/migrations/20260812023818_preserve_create_invite_json_response.sql"
        const val MONETIZATION_MIGRATION_PATH = "supabase/migrations/20260812130000_monetization_schema.sql"
        const val ADMOB_MIGRATION_PATH = "supabase/migrations/20260812140000_admob_reward_grant.sql"
        const val GATING_MIGRATION_PATH =
            "supabase/migrations/20260812150000_workspace_payer_and_entitlement_gating.sql"
        const val GOOGLE_PLAY_RTDN_PATH = "supabase/functions/google-play-rtdn/index.ts"

        fun findRepositoryRoot(): File {
            val start = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
            return generateSequence(start) { current -> current.parentFile }
                .firstOrNull { candidate ->
                    File(candidate, "settings.gradle.kts").isFile &&
                        File(candidate, GATING_MIGRATION_PATH).isFile
                } ?: start
        }
    }
}
