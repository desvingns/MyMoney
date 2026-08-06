# Implementation progress

> Read this compact head first in a new session. It points to the active phase and the latest MP work without loading the full historical log.
> Historical session entries live verbatim under `docs/implementation_plan/log/YYYY-MM.md`; open an archive only when investigating that period.
> Update this head at the end of every session, then move older entries to the matching archive when the head grows past the last three entries.

---

## Current state

- **2026-08-06 (Codex MP close-out, shared-backend-sync):** Closed SPEC 04 and the standalone
  `join_workspace` pgcrypto bugfix after user-confirmed multi-user/device E2E and Pixel 8 invite
  join verification. Commits: `ea914537`, `716216fd`, `a9c9a877`. Scoped JVM checks: 370 passed /
  0 failed / 0 skipped. All four shared-backend-sync child SPECs plus the epic overview moved to
  `.claude/specs/done/`; the experimental gate remains off for public/release defaults.

- **2026-08-05 (Codex MP `--bugfix`, dashboard inline transaction records):** Reproduced the
  dashboard showing only one aggregated `23 RSD` tile while August data existed on Pixel 5/API34
  and Pixel 9/API37. Restored lazy inline category-record expansion with row navigation, safe
  period/account stale-result invalidation, mixed-currency `AllAccounts.ConvertTo` grouping, and
  48dp accessibility touch targets. Commits: `0e7a7330`, `f4351442`, `3133773a`, `170586c9`.
  Final MP runner: 1954 passed / 0 failed / 0 skipped, detekt/lint green; focused connected
  dashboard regression: 1 passed on Pixel 5/API34. Final APK launch on Pixel 5 is green after
  forced KSP/Hilt regeneration; the Pixel 9 AVD exited before boot during the repeat smoke, so
  its final APK launch was not re-verified in this session.

- **2026-08-05 (Codex MP `--bugfix`, Shared Supabase sync):** Reproduced the stuck durable
  outbox on Pixel 5/API34 and Pixel 9/API37: manual `Sync now` previously left the UI retrying
  and did not advance completion. Fixed the RPC boundary so `push_operation` and
  `resolve_conflict` decode either a single object or exactly one object in a JSON array, and
  model the deployed `author_id` field as nullable (pull responses intentionally omit it).
  Added MockWebServer regressions for both successful shapes and empty/ambiguous arrays. Network
  test, reviewer script, final `assembleDebug -Psync.forceEnabled=true`, and device smoke on
  Pixel 5/Pixel 9 are green; both devices show `Realtime connected` and updated `Last sync`.
  The full `mp-runner-android.sh` exceeded its 244-second tool limit without returning its JSON;
  the runner child processes were stopped after verification. OnePlus 11 was not attached to
  host ADB, so its physical three-device path remains unverified.

- **2026-08-05 (Codex Supabase, shared-backend-sync SPEC 04):** Applied the shared Realtime
  migrations to project `shwzjlkhlpgbmzgnxhxi`: `shared_realtime_security` and
  `private_workspace_realtime`. Post-check confirmed an empty `supabase_realtime` publication,
  RLS on `operations`/`conflicts`/`realtime.messages`, no direct client writes, and no `author_id`
  column access. A follow-up grant hardening migration removed explicit `anon`/`authenticated`
  EXECUTE defaults; the authorization helper now lives in the unexposed `private` schema and
  binds its user argument to `auth.uid()`. The private Realtime read policy was corrected for
  Supabase's transient authorization row (`20260805130000_fix_private_realtime_read_authorization`);
  remote migration history now has five entries. Pixel 5/API34 retry shows `Realtime connected`
  and remains connected for 28 seconds; runner is 1875 passed / 0 failed with detekt/lint green.
  The pre-existing duplicate `0003` filenames remain a CLI ordering hazard and need a separate
  migration-history cleanup decision.

- **2026-07-29 (Codex MP `--feature --next`, shared-backend-sync epic, SPEC 03 — BLOCKED,
  not closed):** Resumed and fixed the 9 red tests (`c4eb0cd`), Shared coordinator lifecycle/
  publish races (`94951044`), durable Shared outbox + Room 8→9 migration (`7c49472a`), JSON
  canonicalization (`80f2923c` + `c73051ed` tests), and scheduler cancellation on detach
  (`5bf10b1e`). Latest deterministic reviewer is clean; Runner is **1860 passed / 0 failed**
  with detekt/lint OK; `CloudSyncSharedCardUiTest` on Pixel_5/API34 is **3 passed / 0 failed**.
  SPEC remains in `active/`, unpushed: independent critic confirms its Google sign-in acceptance
  is non-functional because `LaunchSharedGoogleSignIn` deliberately emits failure and
  `local.properties` lacks a Google OAuth server client ID. This requires a real Credential
  Manager/Supabase Auth integration and external OAuth configuration; do not close/push/move the
  SPEC until that is authorized and verified. Keep documented SPEC 04 deferrals separate.

