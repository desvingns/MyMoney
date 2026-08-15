# Handoff — MyMoney (cross-tool scratchpad)

Phase/release state authority: `docs/implementation_plan/PROGRESS.md` (do not restate it here).

## DONE (in progress — see BLOCKERS/NEXT, not closed)
- 2026-08-15: Closed `plus-subscription-gating-06-local-only-transition` (Claude MP `--feature
  --next`, resumed from a handoff left by a prior Codex session that stopped after a 3rd
  semantic-review blocker-pass and an `mp-architect` PREFLIGHT verdict of `PATCH ALLOWED`).
  Three more repair cycles landed real fixes: fail-closed auth/role-recovery while LocalOnly
  (never `clearBinding`/`sharedStore.clear`/`clearSharedOutbox`), coordinator-owned
  snapshot→durable-commit→teardown ordering for all three `LocalOnlyReason`s (the
  `RemoteKillswitch` path was missed in the first fix and caught by a second semantic-review
  pass), a fail-safe `Unknown` tri-state role default, and — found independently by the
  routed independent critic — a TOCTOU race between the realtime supervisor's auth-failure
  cleanup and `detachToLocalOnly`'s DataStore commit (closed with `operationMutex.withLock`).
  Commits `b4335959`, `2a708a9a`, `ef2e10e8` pushed to `main` (`8f330a96..ef2e10e8`). Final full runner: 2248 passed / 0 failed / 0 skipped, detekt/lint
  green; deterministic reviewer 0 violations at every cycle; full Verifier passed. Three
  non-blocking findings (a teardown-failure return/commit mismatch, a test that doesn't
  actually prove the auth-race mutex is load-bearing, an untriaged `SyncError.Conflict`
  status question) are logged under "Deferred hardening" in the SPEC file, now in `done/`.
  Epic `plus-subscription-gating` is NOT complete — SPECs 07-10 remain in `backlog/`.
- 2026-08-14: Closed `support-rewarded-ads-03-ad-gateway-admob` locally in commits
  `bb71373e`, `082bbbbd`, `ee32a69f`, `ac7055cf`, `7d4189ee`, `4714cd8d`, and
  `e440e9ec`. Added the AdMob/UMP gateway, authenticated SSV token flow, bounded
  cancellation-safe loading, process-local no-fill state, and focused fake-based tests.
  Full verifier passed; final JVM evidence is 3753/0/0/0, with a post-Hilt scoped sanity
  check at 263/0/0/0. The next slice remains queued in the same epic.
- 2026-08-06: Closed shared-backend-sync SPEC 04 and the standalone pgcrypto join bugfix.
  Commits: `ea914537` (schema-qualified `extensions.digest` plus forward migration),
  `716216fd` (Realtime/security/recovery hardening), and `a9c9a877` (separate device
  disconnect and final-owner deletion flow). Scoped JVM verification is 370 passed / 0 failed /
  0 skipped; the user confirmed the multi-user/device E2E and Pixel 8 invite-join fix.
  All four shared-backend-sync child SPECs and the epic overview are now in `done/`.
- 2026-08-05: Dashboard inline transaction records bugfix completed locally in commits
  `0e7a7330`, `f4351442`, `3133773a`, `170586c9`. Restored category-tile expansion on the
  dashboard, row navigation, period/selection stale-result guards, mixed-currency ConvertTo
  grouping, and 48dp touch targets. Evidence: full MP runner 1954/0 with detekt/lint green,
  focused mixed-currency JVM regression passed, and focused dashboard connected regression 1/1
  on Pixel_5/API34. Final APK launch on Pixel 5 passed after forced KSP/Hilt regeneration.
  Pixel 9 AVD exited before boot during repeat smoke; its final APK was not re-verified.
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

## 2026-08-12 MONETIZATION BACKEND CLOSE-OUT
- Codex completed the server-side monetization foundation for `com.kshavrin.mymoney`: Supabase schema/RLS, whitelist, one-time activation-code path, Google Play API/RTDN via Pub/Sub + OIDC, AdMob SSV with idempotency, and Firebase Analytics project setup.
- Supabase Edge Functions are deployed and active; `admob-ssv` is version 10 after handling AdMob's signed dummy URL-verification callback without granting rewards. No secret values are stored in this handoff.
- The backend is ready for Android implementation. Next client contract: authenticated call to `create-ad-reward-token`, pass returned `custom_data` into the rewarded ad request, and send Play purchase tokens to `bind-google-play-purchase`.
- Android production code was intentionally not changed. Actual rewarded-ad end-to-end testing remains deferred until the client supplies signed `custom_data`.

## DECISIONS
- `.ai/memory/MEMORY.md` is the shared durable memory for both tools; tool-local
  memories are mirrors.

## NEXT
- No active MP SPEC remains. `plus-subscription-gating-06-local-only-transition` is closed
  (2026-08-15). The epic's remaining backlog items are SPECs 07 (entitlement notifications), 08
  (monetization analytics events), 09 (Shared killswitch + release flip), 10 (privacy policy
  monetization update) — `--feature --next` will pick up SPEC 07 next.
- Three deferred-hardening follow-ups logged in the now-`done/` SPEC 06 file, none blocking:
  a `detachToLocalOnly` teardown-failure return/commit mismatch (self-healing via the realtime
  supervisor's next sync attempt), a regression test that doesn't actually prove the
  AUTH-RACE-001 mutex fix is load-bearing under real concurrency, and an untriaged
  `SyncError.Conflict`-while-LocalOnly status question.
- Optional housekeeping remains: manual deletion of the six archived root logs when convenient.

## OWNER
- none (idle)

## BLOCKERS
- none hard-blocking. The Supabase project is provisioned in the EU/Ireland region and the
  shared-sync close-out was manually verified by the user. Public rollout remains disabled by the
  experimental gate and the existing release/DevOps prerequisites remain tracked in `PROGRESS.md`.
