# Implementation progress

> Read this compact head first in a new session. It points to the active phase and the latest MP work without loading the full historical log.
> Historical session entries live verbatim under `docs/implementation_plan/log/YYYY-MM.md`; open an archive only when investigating that period.
> Update this head at the end of every session, then move older entries to the matching archive when the head grows past the last three entries.

---

## Current state

- **2026-08-22 (Codex `$mp --feature --next --chain`, support-screen-redesign SPEC 02):** Added per-product small/large coffee counters with one-time legacy backfill, domain-owned product IDs, monotonic persistence, cancellation-safe supporter retry, and regression coverage across datastore, sync, billing, and support consumers. Deterministic reviewer passed after adding the dedicated `ObserveSupporterStateUseCaseTest`; semantic matrix and independent critic covered 8/8 with one non-blocking warning that direct `SupporterPurchaseStoreImpl` tests could be deeper. Scoped Runner: `874/0/0`; full Runner: `2411/0/0`, detekt/lint green. Commits `662b8230`, `c2187cac`, `d660c9b2`, `e8ae3dfe`, `dbcc2800`; SPEC moved to `done/`. SPEC 03–07 remain queued in the `support-screen-redesign` epic.

- **2026-08-22 (Codex `$mp --feature --next`, support-screen-redesign SPEC 01):** Added the
  six exact-size transparent PNG-32 `support_neon_*` placeholders to `:core:designsystem`
  (hero/coffee-small/coffee-large/ads/Plus/avatar) under O1; final artwork remains a later
  replacement. Deterministic reviewer passed, semantic review covered 6/6 with one close-out
  warning resolved in the board links, independent critic passed, and Verifier passed. Scoped
  Runner: `257/0/0`; full Runner: `2409/0/0`, detekt/lint green. Commit `d30680e`; SPEC 02–07
  remain queued in the `support-screen-redesign` epic.

- **2026-08-22 (Codex `$mp --feature --next`, drawer-search-redesign SPEC 01):** Closed the
  right drawer before `NavigateSearch` via `DashboardViewModel.closeDrawers()`, preserving the
  existing search overlay/back wiring. Added focused ViewModel and Compose regression tests;
  staged only these additions alongside pre-existing dirty dashboard-test edits. Commits
  `5795aa7d` + `8de1f24f`. Deterministic reviewer, semantic review, independent critic, and
  Verifier passed; scoped Runner `403/0/0`, full Runner `2409/0/0` with detekt/lint green, and
  connected `DashboardContentUiTest` `68/0/0` on Pixel 5/API 34. Non-blocking critic warning
  `TEST-001` remains in the manual checklist; SPEC moved to `done/`. SPEC 02 remains queued.

- **2026-08-21 (Codex `$mp --bugfix`, shared-workspace reinstall recovery):** Reproduced the empty-after-reinstall flow on the Play build with Pixel 9 and traced it to two client-side failure paths: remote pull/apply errors could advance/skip state without successful materialization, and import publication failures were swallowed; the replace path also cleared the local database before a remote entitlement-gated pull was known to succeed. `SharedSyncCoordinatorImpl` now propagates pull/apply and publication failures, probes the remote journal before destructive replacement, keeps the probe page for application, advances the cursor only after completed operations, and restores/restarts only after local data was actually changed. Added regression coverage for remote rows, empty journals, malformed operations, Room/persistence failures, entitlement probe failure, and import push failure. Full runner: **2378 passed / 0 failed / 0 skipped**, detekt/lint green, debug assemble green, graphify updated. Pixel 9 debug verification preserved local rows and the recovery dialog on the entitlement failure instead of wiping/restarting; a successful remote pull remains blocked until the `MyMoney QA` workspace entitlement is active/verified on the backend.

  Follow-up production diagnosis: Supabase `pull_operations` returned HTTP 400 because its billing predicate used unqualified `where id = p_workspace_id`, ambiguous with the function's table-return column `id`. Added and applied migration `20260821170000_fix_pull_operations_workspace_id_qualification.sql`, plus a migration contract test. Direct RPC verification now returns 10 operations / max sequence 195; Pixel 9 then completed shared sync with `push_operation` and `pull_operations` returning HTTP 200. The fix is backend-only, so Play `1.0.11` does not need a new APK for this incident.

- **2026-08-20 (Codex beta verification):** Supabase production was missing the two Shared-workspace billing migrations (`workspace_payer_and_entitlement_gating` and `grant_shared_billing_columns`), causing the app's workspace discovery query to return HTTP 400 and surface `Sync failed`. Applied both migrations successfully; Pixel 9 then discovered and connected the existing `MyMoney QA` workspace, whose server billing state is `active` with 10 operations. Rewarded Plus remains deferred for a non-whitelist beta account.

