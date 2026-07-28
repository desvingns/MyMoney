# Handoff — MyMoney (cross-tool scratchpad)

Phase/release state authority: `docs/implementation_plan/PROGRESS.md` (do not restate it here).

## DONE
- 2026-07-28: SPEC `shared-backend-sync-01-supabase-auth-workspaces` CLOSED (pushed, `3378d2b3`,
  chain 9255eb20→7bd0aa6b→1eaac834→3378d2b3). Real free-tier Supabase project provisioned by the
  user (EU/Ireland region, not Frankfurt as originally drafted — overview + SPEC corrected).
  Delivered: SQL migration (`workspaces`/`workspace_members`/`workspace_invites`, RLS, RPCs for
  invite create/join/revoke/owner-transfer/delete) under `supabase/migrations/`, `:core:network`
  Kotlin auth/API contracts (`SharedAuth`, `SharedWorkspaceApi`/`Rpc`, `SupabaseSharedWorkspaceApi`,
  `InviteTokenFactory`, `SupabaseConfig`), secret injection via `local.properties`
  (`supabase.url`/`supabase.anonKey`) mirroring the existing `DROPBOX_APP_KEY` BuildConfig seam.
  Semantic review caught 2 blocker race conditions (owner-transfer race in `leave_workspace`,
  self-rejoin-via-own-invite in `join_workspace`) — both fixed and re-verified; independent critic
  passed clean with 2 non-blocking hardening warnings (`revoke_invite` no-op signalling,
  `PUBLIC`-not-revoked on SECURITY DEFINER grants). Gates: reviewer 0 violations, runner 1729/0 JVM
  tests + detekt/lint ok, verifier pass. **Not yet live-verifiable**: no Supabase CLI/Docker/
  service-role key on this machine, so the migration is committed but NOT applied to the project —
  the user must run it via the Supabase Dashboard SQL Editor (`supabase/README.md`) before any
  RLS/RPC integration test or SPEC 02/03/04 work can exercise it end-to-end. This was an
  intermediate epic slice (1 of 4) — post-ship feedback question and Telegram build offer were
  both skipped per epic-scoped timing (SPECs 02-04 still in backlog; nothing user-visible to try
  yet).
- 2026-07-28: SPEC `review-2026-07-35-repo-hygiene` CLOSED — final slice, so the whole
  `review-2026-07` epic (35 SPECs) is now closed; overview moved to `done/`. (b) 6 root logs
  moved to git-ignored `archive/` (manual deletion pending on the user); (c) stray "@ "
  commit-subject prefix root-caused: PowerShell here-strings fed to Git Bash `git commit -m`
  (transient 2026-06-26 artifact, no versioned source to fix); (d) dead no-arg
  `LockController.markUnlocked()` removed (`e7b9d1b5`) + 5 stale test refs migrated (`30ed4fe1`).
  Gates: reviewer pass, 1694 JVM tests + detekt + lint green, full verifier pass.
- 2026-07-26: SPEC `monefy-decoupling-01-ui-component-rename` CLOSED locally in commit
  `3c3ce219`. Renamed the design-system Monefy-prefixed UI components, consumers, tests,
  detekt baseline, and screenshot baselines. Evidence: 1655 JVM tests + 147 connected
  design-system tests green; reviewer, runner, and verifier passed. Close-out commit
  `0489a1e6` was pushed to `origin/main`.
- 2026-07-26: SPEC `review-2026-07-28-convention-plugins` CLOSED (pushed, 748a8efa). Codex had
  stopped it after its 2 runner attempts; the migration itself was already complete and correct.
  Equivalence proven against a `git worktree` at the pre-migration commit `a4e41e06`: dependency
  graphs identical for all 19 modules (2636 configurations, 32878 coordinates, 0 diffs), 1631 unit
  tests green, Kover coverage equal to 4 decimals, detekt/ktlint failure sets unchanged. One real
  regression found and fixed: a stray blank line in `macrobenchmark/build.gradle.kts`.
  **Why Codex could not finish:** `scripts/mp-runner-android.sh` is structurally incapable of
  returning `pass:true` in this repo — it runs `:app:jacocoUnitTestReport` (this project uses Kover)
  and greps for `N tests completed`, a line Gradle prints only on FAILURE. Both are model-independent.
  The protocol to use instead is now in `.claude/mp/extras/mp-runner-android.md`.
- 2026-07-10: Codex default model and all five active native `gpt-5.5` specialists
  (`mp-developer-android`, `mp-fidelity-android`, `spec-evaluator`, and both screenshot
  analyzers) were promoted to `gpt-5.6`; existing reasoning effort and sandbox modes remain
  unchanged. The 17 MP Spec native subagents also now point at the installed personal
  `mp-spec` 1.10.0 cache instead of the absent 1.8.1 cache.
- 2026-07-05: `.ai/` workspace introduced (README, memory/MEMORY.md, this file);
  AGENTS.md gained the file-deletion policy + second-brain section; CLAUDE.md
  collapsed to `@AGENTS.md` + Claude-only deltas (host device testing, token
  workflow, /mp plugin notes, auto-push policy, memory paths).

## DECISIONS
- `.ai/memory/MEMORY.md` is the shared durable memory for both tools; tool-local
  memories are mirrors.

## NEXT
- (owner of the next session) Read PROGRESS.md for the active phase as usual. Before starting
  SPEC 02 (`shared-backend-sync-02-operation-api-and-conflicts`, next in `.claude/specs/backlog/`),
  confirm with the user whether `supabase/migrations/0001_shared_workspaces.sql` has been applied
  via the Supabase Dashboard SQL Editor yet — SPEC 02 builds the operation API/conflicts on top of
  the schema SPEC 01 defined, and live RLS/RPC verification for SPEC 01 is still outstanding
  either way. Also awaiting: manual deletion of the 6 logs in `archive/`.

## OWNER
- none (idle)

## BLOCKERS
- none currently open. (Prior blocker — real Supabase project + Google OAuth for SPEC 01 — was
  resolved 2026-07-28: project provisioned by the user in the EU/Ireland region, credentials in
  `local.properties`. The SQL migration still needs manual application before live verification,
  but that is tracked as a NEXT item, not a hard blocker on further schema/code work.)
