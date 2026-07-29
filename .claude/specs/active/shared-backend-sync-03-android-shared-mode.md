# Android Shared mode, join/import, backups, and membership lifecycle
Epic: shared-backend-sync
Order: 03 of 04
Status: active
Depends-on: shared-backend-sync-02-operation-api-and-conflicts
Date: 2026-07-22

## SPEC
=== SPEC ===
TASK: feature
WHAT: Add Shared as a mutually exclusive Android sync mode with Google sign-in, workspace create/join flows, safe local-data import choices, internal backups, conflict resolution UI, and leave/removal lifecycle handling.
LAYERS: [domain] [data] [presentation]
CHANGED_HINT: SyncTarget/binding orchestration, :core:database internal backup/restore, :core:sync Shared repository, :feature:cloudsync Shared/workspace/conflict flows
TEST_TYPES: unit [dao] [compose-ui] [instrumented-compose-ui]
CONSTRAINTS: Dropbox, Google Drive, and Shared are mutually exclusive; one active workspace and one active local database; no workspaceId/profile column across domain entities; before join the user explicitly chooses import or no import, with no-import selected by default; both paths create an internal safety backup before active data changes; no-import replaces active local finance data with Shared data; import publishes local Transaction/Account/Category operations; leaving or removal cuts remote access but preserves the current shared data as a personal local copy; any member may resolve conflicts; author appears only in conflict UI.
=== END SPEC ===

## Acceptance

- Switching into Shared cannot silently publish personal data or keep a personal provider syncing.
- Join-without-import creates a restorable backup before loading Shared data.
- Join-with-import preserves local financial entities and publishes them through the Shared journal.
- Internal backup list/restore is usable after failed join, leave, removal, and provider migration.
- Removed users receive access-denied state, stop background work, and retain a personal local copy.
- Conflict UI resolves either version while unrelated sync continues.

## Implementation links

- commits so far (base `b34a9b94` → current `HEAD`, all pushed to local branch, NOT yet pushed to `origin/main` — Verifier/push gate not reached):
  - `fdb7331b` — UI Designer: 6 conflict-resolution color tokens (`core/ui/.../theme/Color.kt`).
  - `450a828b` — Developer: initial Shared mode (SyncTarget/CloudProvider gain `Shared`; `SharedSyncCoordinator(Impl)`; `SharedEntityCodec`; internal backups; `feature:cloudsync` Shared card/dialogs/conflict UI). Deliberately stubbed: Supabase HTTP transport (`StubSharedTransport`, all calls fail until a real-transport SPEC), Google ID-token acquisition at Compose layer.
  - `52493e22` — fix round 1 (semantic review): leaveWorkspace no-op-on-failure + missing signOut; adoptWorkspace import-order data-loss risk (pull ran before publish); pullAndApply cursor stalled forever on one bad op; no Room transaction wrapping apply; `isMembershipActive()` had no reader.
  - `2efb2931` — fix round 2: leaveWorkspace backup-throws-abort regression from round 1; adoptWorkspace clear/setBinding/setMembershipActive race; **cross-device entity identity switched from local Room Long id to the entity's existing unique `uuid` column** (new `uuidForId`/`idForUuid`/`applySharedUpsert`/`applySharedDelete`/`applySharedArchive` repository methods, bypass the private Dropbox/GDrive `operationDao` journal write).
  - `80360ba3` — fix round 3: Transaction's `accountId`/`categoryId`/`toAccountId` FK refs were still raw sender Room ids (not remapped like the entity's own identity) — added `accountUuid`/`categoryUuid`/`toAccountUuid` to the payload, resolved via uuid lookup at apply time, unresolved ref throws (per-op catch skips + advances cursor, so a not-yet-applied referenced row means the op is lost, not retried — accepted as residual risk per reviewer, see below).
  - `7d710d20` — fix round 4: `currencyId` had the SAME non-portability bug (fixed by carrying `currencyCode`, resolved via the ALREADY-EXISTING `CurrencyRepository.findById`/`findByCode`); archived accounts weren't published by `publishLocalData` (only active) while their transactions were, making those transactions unresolvable on the receiver — added `AccountRepository.listAllIncludingArchived()`.
  - `1d13e251` — Tester: unit (`SharedSyncCoordinatorImplTest`, `SharedEntityCodecTest`), dao (`SharedRepositoryMethodsTest`), compose-ui (`CloudSyncViewModelTest`, `CloudSyncContentTest`, `CloudSyncScreenContentTest`), instrumented-compose-ui (`CloudSyncSharedCardUiTest`) — committed by orchestrator per project convention (tester has no git access).
  - `6976353a`, `2cb1a1fb`, `6604e46e` — mechanical compile-fix rounds after Runner's first 2 (contract-limit) runs both failed: ~14 stale Fakes across `feature/{dashboard,dictionaries,transaction,transactionslist,onboarding,settings}` and `app/` missing new `AccountRepository.listAllIncludingArchived()` / `{Account,Category,Transaction}Repository.uuidForId()` overrides (mechanical, same pattern each); a real pre-existing Hilt/Kotlin bug surfaced for the first time — `core/network/.../shared/InviteTokenFactory.kt`'s `@Inject constructor(random: SecureRandom = SecureRandom())` had a default value, which Kotlin/Hilt treats as two `@Inject` constructors (fixed: default removed, `SecureRandom` now provided via a new `@Provides` in a Hilt module — 2 test call sites updated to pass it explicitly); `feature/cloudsync/build.gradle.kts` was missing Robolectric/Compose-UI-testing test dependencies the Tester's `CloudSyncScreenContentTest.kt` needs (added, mirroring `feature/dashboard/build.gradle.kts`).