> Last three session entries are repeated here for fast startup. Full history is archived below.

- **2026-07-29 (Claude MP `--feature --next`, shared-backend-sync epic, SPEC 03 — IN PROGRESS,
  not closed):** SPEC `shared-backend-sync-03-android-shared-mode` (Android Shared sync mode:
  Google sign-in, join/create workspace, import/no-import choice, internal backups,
  conflict-resolution UI, leave/removal) is still in `.claude/specs/active/`, NOT pushed. Five
  semantic-review fix rounds landed real correctness fixes (leave/join ordering + races,
  cross-device entity identity moved from local Room Long id to the entity's own `uuid` column,
  Transaction FK-ref uuid remapping, currency-code portability, archived-account publishing,
  private Dropbox/GDrive-journal leak prevention) — see the SPEC file's "Implementation links"
  for the full commit-by-commit history. Runner is currently RED with 9 real test failures (2
  easy stale-test updates in `SyncTargetTest`/`FactoryResetGatewayDetachTest`; 7 undiagnosed
  failures in the new `CloudSyncScreenContentTest`, root cause not yet known). Stopped
  deliberately after many fix rounds — full resume plan is in the SPEC file and
  `.ai/handoff.md`. Do not restart from scratch; read the SPEC file's notes first.

- **2026-07-28 (Claude MP `--feature --next`, shared-backend-sync epic, SPEC 02):** Completed
  SPEC `shared-backend-sync-02-operation-api-and-conflicts` (commits `82f8d6f2` feat,
  `f132bdc6`+`8b3e0a78` fixes, `c81a5f11` tests; pushed to `main`). Added SQL migration
  `supabase/migrations/0002_shared_operations.sql` (append-only `operations` + `conflicts`
  tables, RLS, 4 SECURITY DEFINER RPCs: `push_operation`/`pull_operations`/
  `list_pending_conflicts`/`resolve_conflict`) plus `:core:domain` (SharedOperation,
  SharedConflict, SharedJournalRepository) and `:core:network` (DTOs, SharedJournalRpc
  transport seam, SupabaseSharedJournalApi) Kotlin contracts. Semantic review caught 2
  blockers (non-atomic push idempotency racing under concurrent retries; `author_id` leaking
  through `pull_operations`' `select *`, violating the "attribution only in conflict UI"
  constraint) — both fixed (`INSERT ... ON CONFLICT DO NOTHING RETURNING`; explicit
  column-list SELECT + `authorId` moved to the conflict-only DTOs) and re-verified clean.
  Independent critic passed (risk downgraded high→standard) with 2 non-blocking hardening
  findings logged in the SPEC file's "Deferred hardening" section for SPEC 04 to pick up:
  default Supabase table grants let a client bypass the RPC's column allowlist via direct
  PostgREST SELECT (RLS restricts rows, not columns); a non-atomic `base_sequence` MAX-scan
  in `resolve_conflict` (metadata-only staleness, cursor ordering unaffected). Gates: reviewer
  0 violations, runner 1771 JVM tests + detekt/lint green, full verifier pass. SPEC moved to
  `done/`; epic not yet complete (SPECs 03/04 remain in backlog), so feedback question and
  Telegram offer were both skipped per epic-scoped timing.

## Historical session log archives

Read archives on demand only; do not bulk-load them during normal MP startup.

- `log/2026-07.md` - July 2026 session entries.
- `log/2026-06.md` - June 2026 session entries.
- `log/2026-05.md` - May 2026 session entries.
- `log/legacy.md` - undated plan-completion and legacy current-state entries.

---

## Phase completion

