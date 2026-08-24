package com.kshavrin.mymoney

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SupporterPurchasesMigrationContractTest {
    private val migration = File(findRepositoryRoot(), MIGRATION_PATH)

    @Test
    fun `migration creates an append-only purchase journal with required constraints`() {
        val sql = normalized()

        assertContainsAll(
            sql,
            listOf(
                "create table public.supporter_purchases (",
                "id uuid primary key default gen_random_uuid()",
                "user_id uuid not null references auth.users (id) on delete cascade",
                "product_id text not null",
                "purchase_token text not null unique",
                "purchased_at timestamptz not null",
                "created_at timestamptz not null default now()",
                "create index ix_supporter_purchases_user_purchased_at",
            ),
        )
        assertFalse(Regex("(?im)^\\s*grant\\s+(update|delete|truncate)").containsMatchIn(sql))
    }

    @Test
    fun `migration enables own-row RLS and grants only authenticated select and insert`() {
        val sql = normalized()

        assertContainsAll(
            sql,
            listOf(
                "alter table public.supporter_purchases enable row level security;",
                "create policy supporter_purchases_select_own on public.supporter_purchases",
                "for select to authenticated",
                "using ((select auth.uid()) = user_id);",
                "create policy supporter_purchases_insert_own on public.supporter_purchases",
                "for insert to authenticated",
                "with check ((select auth.uid()) = user_id);",
                "revoke all on table public.supporter_purchases from public, anon, authenticated;",
                "grant select, insert on table public.supporter_purchases to authenticated;",
            ),
        )
        assertFalse(Regex("(?im)^\\s*grant\\s+.*\\bto\\s+(anon|public)\\b").containsMatchIn(sql))
    }

    @Test
    fun `after-insert trigger grants the cosmetic supporter badge idempotently`() {
        val sql = normalized()
        val function =
            sql
                .substringAfter("create or replace function private.grant_supporter_from_purchase()")
                .substringBefore("create trigger supporter_purchases_grant_supporter")
        val trigger = sql.substringAfter("create trigger supporter_purchases_grant_supporter")

        assertContainsAll(
            function,
            listOf(
                "returns trigger",
                "security definer",
                "set search_path = ''",
                "insert into public.supporters",
                "new.user_id",
                "'google_play'",
                "new.purchase_token",
                "on conflict (user_id) do nothing",
                "return new;",
                "revoke all on function private.grant_supporter_from_purchase() from public, anon, authenticated;",
            ),
        )
        assertContainsAll(
            trigger,
            listOf(
                "after insert on public.supporter_purchases",
                "for each row",
                "execute function private.grant_supporter_from_purchase();",
            ),
        )
    }

    @Test
    fun `migration timestamp follows the already applied monetization migrations`() {
        val timestamp = Regex("^(\\d{14})_").find(MIGRATION_PATH.substringAfterLast('/'))!!.groupValues[1].toLong()

        assertTrue(timestamp > 20260812133214L)
    }

    private fun normalized(): String = migration.readText().replace("\r\n", "\n")

    private fun assertContainsAll(
        text: String,
        fragments: List<String>,
    ) {
        fragments.forEach { fragment ->
            assertTrue("Expected to find '$fragment'", text.contains(fragment))
        }
    }

    private companion object {
        const val MIGRATION_PATH = "supabase/migrations/20260813090000_supporter_purchases.sql"

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