- **Current blocker — Runner FAILING with 9 real test failures (not compile errors)**, found after the above rounds got the build compiling clean. Runner is only allowed 2 attempts per the pipeline contract; both are now spent, plus several extra compile-fix-only rounds already used with explicit user approval each time. **This is where the next session must resume — do NOT re-run Developer for more "obvious mechanical" fixes without first diagnosing #2 below.**
  1. **Stale test-expectation updates (easy, do first):**
     - `core/sync/src/test/.../SyncTargetTest.kt:9` — `has exactly Dropbox and GoogleDrive entries` asserts `listOf(Dropbox, GoogleDrive)`; actual is now `[Dropbox, GoogleDrive, Shared]`. Update the assertion to include `Shared`.
     - `core/sync/src/test/.../FactoryResetGatewayDetachTest.kt:13` — `detached cloud state has no active binding` throws `IllegalStateException: Check failed.` — likely a hardcoded `when`/`require` over `CloudProvider`/`SyncTarget` that doesn't yet account for the `Shared` case, or a stale fixture. Read the test and the production code path it exercises (`FactoryResetGatewayImpl` in `core/sync`) to find the exact mismatch.
  2. **`feature/cloudsync/src/test/.../CloudSyncScreenContentTest.kt` — 7 of 17 tests fail, ALL NEEDING DIAGNOSIS (root cause not yet determined — could be a real `CloudSyncScreen` wiring bug, or the new test's node-matching being wrong):**
     - `Shared card shows sync-now and leave buttons when workspace is active` — `AssertionError: The component is not displayed!` (line 86)
     - `Shared card shows sign-in button when user is not signed in` — same assertion failure (line 42)
     - `Shared card shows setup button when signed in but no active workspace` — same assertion failure
     - `leave button click emits SharedLeaveClicked` — event list empty, expected `[SharedLeaveClicked]` (line 123)
     - `setup dialog import-choice rows emit SharedImportChoiceChanged` — event list empty, expected `[SharedImportChoiceChanged(importLocalData=true)]` (line 158)
     - (2 more failures not yet individually inspected — re-run the Runner or read the full XML at `feature/cloudsync/build/test-results/testDebugUnitTest/TEST-com.kshavrin.mymoney.feature.cloudsync.CloudSyncScreenContentTest.xml` for the complete list)
     - Notably, 10 of 17 tests in the SAME file DO pass (conflict dialog rendering, Dropbox/Drive switch-button visibility, confirm-leave dialog, author-attribution-scoping check) — so the Compose test harness itself works; the failures cluster specifically around the "Shared card" base states (signed-out / signed-in-no-workspace / active-workspace) and the setup-dialog import-choice interaction. First hypothesis to check: does the test's `testTag`/text lookup match what `CloudSyncScreen.kt`'s actual Shared-card composable emits for these specific `CloudSyncState`/`TargetCardState`/Shared-specific state shapes, or is the card's visibility condition in the production composable actually wrong for these states?

- **Once the 9 failures are fixed**, re-run `bash "${CLAUDE_PLUGIN_ROOT}/scripts/mp-runner-android.sh" false` (a fresh, non-contract-limited invocation since this is effectively restarting the Runner step). If it passes: risk route already flagged `independent_critic=true` (last risk-route run: risk=high dropping toward standard across rounds; re-run `mp-risk-route.sh --task feature --spec <this file> --visual --changed <final file list>` once more with the FINAL changed-file set including test files, to get the authoritative final route) — run ONE independent-critic semantic-review pass (fresh evidence packet, no prior conclusions) before Verifier per `contract-risk-routing.md`, since the route required it. Then Step 4.5 Verifier (full, always for features), print the manual checklist, and push per this project's auto-push override (CLAUDE.md "/mp auto-push policy") since a clean Verifier pass authorizes pushing without an extra y/N gate.
- Device used for the instrumented test type: Pixel_5/API34 at `emulator-5554` (re-verify live via `adb devices -l` + `getprop ro.boot.qemu.avd_name`/`ro.build.version.sdk`/`sys.boot_completed` — the serial has drifted before, per cross-session memory `device-topology-and-single-test-run.md`). `CloudSyncSharedCardUiTest` (instrumented) has not yet been run on-device — do that as part of the Runner/verification pass once the JVM-side failures are fixed.
- Deferred hardening (flagged during semantic review, non-blocking, candidates for SPEC 04 "realtime-hardening-e2e"): (a) a currently-dead-but-harmless `currencyId`/`accountId`/`categoryId`/`toAccountId` numeric field is still emitted alongside the uuid/code fields in the payload "for debugging" — cosmetic only, decode ignores it; (b) distinguishing a "you were removed" RPC error to flip `isMembershipActive` to false still needs the real Supabase transport (currently only self-initiated leave clears it); (c) whether `pullAndApply`'s per-operation skip-on-unresolved-ref (cursor still advances, so a permanently-unresolvable FK ref, e.g. arriving out of causal order across concurrent devices, is lost rather than retried) is an acceptable residual risk depends on whether the Shared journal's `server_sequence` assignment actually guarantees causal ordering across devices — not verifiable from client code alone; (d) soft-deleted transactions are excluded from `publishLocalData` (join-with-import), so a transaction deleted locally just before joining does not propagate its deletion to the workspace — unclear if this is in scope for SPEC 03's acceptance or an open question.
- The `feature/settings/.../ImportWizardViewModel.kt` `NoOpCategoryRepository`/`FakeCategoryRepository` touches across this SPEC are all mechanical interface-completeness updates (test/preview stubs), not behavior changes.

## Resume status — 2026-07-29 (supersedes the stale red-Runner section above)

- `c4eb0cd` fixed the nine Runner failures: stale Shared enum expectations, scroll-aware Shared
  Compose assertions, and whole-row import selection.
- Semantic review then found and fixes landed for post-adoption publication/lifecycle races
  (`94951044`), durable isolated pending Shared operations plus Room 8→9 migration
  (`7c49472a`), recursively canonical JSON payload comparison (`80f2923c`) with focused tests
  (`c73051ed`), and periodic work cancellation during Shared detach (`5bf10b1e`).
- Final verification evidence: deterministic reviewer pass; JVM Runner **1860 passed / 0 failed**
  with detekt/lint OK; on-device `CloudSyncSharedCardUiTest` on Pixel_5/API34 **3 passed / 0
  failed**.
- **Blocking acceptance gap:** `CloudSyncScreen` deliberately maps
  `LaunchSharedGoogleSignIn` to `SharedSignInFailed`; `local.properties` has no Google OAuth
  server client ID. Implementing real Credential Manager ID-token acquisition and binding it to
  Supabase Auth requires external OAuth configuration/authorization. The SPEC must stay active,
  must not be pushed, and must not move to `done/` until this is implemented and verified.