| #  | Phase                                                 | Status        | Session date  | Outcome / link to "Notes for next session" |
|----|-------------------------------------------------------|---------------|---------------|--------------------------------------------|
| 00 | Overview / orientation                                | done          | 2026-05-18    | Plan + checklist files created. Start with PHASE_01. |
| 01 | Multi-module Gradle scaffolding                       | done          | 2026-05-19    | 14/14 tasks ticked. 4 of 5 Done-criteria deferred-due-loopback; one ✓ verified (`com\example\mymoney` absent in `:app`). See `phases/PHASE_01_scaffolding.md` → "Notes for next session" (lines 69–114). |
| 02 | DI + App shell + Sentry skeleton                      | done          | 2026-05-19    | 13/13 tasks ticked. Hilt graph wired across 14 modules (pure-JVM `:core:common` + `:core:domain` use `hilt-core` + KSP; 12 Android-library modules use `hilt-android` + Hilt plugin + KSP). MyMoneyApp `@HiltAndroidApp` + `MainActivity @AndroidEntryPoint`. Sentry init guarded by `BuildConfig.SENTRY_DSN.isNotBlank()` — blank-by-default, populate via `gradle.properties sentry.dsn=...` when OQ-1 resolves. `:core:common` DI scaffolding (Dispatcher qualifiers, DispatchersModule, AppResult, SyncException, SentryExt). Debug-only Sentry test IconButton in MainActivity (BuildConfig.DEBUG-gated, will be removed in PHASE_07 when nav root arrives). See `phases/PHASE_02_di_and_app_shell.md` → "Notes for next session". |
| 03 | Design system (`:core:ui` + designsystem skeleton)    | done          | 2026-05-19    | 12/12 tasks ticked. :core:ui Compose-wired with theme tokens (Color/Typography/Shape/Spacing) — exact APK ARGB fidelity for primary mint + 15 CategoryColors; M3 modernisation for corners + spacing. MyMoneyTheme wraps MaterialTheme with status-bar tint to primary + dynamicColor=false (no Material You). 2 Preview meta-annotations (ThemePreviews Light+Dark, PreviewLocales EN+RU) + PreviewSamplePalette visual QA. :core:designsystem upgraded to Compose Android-library + 7 stub component files (donut/keypad/amountinput/pill/confetti). MainActivity rewired bare MaterialTheme → MyMoneyTheme. See `phases/PHASE_03_design_system.md` → "Notes for next session". |
| 04 | Database layer (`:core:database`)                     | done          | 2026-05-20    | 12/12 tasks ticked across 2 commits (SPEC A 8aba33d + SPEC B 9954215). Full Room scaffolding: 9 entities verbatim from TDD §7.2 (CurrencyEntity / CurrencyRateEntity FK RESTRICT / AccountEntity / CategoryEntity / TransactionEntity 4 FKs + 7 indices / BudgetEntity / RecurringTemplateEntity / SyncLogEntity / SearchHistoryEntity). 9 DAOs verbatim from TDD §7.4 — AccountDao as `abstract class` (Transaction setDefault) + TransactionDao with back-tick `\`transaction\`` escaping + SearchHistoryDao with `\`query\`` escaping. MoneyDatabase @Database(version=1, exportSchema=true) + 9 abstract accessors. MoneyTypeConverters (BigDecimal/LocalDate/Instant). DatabaseModule Hilt @InstallIn(SingletonComponent) "monefy.db" + fallbackToDestructiveMigrationFrom(99). CategorySummaryRow projection. RoundTripTest 9 androidTest tests. MigrationTest placeholder. Schema JSON + connected tests deferred-by-loopback — PHASE_15 release prep must reconcile. See `phases/PHASE_04_database_layer.md` → "Notes for next session". |
| 05 | DataStore + secure storage (`:core:datastore`)        | done          | 2026-05-20    | 11/11 tasks ticked (commit 15ec5ac). AppSettings DataStore Preferences with 15 fields verbatim from TDD §7.3 + monotonic firstPositiveSeen validation in update() (false→true OK, true→false throws IllegalStateException) + null-clear handling for optional Long? timestamps. SecureStorage EncryptedSharedPreferences (MasterKey AES256_GCM + AES256_SIV key encryption + AES256_GCM value encryption) — Dropbox token / GDrive email / PIN hash typed accessors. DataStoreModule provides @Singleton DataStore via PreferenceDataStoreFactory.create with @IoDispatcher (FIRST cross-module Hilt qualifier usage from :core:common). 4 unit tests (defaults / round-trip 15 fields / monotonic enforcement / null-clearing) + 5 androidTests (per-secret roundtrip + clearAll + null-write removes). File names match TDD §8.3: `app_settings.preferences_pb` + `com.kshavrin.mymoney_secure.xml`. See `phases/PHASE_05_datastore_and_secure_storage.md` → "Notes for next session". |
| 06 | Domain layer + seeding (`:core:domain`, `:core:common`) | done       | 2026-05-20    | 17/17 tasks ticked across 3 work commits (99dbea3 SPEC 1 + 94b54d9 SPEC 2 + b771da6 SPEC 3) + close-out. :core:domain pure JVM with 14 model files + 9 repository interfaces + 4 UseCases (BalanceCalculator/TransferExecutor with TransferResult AS-6+AS-7/BudgetEvaluator/RecurringScheduler) + InitialDataSeeder (20 currencies + 1 Cash + 17 categories). :core:database with 9 RepositoryImpls + Mappers + RepositoryBindingsModule. :core:common with MoneyFormatter + ApplicationScope. 18 unit tests + 5 Fake repos. Verifier hilt_graph=ok. Decisions: idempotency via currencyRepo.observeAll().first().isEmpty() (no :core:datastore dep); RepositoryImpls in :core:database not :core:domain; TransactionRepository.findByPeriod placeholder for PHASE_11. See `phases/PHASE_06_domain_layer.md` → "Notes for next session". |
| 07 | Splash + onboarding (S00, S11) + nav root             | done          | 2026-05-20    | 12/12 tasks ticked (SPEC A commit 6519467 + SPEC B 7b77dab + fix 42f4887). Navigation: MyMoneyNavHost with 12 Destinations + DecisionRouterViewModel (Pending/Splash/Dashboard) routing on AppSettings.onboardingCompletedAt. SplashScreen (with public SplashContent) → SplashViewModel runs InitialDataSeeder → routes to Onboarding. OnboardingScreen (public OnboardingContent + 4-slide HorizontalPager + PagerDotsIndicator + Next/Get Started button) → OnboardingViewModel persists onboardingCompletedAt + routes to Dashboard. popUpTo(inclusive=true) on both transitions (noHistory per TDD §3.3). Theme.MyMoney.Splash (postSplashScreenTheme=Theme.MyMoney, windowSplashScreenBackground=#7AC794). 3 App Shortcuts (add_expense/add_income/transfer). monefy:// deep-link + DRIVE_OPEN intent-filters. 4 placeholder hero drawables (design pass deferred to PHASE_15). 10 EN strings (RU deferred to PHASE_15). Reviewer initially flagged 2 violations (DecisionRouter direct Repository inject + SplashScreen missing Content extraction) — both fixed in commit 42f4887 + re-reviewed ✓. See `phases/PHASE_07_navigation_and_onboarding.md` → "Notes for next session". |
| 08 | Dashboard + donut chart (S01/S05 + S02/S04)            | done         | 2026-05-20    | 13/13 tasks ticked across SPEC 1 (fa50c68 — MonefyDonutChart Canvas + DonutGeometry + BalancePill + Confetti + 5 tests) + SPEC 2 (8995437 — DashboardScreen/VM/State/Action + PeriodStrip + TwoFabLayout + 2 Drawers + MyMoneyNavHost integration) + close-out. AS-1, AS-2, AS-3, AS-10, AS-12, AS-14 all wired. Verifier nav_wired=ok + hilt_graph=ok + en_strings=ok. See `phases/PHASE_08_dashboard_and_donut.md` → "Notes for next session". |
| 09 | Dictionaries CRUD (S21–S26)                            | done          | 2026-05-20    | 9/9 tasks ticked across baseline 81d1888 (28-file scaffolding: 9 screens/VMs + 4 common + AS-13 wiring + 60 EN strings + 6 Destinations routes + 3 DashboardActions) + 16627b0 (NavHost 6 composable wiring task 7) + 5fe3008 (7 nav unit tests) + 8b3077e (Categories drag-reorder task 3 — hand-rolled detectDragGesturesAfterLongPress, section-isolated) + 52cc17a (10 reorder VM tests + module-local FakeCategoryRepository) + b0c17f2 (Currency code-lock task 5 — new AccountRepository.countByCurrency, archived accounts excluded) + 8bdb4c1 (9 lock VM tests + module-local fakes) + close-out commit. Verifier final sweep `pass=true` with nav_wired=ok + hilt_graph=ok + room_schema=n/a + en_strings=ok. Decisions: S25 +FAB allowed (Q-D6 custom currency), reuse upsertAll for reorder, module-local fakes pending java-test-fixtures cleanup, pre-existing CurrencyEditScreen back-arrow→SaveClicked quirk flagged for PHASE_10 bugfix. See `phases/PHASE_09_dictionaries_crud.md` → "Notes for next session". |
| 10 | Transaction forms (S03, S06, S07, S09, S27)            | done         | 2026-05-22    | 12/12 tasks ticked. AS-6 + AS-7 found already-implemented in `cd41194` ("Mb revert?") & verified vs TDD §4.8. Missing nav wired in `e77770a` (5 transaction composables in MyMoneyNavHost + AS-4 `category_edit` kind/fromPicker via navController overload + AS-6 `currency_rate` fromId/toId + MainActivity shortcut routing §3.4); swap-toggle route bug fixed `45c77a9`; `DashboardViewModel` reactive refresh `89a33ef` (TDD §4.6 #6). Reviewer + Verifier pass. 5 route-contract tests in DestinationsTest. Build/e2e Done-criteria deferred — gradlew now runs but blocked at config by oss-licenses plugin marker (NOT loopback). See `phases/PHASE_10_transaction_forms.md` → "Notes for next session". |
| 11 | List + search + detail (S08, S12, S13)                 | done         | 2026-05-24    | 9/9 ticked across 4 /cmp --phase slices (S12 list `fc7fd0d`, form relocation `45e0d12`, S13 detail `5fae84f`, S08 search `acf62a7`). Done-criteria green (63 unit tests + both assembleDebug). Compose-UI/instrumentation/device QA + RU deferred to PHASE_15. See `phases/PHASE_11_*.md` → "Notes for next session". |
| 12 | Settings hierarchy (S14, S15, S18, S19, S20)            | done         | 2026-05-24    | All tasks ticked across 5 `/cmp --phase` slices (S15 f065666, S19 815d4b7, S20 26dc07a, S18 003ec46, S14 395adf9; tests 0c8c601/105e1a9/08f2969). Done-criteria green: 112 settings unit tests + :core:domain + :app tests + both assembleDebug. Device QA, CSV/factory-reset, RU → PHASE_15. See `phases/PHASE_12_*.md` → "Notes for next session". |
| 13 | Cloud sync + Sentry + Remote Config (S17)              | done         | 2026-05-24    | All tasks ticked across 7 `/cmp --phase` passes. Full cloud-sync skeleton, DevOps-gated OFF (OQ-1/2/3/5/9 still open): `:core:sync` (Dropbox + GDrive backends, SnapshotSyncRepository push/pull/conflict, @HiltWorker SyncWorker + 6h SyncScheduler, gated Firebase RemoteConfig, Sentry breadcrumbs/capture at §9.5 levels) + `:core:network` HttpModule + S17 CloudSyncScreen/VM/ConflictDialog wired into nav + S14. Done-criteria green: `:app:assembleDebug` + sync/cloudsync/settings unit tests + `:core:domain:test`. Commits `2920b85`/`90872c5`/`d1002a1`/`96062f6`/`639c0d7`/`2467058`/`5114e3d` (+tests). See `phases/PHASE_13_*.md` → "Notes for next session". |
| 14 | Biometric + WorkManager (S16, recurring, budget)       | done         | 2026-05-26    | 13/13 tasks ticked across 4 `/cmp --phase` SPEC pipelines (+2b nav wiring). S16 biometric+PIN, AS-5 lock overlay (not a nav dest), 3 daily workers (RecurringWorker silent/AS-11), budget-alert use case. 148 unit tests green; `:feature:lockscreen`+`:app` assembleDebug green at `1d9bbd2`. Commits LOCAL. See `phases/PHASE_14_*.md` → "Notes for next session". |
| 15 | Polish + gamification + l10n + tests + release         | done         | 2026-06-01 | 18/18 tasks ticked. Main work complete with 809/809 autotests green, release build green, unsigned minified APK 12.38 MB, Baseline Profile generated, and macrobenchmark infrastructure executed. Remaining manual/release gates intentionally deferred by product decision; see `phases/PHASE_15_polish_l10n_tests_release.md` → "Implementation plan close-out". |

Legend: `not started` → `active` → `in progress` → `done` (use `blocked` if a phase is paused on external work; cite OQ-id).

---

## Decisions log

Append a one-line entry whenever a non-obvious decision is made during a session. Format: `YYYY-MM-DD — <decision>. Cross-ref: <phase or TDD §>.`

- 2026-05-18 — Implementation plan: 15 phases, English, located under `docs/implementation_plan/`. Cross-ref: this file's existence.
- 2026-05-18 — Application namespace finalised as `com.kshavrin.mymoney` (replaces template `com.example.mymoney`). Cross-ref: README §4 conventions, TDD OQ-3.
- 2026-05-18 — All TDD §14.1 resolved decisions (AS-1 … AS-15) are pre-locked. See TDD lines 2727–2750. Do not re-litigate during implementation; cite the AS-id instead.
- 2026-05-20 — PHASE_09: S25 currencies list includes a + FAB for custom-currency creation (deviates from §4.24 line 1132-1142 which implies no create). Justification: Q-D6 allows custom currency entries in scope; users may add cryptocurrencies / loyalty points / regional currencies missing from the 20 seeded set. Revisit only if user vetoes during PHASE_15 polish.
- 2026-05-20 — PHASE_09: AccountRepository.countByCurrency added as a new domain method (filters is_archived=0) used by Currency edit-mode to lock the code field when accounts depend on it. Mirrors the TransactionRepository.countBy* pattern added in PHASE_09 baseline. Cross-ref: §4.25 lines 1143-1152 + PHASE_09 file task 5.
- 2026-05-22 — TDD spec relocated: the authoritative `MyMoney_TDD.md` now lives at `C:\Pet\MyMoney\TDD\MyMoney\MyMoney_TDD.md` (the old `D:\Pet\TDD_creater\…` drive is gone). Repointed across 22 docs/config files (CLAUDE.md, cmp.md, developer-extras.md, all PHASE files, README, 00_overview, SESSION_PROMPT, DOCUMENTATION). It is English prose + EN/RU string tables, 2409 lines (CLAUDE.md previously mis-stated "Russian, 2 850 lines"). Cross-ref: `mymoney-tdd-relocation` memo.
- 2026-05-22 — PHASE_10: AS-6 + AS-7 were already implemented in commit `cd41194` ("Mb revert?" / "Не уверен в этом коммите" — added ~2893 lines, not a revert). Kept the work (verified correct vs TDD §4.8) rather than reverting; only the genuinely-missing :app navigation wiring + a swap-toggle route bug + the dashboard reactive-refresh remained. Cross-ref: PHASE_10 Notes.
- 2026-05-22 — "Loopback blocker" reassessed: gradlew actually runs once `JAVA_HOME` points at the Android Studio JBR; the real build blocker is the root `oss-licenses` plugin marker not resolving via the plugins{} DSL. Static-inspection precedent retained for PHASE_10, but real builds are now one config fix away. Cross-ref: Open questions + `mymoney-windows-loopback-blocker` memo.
- 2026-05-22 — Build unblocked; first green build + APK in project history. Two non-obvious choices: (a) JVM target locked at **17 project-wide** via a single root `subprojects { tasks.withType<KotlinCompile> { compilerOptions.jvmTarget.set(JvmTarget.JVM_17) } }` block rather than editing 16 module files — respects the author's explicit `VERSION_17` Java target and avoids provisioning a JDK 17 toolchain; (b) oss-licenses kept (not dropped) via `pluginManagement.resolutionStrategy.eachPlugin`, since the OSS-licenses screen lands ~PHASE_12/15. Cross-ref: Open questions 2026-05-22.
- 2026-05-24 — PHASE_11 (Decision 1): the shared transaction-amount form (`AmountFieldSection` + `AmountFieldState`/`Event`) and the keypad↔calculator `OperatorMapper` were promoted from `:feature:transaction` down to `:core:designsystem` so the new S13 screen in `:feature:transactionslist` can reuse them without a forbidden `:feature:*→:feature:*` dependency. `AmountFieldSection` was made string-agnostic (defaulted `noteLabel`/`dateContentDescription` params backed by designsystem-owned strings) and `:core:designsystem` gained a `:core:common` dependency (acyclic — common is pure-JVM). Cross-ref: PHASE_11 Notes, commit `45e0d12`.
- 2026-05-24 — PHASE_11: where the PHASE_11 phase file and the TDD disagreed, the TDD won (per CLAUDE.md "the spec is the source of truth"). Search debounce is **200 ms** (TDD §4.9 AC1 + §11.4 US-12), not the phase file's 300 ms; search matches **note + category name** via the existing `searchByNote` (List, cap 200), not the phase file's amount-parsing or `Flow<PagingData>` (those exceed §4.9). Cross-ref: PHASE_11 Notes, commit `acf62a7`.
- 2026-05-24 — PHASE_12 (Decision 3): `BackupRepository` interface placed in `:core:domain` (pure-JVM, so URIs are passed as `String` not `android.net.Uri`) with the impl in `:core:database` (it owns `MoneyDatabase` + the `monefy.db` file + `ContentResolver`); the `:feature:settings` ViewModel injects the domain interface only. This deviates from the phase file's stated `feature/settings/backup/BackupRepository.kt` location — justified by the layer rule (a feature must not close/reopen `MoneyDatabase` or touch data internals) and the PHASE_06 RepositoryImpl-in-`:core:database` precedent. Cross-ref: PHASE_12 file task "S18 Backup", commit `003ec46`.
- 2026-05-24 — PHASE_12: S18 scoped to **.db SAF export/import + OQ-8 keep-newest-3 rotation**; CSV export/import + the destructive factory-reset (TDD §4.17 AC4/AC5) are deferred beyond this phase's checklist. Export uses `OpenDocumentTree` (not the phase file's `CreateDocument`) so OQ-8 can enumerate siblings for rotation; the keep-newest-3 selection is a pure unit-tested `backupsToDelete()`. Cross-ref: PHASE_12 Notes, commit `003ec46`.
- 2026-05-24 — PHASE_12: `MainActivity` migrated `ComponentActivity`→`AppCompatActivity` and `Theme.MyMoney` reparented to `Theme.AppCompat.DayNight.NoActionBar`, so AppCompat per-app locale (`AppCompatDelegate.setApplicationLocales`) actually applies on API 31/32 (the framework `LocaleManager` is API 33+). Compose `setContent` + Hilt + splash all keep working (AppCompatActivity is a ComponentActivity). Cross-ref: PHASE_12 file task "Language selection", commit `815d4b7`.
- 2026-05-26 — PHASE_14 (Decision): biometric dependency pinned to `androidx.biometric:1.1.0` (stable). The phase file named `1.2.0-alpha07`, which does not exist on Google Maven (the 1.2.0 line stops at alpha05). 1.1.0 supplies BiometricManager/BIOMETRIC_STRONG/BiometricPrompt/FragmentActivity — all S16 + overlay needs. Cross-ref: PHASE_14 Notes, commit `60cfb4a`.
- 2026-05-26 — PHASE_14 (Decision, AS-5): the lock OVERLAY is rendered in `MainActivity`'s Box above `MyMoneyNavHost` (gated on `LockController.shouldShowLock`) and is NOT a NavController destination; the S16 *setup* screen IS a normal destination, reusing the pre-existing `Destinations.LOCK_SCREEN` constant and wired from the Settings "App lock" row (previously a DisabledListItem). LockController idle math extracted to a pure `internal fun shouldLockAfterIdle(...)` + `internal var now` clock seam for JVM testability. Cross-ref: PHASE_14 Notes, commits `c2d2c0f`/`a848114`.
- 2026-05-26 — PHASE_14 (Decision, AS-11): RecurringWorker generation logic lives in `GenerateDueRecurringUseCase` (`:core:domain`) so the silence rule is statically verifiable (zero NotificationManager/Toast/DataStore) and JVM-unit-testable; the @HiltWorker stays thin. BackupRotationWorker formalised via a new `BackupRepository.rotateBackups(treeUri)` (SAF/Context stays in `:core:database`); the existing inline keep-3 rotation in `exportDb` was kept. Daily workers scheduled idempotently (`ExistingPeriodicWorkPolicy.KEEP`) from `MyMoneyApp.onCreate` via new `WorkScheduler`. Cross-ref: PHASE_14 Notes, commit `bc9c308`.
- 2026-05-26 — PHASE_14 (Decision): budget-alert wiring = `ObserveBudgetAlertsUseCase` parameterized by `accountId`+`period` (does NOT inject AppSettings — preserves the locked `:core:domain ⊥ :core:datastore` rule from PHASE_06); reuses `BalanceCalculator` + `BudgetEvaluator`, emits `DomainEvent.BudgetAlert(budgetId, categoryId, over)`. Added `TransactionRepository.observeAll()` as the reactive trigger (schema-free DAO @Query, Room version unchanged). Dashboard chip consumption deferred to PHASE_15. Cross-ref: PHASE_14 Notes, commit `a8dd486`.
- 2026-05-26 — PHASE_14 (batching): the 13 phase tasks were implemented as **4 coherent `/cmp --phase` SPECs** (S16 setup; lock overlay + nav-wiring 2b; 3 workers; budget alerts) rather than one pipeline per checklist line — related deliverables in the same module are more correct and testable together. Each SPEC ran the full Developer→Reviewer→Tester→Runner→Verifier chain green. Test files committed by the orchestrator (tester/runner agents have no git access), matching the PHASE_11–13 separate-test-commit pattern.
- 2026-05-26 — PHASE_15: release depends on `sentry-android-core` in `:app` and base `sentry` in `:core:sync`, not umbrella `sentry-android`; native crash/replay integrations are not required by the TDD and raised the R8 APK over the 15 MB budget (16.87 MB → 12.35 MB). Cross-ref: TDD §8.5, PHASE_15 release notes.
- 2026-05-26 — PHASE_15: CSV v1 deliberately rejects transfer rows because the locked flat export header has no destination account/amount/rate columns; silent lossy import would corrupt AS-7 transfer semantics. Cross-ref: TDD §4.17, PHASE_15 notes.
- 2026-06-01 — implementation_plan close-out: remaining PHASE_15 manual/release gates are skipped for plan completion and deferred to release/v1.1 readiness by product decision. Cross-ref: PHASE_15 close-out notes.
- 2026-06-02 — Visual CMP work now has a hard `Pixel_5_API_34` pre-flight gate: explicitly visual `$cmp --phase`/`--feature`/`--bugfix`/`--device` tasks must stop before agent work if the required device is absent, because correct development cannot proceed without visual testing. Cross-ref: AGENTS.md "Visual-change device gate" and `.claude/commands/cmp.md`.
- 2026-06-03 — Codex pipeline hard-switch target is MP Dev (`$mp`) via thin wrappers over Claude `mp-dev` 1.7.0; project-specific skill/agent improvements now go first to `.claude/mp/extras/*` so Claude and Codex stay synchronized. `$cmp` remains legacy fallback until a fresh-runtime `mp-architect` smoke confirms Codex has loaded the new `.codex/agents/mp-*.toml` roles. Cross-ref: AGENTS.md "Native Codex Pipeline".

---

## Deferred work — DevOps prerequisites (TDD §14.2)

These need real external accounts. They block PHASE_13 (cloud sync) but do NOT block PHASE_01 … PHASE_12 or PHASE_14 / PHASE_15. Track here when picked up.

- [ ] **OQ-1** — Sentry: project created for the re-impl; fresh DSN collected. **DO NOT reuse Monefy's DSN.** Deferred to release/v1.1; blank local DSN remains supported.
- [ ] **OQ-2** — Dropbox: app registered (Scoped access — App folder); app key + debug/release SHA-1 fingerprints collected. Deferred to release/v1.1; cloud sync remains gated off.
- [ ] **OQ-3** — Google Cloud: project + Drive API + OAuth consent screen (`drive.appdata` scope), SHA-1 fingerprints, package name `com.kshavrin.mymoney`. Deferred to release/v1.1; Google Drive sync remains gated off.
- [x] **OQ-5** — Firebase Remote Config: **RESOLVED 2026-07-17 (ADR-0008)** — kept in scope, deferred to v1.1. Gated-OFF scaffolding in `:core:sync` (PHASE_13) stays as-is; v1.1 scope = feature flags + initial `min_supported_version_code` = `1`. No SDK wiring change now; defaults keep remote-gated features off.
- [ ] **OQ-9** — CI: secret-injection path for `google-services.json` decided. Workflow scaffold exists in `.github/workflows/ci.yml`; secret policy and hosted green run remain open. Deferred to release/v1.1.
- [x] **OQ-10** — Crash reporting: **RESOLVED 2026-07-17 (ADR-0008)** — committed to **Sentry-only** (errors-only, free Developer plan, 5k errors/mo). Firebase Crashlytics (Spark, unlimited free) documented as the fallback if that cap is ever consistently saturated (requires a new ADR before migration). Current implementation already uses Sentry-only APIs (ADR-0004); no wiring change. OQ-1 (fresh DSN) remains the only open Sentry prerequisite.

OQ-4 (Privacy Policy) was resolved as AS-15 → bundled HTML. OQ-6 (live FX provider) deferred to v1.1, not blocking. OQ-7 (auto-sync interval) and OQ-8 (backup rotation N) resolved in §14.1.

---

## Open questions (non-DevOps)

Append any clarification needed mid-implementation. Format: `YYYY-MM-DD — <question>. Affects: <phase>. Status: <open|answered (date)>.`

- 2026-05-19 — Windows host blocks AF_UNIX loopback for the JDK NIO selector (`sun.nio.ch.PipeImpl$Initializer$LoopbackConnector` → `SocketException: Invalid argument: connect`). Every `gradlew.bat` invocation fails with `java.io.IOException: Unable to establish loopback connection`. Reproduces across Gradle 9.4.1 / 8.11.1, JDK 17 MS-hotspot / JBR 21, daemon + no-daemon, default + relocated `GRADLE_USER_HOME=.gradle-local`. Root cause likely corporate AV / endpoint protection. Affects: PHASE_01 sanity-build tasks (worked around by "verified-by-inspection" precedent), PHASE_02+ will need actual `gradlew` runs for Hilt/Room codegen verification. Status: **SUPERSEDED 2026-05-22 (see next entry)**. Cross-ref: `mymoney-windows-loopback-blocker.md` in cross-session memory.
- 2026-05-22 — Loopback NOT reproducing. With `JAVA_HOME` = Android Studio JBR (`/c/Program Files/Android/Android Studio/jbr`, OpenJDK 21.0.10), `./gradlew :feature:transaction:help --no-daemon` runs and reaches plugin resolution (no loopback error). The real blocker: root `build.gradle.kts` line 11 declares `alias(libs.plugins.gms.oss.licenses) apply false` → plugin id `com.google.android.gms.oss-licenses-plugin` v0.10.6, whose plugin-marker artifact does not resolve from Google/MavenCentral/PluginPortal. Fix options: (a) `pluginManagement.resolutionStrategy.eachPlugin { useModule("com.google.android.gms:oss-licenses-plugin:<v>") }` in settings.gradle.kts, or (b) move it to a legacy buildscript classpath, or (c) drop it until the OSS-licenses screen is built (it's `apply false` everywhere; used ~PHASE_12/15). Affects: ALL gradle builds (config-time failure). Status: **RESOLVED 2026-05-22 (see next entry)**.
- 2026-05-22 — Real Gradle builds now work end-to-end — first green build in project history. Five fixes this session: **(1)** oss-licenses plugin marker — added `pluginManagement.resolutionStrategy.eachPlugin { useModule("com.google.android.gms:oss-licenses-plugin:${requested.version}") }` to `settings.gradle.kts`. **(2)** JVM-target mismatch (every Android module pins Java to 17 but Kotlin defaulted to the running JBR 21) — added a root-`build.gradle.kts` `subprojects { tasks.withType<KotlinCompile>().configureEach { compilerOptions.jvmTarget.set(JvmTarget.JVM_17) } }` block (single source of truth; pure-JVM modules already self-align via `jvmToolchain(17)`). **(3)** `:core:designsystem` `MonefyDonutChart.kt` imported `drawText` from the wrong package (`androidx.compose.ui.graphics.drawscope` → `androidx.compose.ui.text`). **(4)** `:core:database` `RecurringTemplateRepositoryImpl.kt` — cross-module smart-cast on nullable `template.endsAt` impossible; captured to a local `val`. **(5)** `:core:common` `MoneyFormatter` emitted a trailing `.` for zero-decimal currencies (pattern `#,##0.` when `decimalDigits==0`) → conditional pattern `#,##0`; caught by the real failing test `MoneyFormatterTest.formats_zero_decimal_currency`. Verified: `./gradlew :app:assembleDebug` → green `app/build/outputs/apk/debug/app-debug.apk`; `./gradlew testDebugUnitTest :core:domain:test :core:common:test` → all modules green, 0 failures (feature:transaction alone = 34 tests). **Setup gotcha:** Git Bash does not export `JAVA_HOME`; run `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"; export PATH="$JAVA_HOME/bin:$PATH"` before any gradle command. Affects: project-wide build/test verification (now UNBLOCKED — phases can be verified by real builds, not static inspection). Status: answered (2026-05-22).

---

## Session log

Long-form historical session entries moved verbatim to monthly archives:

- `log/2026-05.md`
- `log/2026-06.md`
- `log/2026-07.md`
- `log/legacy.md`

For new MP closeout, add a short bullet to `## Current state` above. When it falls out of the last-three window, move it to the matching archive instead of growing this file again.
