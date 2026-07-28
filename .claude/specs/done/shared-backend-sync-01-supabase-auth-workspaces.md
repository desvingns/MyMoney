# Shared backend foundation: Supabase Auth, workspaces, membership, and invites
Epic: shared-backend-sync
Order: 01 of 04
Status: done
Depends-on: review-2026-07-22-cloud-creds-setup
Date: 2026-07-22
Shipped: 2026-07-28

## SPEC
=== SPEC ===
TASK: feature
WHAT: Provision the free-tier Supabase foundation (EU/Ireland region, as actually provisioned) for Google-authenticated shared workspaces, with owner/editor membership, a five-member cap, secure one-time invitations, and workspace lifecycle RPCs protected by RLS.
LAYERS: [data]
CHANGED_HINT: Supabase migrations/config/docs, :core:network Shared auth/API contracts, secret injection seams
TEST_TYPES: unit [integration]
CONSTRAINTS: Google Sign-In uses Supabase Auth without Drive scope; tables include workspaces, workspace_members, and hashed workspace_invites; invites expire after 24 hours and are single-use/revocable; roles are owner/editor; maximum five active members; one active workspace per user in MVP; owner transfer selects the earliest-joined active editor; the last owner cannot leave and must explicitly delete the workspace; TLS+RLS only, no E2EE; FREE TIER ONLY; no secrets committed.
=== END SPEC ===

## Acceptance

- RLS and RPC tests prove members can access only their own workspace and non-members cannot bypass it.
- Invite create/join/replay/expiry/revoke and five-member limit are deterministic and race-safe.
- Owner departure transfers ownership to the earliest-joined active editor.
- A sole owner receives a normal destructive confirmation and must delete rather than leave.
- Google identity is represented by stable Supabase user ID; email is display-only.

## Implementation links

- commit: 9255eb20 (initial), 7bd0aa6b (race-condition/grant/wiring fix from semantic review), 1eaac834 (test-vector fix), 3378d2b3 (remaining test files + push)
- files:
  - supabase/migrations/0001_shared_workspaces.sql
  - supabase/README.md
  - core/network/build.gradle.kts
  - core/network/src/main/java/com/kshavrin/mymoney/core/network/shared/SharedWorkspaceModels.kt
  - core/network/src/main/java/com/kshavrin/mymoney/core/network/shared/SharedAuth.kt
  - core/network/src/main/java/com/kshavrin/mymoney/core/network/shared/SharedWorkspaceApi.kt
  - core/network/src/main/java/com/kshavrin/mymoney/core/network/shared/SharedWorkspaceRpc.kt
  - core/network/src/main/java/com/kshavrin/mymoney/core/network/shared/SupabaseSharedWorkspaceApi.kt
  - core/network/src/main/java/com/kshavrin/mymoney/core/network/shared/InviteTokenFactory.kt
  - core/network/src/main/java/com/kshavrin/mymoney/core/network/shared/SupabaseConfig.kt
  - core/network/src/main/java/com/kshavrin/mymoney/core/network/shared/SharedConfigModule.kt
  - core/network/src/test/java/com/kshavrin/mymoney/core/network/shared/InviteTokenFactoryTest.kt
  - core/network/src/test/java/com/kshavrin/mymoney/core/network/shared/SupabaseConfigTest.kt
  - core/network/src/test/java/com/kshavrin/mymoney/core/network/shared/SupabaseSharedWorkspaceApiTest.kt

## Notes

- Real Supabase project provisioned by the user: EU (Ireland) region, not Frankfurt as originally drafted in the epic overview — overview and this SPEC's WHAT were corrected to reflect the actual region. Project URL + anon key live in git-ignored `local.properties` (`supabase.url`, `supabase.anonKey`), injected via `core:network`'s BuildConfig (mirrors the existing `DROPBOX_APP_KEY` seam).
- SQL migration is committed but NOT YET APPLIED to the live project — no Supabase CLI/Docker/service-role key is available on this machine. The user must run `supabase/migrations/0001_shared_workspaces.sql` via the Supabase Dashboard SQL Editor (see `supabase/README.md`) before any live RLS/RPC verification is possible. This is the acknowledged coverage gap for `TEST_TYPES: [integration]`.
- Semantic review caught and the developer fixed 2 blocker-severity race conditions before shipping: an owner-transfer race in `leave_workspace` (missing row lock + `active=true` filter) and a self-rejoin-via-own-invite bug in `join_workspace` (missing active-membership guard before the upsert). Independent critic (fresh evidence) then passed clean with 2 non-blocking warnings: `revoke_invite` doesn't signal a concurrent no-op, and `EXECUTE` is granted to `authenticated` but not explicitly revoked from `PUBLIC` on the SECURITY DEFINER RPCs (each function's internal `auth.uid()` check already prevents exploitation) — left as follow-up hardening, not blockers.
- Verified: layer-boundary reviewer 0 violations; runner 1729/0 JVM tests + detekt/lint ok; verifier pass (hilt_graph ok, tests_exist ok; nav/room/ui-language checks n/a — data-only foundation, no consumer wired yet).