- **2026-08-16 (Claude MP `--feature --next`, plus-subscription-gating SPEC 10, epic close):**
  Shipped the epic's final SPEC (10/10 done) — Block 3 of the privacy-policy monetization draft
  (`docs/legal/privacy-policy-monetization-draft.md:141-174`): dropped the "compatible builds"
  hedging around Shared workspace in both locales (app-bundled `privacy_policy_{en,ru}.html` +
  GitHub Pages mirrors `privacy-policy/{en,ru}/index.html`) and added the new paragraph stating
  Shared-workspace access requires an active Plus entitlement recorded on the Supabase backend,
  account-bound not device-bound. Size gate `warn` (12 cells, locale×surface×block — SPEC predated
  the field, derived and frozen this session); risk route high (score 9) → powerful developer,
  semantic review, independent critic, full verifier. The SPEC's own `CHANGED_HINT` line numbers
  (`:33`/`:48`) were stale — earlier-shipped `support-hub-tip-08` and `support-rewarded-ads-06`
  insertions had shifted the Supabase Auth paragraph to `:57-58` EN / `:58-59` RU; verified against
  current file state before dispatching the developer rather than trusted blindly. First semantic
  review and the independent critic both passed clean at 12/12 coverage, risk standard — but an
  orchestrator pre-check (running the pre-existing `PrivacyPolicyAdvertisingContractTest` directly,
  ahead of the formal Runner step) caught a regression neither review pass was scoped to see: the
  developer's accurate rewrite of the draft doc's status header (now correctly says all 4 blocks
  are applied, not just Block 4) dropped the literal substring `"Block 4 (Advertising) applied
  2026-08-16"` that one pre-existing test pinned on. Tester reconciled it (Stale-Test Update Rule)
  by repointing the assertion at two more stable, block-specific substrings instead of weakening
  coverage. Commits `3cd4107b` (feature) + `e55a843e` (test reconciliation) pushed to `main`. Full
  runner: `2301 passed / 0 failed / 0 skipped`, detekt/lint green. SPEC + epic overview both moved
  to `done/` after an epic-completion review confirmed all 10 SPECs shipped with commits and the
  overview's stated goal (domain entitlement model, gated billing/paywall, server-authoritative
  state machine, notifications, analytics, remote killswitch, release flip, and now the matching
  privacy-policy wording) met by the union of ships. Telegram delivery not configured (skipped
  silently); feedback question asked but user gave no rating; retro offered (59 events queued) and
  declined. `support-rewarded-ads-05` (blocked on this epic per its own CONSTRAINTS) is now
  unblocked for a future session — its only remaining blocker was `plus-subscription-gating`
  closing.

