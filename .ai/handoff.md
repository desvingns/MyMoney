# Handoff — MyMoney (cross-tool scratchpad)

Phase/release state authority: `docs/implementation_plan/PROGRESS.md` (do not restate it here).

## DONE (in progress — see BLOCKERS/NEXT, not closed)
- 2026-07-29: SPEC `shared-backend-sync-03-android-shared-mode` remains `active/` and unpushed.
  Codex resolved the original 9 failures (`c4eb0cd`), coordinator publish/lifecycle races
  (`94951044`), a durable isolated Shared outbox with Room 8→9 migration (`7c49472a`), JSON
  canonicalization plus focused tests (`80f2923c`, `c73051ed`), and scheduler cancellation on
  detach (`5bf10b1e`). Evidence: final reviewer pass, JVM Runner 1860/0 + detekt/lint OK, and
  `CloudSyncSharedCardUiTest` on Pixel_5/API34 3/0. Do not re-derive these fixes; inspect their
  commits and the active SPEC history if needed. The independent critic found the remaining
  blocker: `CloudSyncScreen` deliberately maps `LaunchSharedGoogleSignIn` to failure; no Google
  OAuth server client ID exists in `local.properties`. A real Credential Manager + Supabase Auth
  integration and external OAuth setup are needed before this SPEC can be closed/pushed.
- 2026-07-28/29: SPEC `shared-backend-sync-03-android-shared-mode` (still `active/`, NOT
  pushed) went through 5 semantic-review fix rounds fixing real bugs (leave/join
  ordering+races, cross-device entity identity moved from local Room Long id to the
  entity's own `uuid` column, Transaction FK-ref uuid remapping, currency-code
  portability, archived-account publishing, private-journal leak prevention) — full
  commit-by-commit history and exact remaining work written into
  `.claude/specs/active/shared-backend-sync-03-android-shared-mode.md` under
  "Implementation links" (read that file fully before resuming, do not re-derive from
  scratch). Runner is currently RED with 9 real test failures (2 easy stale-test updates
  in `SyncTargetTest`/`FactoryResetGatewayDetachTest`, 7 undiagnosed failures in the new
  `CloudSyncScreenContentTest` — root cause not yet known, could be `CloudSyncScreen`
  itself or the test's node-matching). Stopped here deliberately (many rounds already,
  late session) — next session resumes at "diagnose the 7 CloudSyncScreenContentTest
  failures" per the SPEC file's detailed notes, then re-run Runner, independent critic,
  Verifier, push.
- 2026-07-28: SPEC `shared-backend-sync-02-operation-api-and-conflicts` CLOSED (pushed to
  `main`, `82f8d6f2` feat → `f132bdc6`+`8b3e0a78` fixes → `c81a5f11` tests). Added
  `supabase/migrations/0002_shared_operations.sql` (append-only `operations`+`conflicts`,
  4 SECURITY DEFINER RPCs) plus `:core:domain`/`:core:network` Kotlin contracts
  (SharedOperation/SharedConflict/SharedJournalRepository, DTOs, SharedJournalRpc transport
  seam, SupabaseSharedJournalApi). Semantic review caught 2 blockers (non-atomic push
  idempotency race; `author_id` leaking through `pull_operations`) — fixed and re-verified
  clean; independent critic passed (risk high→standard) with 2 non-blocking hardening
  findings recorded in the SPEC file's "Deferred hardening" section, both candidates for
  SPEC 04 (default Supabase table grants bypass the RPC column allowlist via direct
  PostgREST SELECT — RLS restricts rows, not columns; non-atomic `base_sequence` MAX-scan
  in `resolve_conflict`, metadata-only). Gates: reviewer 0 violations, runner 1771 JVM tests
  + detekt/lint green, full verifier pass. Epic not complete (SPECs 03/04 remain in
  backlog) — feedback question and Telegram offer both skipped per epic-scoped timing.
  **Session note:** the mp-developer-standard-android subagent hit an account-wide Claude
  session-limit mid-task twice; both times it had already applied the edit to disk before
  failing on the final report-back, so resuming/re-sending the same agent and asking it to
  just commit recovered cleanly with no lost work or duplicate effort. Also caught one real
  discrepancy myself: the developer's commit message claimed a `mapCatching` fix was applied
  to 3 methods but the diff only touched 2 — always spot-check the actual file/diff against
  a developer agent's JSON claim before trusting it.
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
- (owner of the next session) **SPEC 03 is mid-flight, not a fresh start.** Read
  `.claude/specs/active/shared-backend-sync-03-android-shared-mode.md` in full first — it has
  the complete commit-by-commit history and exact remaining failures. In order:
  1. Fix `SyncTargetTest`/`FactoryResetGatewayDetachTest` (easy, stale expectations vs. the new
     `Shared` enum entry).
  2. Diagnose and fix the 7 failing tests in `feature/cloudsync/src/test/.../CloudSyncScreenContentTest.kt`
     (component-not-displayed / event-not-emitted for the Shared card's signed-out /
     signed-in-no-workspace / active-workspace states, and the setup dialog's import-choice rows) —
     read `CloudSyncScreen.kt`'s actual Shared-card composable against what the test looks up first.
  3. Re-run the Runner (`mp-runner-android.sh false`), then the required independent-critic
     semantic-review pass (risk route already flagged `independent_critic=true`), then Verifier,
     then push per the auto-push override once Verifier passes clean.
  - Separately (not blocking SPEC 03): confirm/apply `supabase/migrations/0002_shared_operations.sql`
    via the Supabase Dashboard SQL Editor if not already done (only 0001 was confirmed applied as of
    SPEC 02's close-out) before SPEC 03/04 need to exercise the operation API live.
  - Fold into SPEC 04 (realtime-hardening-e2e): the deferred-hardening items logged in both
    `.claude/specs/done/shared-backend-sync-02-operation-api-and-conflicts.md` (Supabase
    default-grant column leak, non-atomic base_sequence read) and the SPEC 03 active file (FK-ref
    causal-ordering assumption, forced-removal detection needs the real transport, soft-deleted
    transactions not published on import).
  - Also awaiting: manual deletion of the 6 logs in `archive/`.

## OWNER
- none (idle)

## BLOCKERS
- none hard-blocking. SPEC 03 is red on 9 test failures (see NEXT) — resumable, not stuck; just
  needs another session's diagnosis time. (Prior blocker — real Supabase project + Google OAuth
  for SPEC 01 — was resolved 2026-07-28: project provisioned by the user in the EU/Ireland region,
  credentials in `local.properties`. The 0002 migration still needs manual application before live
  verification, tracked as a NEXT item, not a hard blocker on further schema/code work.)