- **2026-08-16 (Claude MP `--feature --next`, plus-subscription-gating SPEC 09):** Shipped the
  shared-sync remote killswitch and the `PLAY_RELEASE_SYNC_ENABLED` release-default flip — the
  epic's penultimate SPEC, deliberately last-but-one per ADR-0010 D1 (flip only after the
  entitlement gate is done). `RemoteConfigRepositoryImpl.sharedSyncEnabled()` now ANDs the
  build-flag disjunction with `KEY_SHARED_SYNC` (new `DEFAULT_SHARED_SYNC_WHEN_BUILD_ENABLED =
  true`, distinct from the older unrelated `DEFAULT_SHARED_SYNC = false`); `sync.playReleaseEnabled`
  flipped to `?: true`. Size gate `ok` (6 cells, build_flag×remote_config — SPEC predated the field,
  derived and frozen this session); risk route high (score 18) → powerful developer, semantic
  review, independent critic, full verifier. The SPEC's own `CHANGED_HINT` named only two required
  gradle sites (`app/build.gradle.kts`, `core/sync/build.gradle.kts`) as "both required" — a THIRD
  site, `core/network/build.gradle.kts` (feeding `SharedConfigModule.SupabaseConfig.enabled`), was
  missed by the SPEC author, the Developer, the deterministic reviewer, and the first semantic-review
  pass alike; only the pre-existing `PlayInternalSyncCiContractTest` (written for an unrelated
  purpose) caught it at the scoped-runner stage, via a one-cycle Developer auto-fix. A second scoped
  iteration then reconciled that same test's stale `?: false` literal (Stale-Test Update Rule) via
  Tester. Semantic review and the independent critic each passed with one non-blocking warning (a
  stale ADR-0010 contradiction sentence, fixed inline; a fragile `.contains("&&")` structural test
  guard, deferred to the SPEC's "Deferred hardening" section). Commits `29913477`, `1628b512`,
  `54df5cf2`, `6332402e` pushed to `main`. Full runner: `2301 passed / 0 failed / 0 skipped`,
  detekt/lint green. SPEC moved to `done/`. Epic `plus-subscription-gating` NOT yet complete — SPEC
  10 remains in `backlog/`; feedback question and Telegram offer both skipped per epic-scoped
  timing. Flagged for the next session: `local.properties`/CI secrets (Dropbox app key, Supabase
  URL, anon key, Google web client ID) must be verified before the next release/CI run, since
  `requireSyncRuntimeConfiguration()` is now live on the flipped path.

- **2026-08-16 (Claude MP `--feature --next`, support-hub-tip SPEC 08, epic close):** Shipped the
  final SPEC of the `support-hub-tip` epic (8/8 done). Added the "Purchases (Google Play Billing)"
  block and replaced the stale "Firebase Remote Config is not enabled in this release" paragraph
  with the honest variant-B description (Remote Config + Analytics, both confirmed on the classpath
  since SPEC-06) — both EN/RU app-bundled policies (`app/src/main/assets/privacy_policy_*.html`)
  and their GitHub Pages mirrors (`privacy-policy/{en,ru}/index.html`). Size gate `ok` (6 cells,
  locale×block); risk route high (payment/legal content) → powerful developer, semantic review,
  independent critic, full verifier. First semantic-review pass found one blocker (`SCOPE-001`):
  the developer's initial commit only touched the app-asset files, leaving the GitHub Pages mirrors
  stale and breaking two pre-existing `PrivacyPolicyAdvertisingContractTest` identity tests — fixed
  in one repair cycle (commit `81bf0462`), re-review passed clean at 6/6 coverage. Tester added 3
  new content-pinning tests (Purchases/Firebase presence + retired-phrase absence); the full runner
  then caught a test-precision false negative (RU "Firebase Analytics" split across a verbatim
  hard-wrapped newline) fixed via the one allowed auto-fix retry (whitespace normalization, no
  content change). Independent critic and full verifier both passed clean. Commits `d6dd1eb6`,
  `81bf0462`, `87d30326`, `5c6ed25e` (feature) + `c3639e6f` (SPEC-board close-out) pushed to `main`.
  Full runner: `2298 passed / 0 failed / 0 skipped`, detekt/lint green. SPEC + epic overview both
  moved to `done/` after an epic-completion review confirmed all 8 SPECs shipped with commits and
  the overview's stated goal met; remaining out-of-repo prerequisites (Play Console coffee
  products, `GOOGLE_SERVICES_JSON` CI secret, Play Data Safety form) documented as manual follow-up.
  Telegram build offer and retro both declined by user; feedback question returned no rating.

## Historical session log archives

Read archives on demand only; do not bulk-load them during normal MP startup.

- `log/2026-08.md` - August 2026 session entries.
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
- 2026-08-16 — plus-subscription-gating SPEC 08: `EntitlementRepositoryImpl`'s inline funnel-transition
  logic (deciding which of TrialStarted/SubscriptionPurchased/SubscriptionCancelled to fire) was
  extracted into a new `SubscriptionFunnelTracker` class in `:core:billing` rather than tested via
  the repository directly, because the repository reads final/unfakeable billing-response types —
  this was a semantic-review repair-cycle fix (TEST-COVERAGE-001), not a pre-planned refactor. Also:
  the two independent-critic warnings (an `onServerConfirmed()` call inside a `mapCatching` that
  could theoretically swallow a thrown exception, and an unpinned idempotency assertion) were
  deferred to the SPEC's "Deferred hardening" section rather than fixed inline, per the
  plus-subscription-gating-06 precedent — they are a genuinely different risk class from the
  blockers already fixed in this SPEC's repair cycle, not a repeat of the same failure. Cross-ref:
  `.claude/specs/done/plus-subscription-gating-08-monetization-analytics-events.md`.
- 2026-08-15 — plus-subscription-gating SPEC 06: an `mp-architect` PREFLIGHT verdict of `PATCH ALLOWED` (issued by the prior session after its 3rd semantic-review blocker-pass exhausted the standard 2-repair-cycle budget) was treated as authorizing a fresh 2-cycle repair budget for the resuming session, not as a one-shot exception — the design capsule is what the budget protects against re-deriving, and a capsule that clears review should let repair proceed normally rather than requiring per-cycle re-authorization. Also: a non-blocking independent-critic *warning* (AUTH-RACE-001, a TOCTOU race reachable via the same `clearSharedOutbox()` destructive path the SPEC's blockers already targeted) was fixed inline rather than deferred, because it was the same failure class as an already-fixed blocker, not a new category of risk — later warnings of a genuinely different kind were deferred to the SPEC's "Deferred hardening" section instead. Cross-ref: `.claude/specs/done/plus-subscription-gating-06-local-only-transition.md`.

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
- `log/2026-08.md`
- `log/legacy.md`

For new MP closeout, add a short bullet to `## Current state` above. When it falls out of the last-three window, move it to the matching archive instead of growing this file again.
