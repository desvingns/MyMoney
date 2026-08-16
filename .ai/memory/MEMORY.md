# MyMoney shared memory (both tools) — index + facts

Durable, git-tracked facts BOTH Claude and Codex need. Append-mostly; English;
never delete (refine in place with a provenance trail). Seeded 2026-07-05 from
tool-local memories — tool-local copies remain historical snapshots.

- Authoritative spec is the TDD (`TDD/MyMoney/MyMoney_TDD.md` relative to the checkout;
  absolute paths differ host vs guest). Cite line ranges, never paraphrase.
- Visual/instrumented device gate: accept local `emulator-5554` when AVD id is either
  `Pixel_5_API_34` or the current alias `Pixel_5`, SDK is 34, and boot is complete. Run local
  discovery and never kill/reconnect healthy local ADB. **VirtualBox is retired by user directive
  (2026-07-12): never try `10.0.2.2:5555`, `ADB_SERVER_SOCKET`, or any guest/NAT attach unless the
  user explicitly restores VirtualBox.** Use `scripts/run_connected_test_on_host_avd.ps1` for
  Gradle connected tests.
- File deletion: move to repo-root `archive/` (git-ignored) with a `.<reason>` suffix;
  user empties it manually (AGENTS.md).
- Post-ship ordering: deliver the built APK to Telegram BEFORE asking the 1-5 feedback
  question; pass the explicit `app/build/outputs/apk/debug/app-debug.apk` path so the
  test APK is not sent by mistake (2026-06-21 lesson, now also in the mp orchestrator).
- Deviations AS-12 (range date picker) and AS-14 (donut labels >=3%) are locked —
  never "fix" them back to Monefy behavior.
- External services must stay on FREE tiers (user directive, 2026-07-06): Sentry
  Developer plan in errors-only mode (`tracesSampleRate = 0`, no session replay /
  profiling, ~5k errors/mo budget + quota alert), GitHub Actions within free minutes
  (run heavy emulator jobs nightly/`workflow_dispatch` if the repo is private),
  Firebase Spark plan, personal Dropbox/Google Drive quotas. Never propose or wire
  paid tiers or features that exceed free limits.
- `--feature --next` / `--feature --backlog` consume an already-approved SPEC; do not
  ask for an extra pre-agent confirmation gate. (User correction, 2026-07-14.)
- Compose 1.8 accessibility tests need an explicit `onRoot().tryPerformAccessibilityChecks()` trigger; keep the ATF check isolated and retain manual touch-target assertions, with 23/23 device evidence.
- Cloud OAuth regression retrospective (2026-07-22): the working Google `drive.appdata` design was
  replaced without compatibility evidence by Picker/shared-folder storage and then restricted full
  `drive`; the first repair removed Picker but retained the failing authorization path. Build/unit
  success was incorrectly treated as OAuth proof and the result was handed to the user unverified.
  For every external OAuth/sync change, require fresh consent on a real target device, persisted
  account identity after restart, and an observed remote push/pull before reporting it fixed.
- Personal cloud product decision (2026-07-22): Dropbox App Folder or Google `appDataFolder`, exactly
  one active provider/account binding, no user-selected folder, and sync only Transaction/Account/
  Category. Different provider accounts remain isolated; multi-user collaboration belongs to the
  separate Supabase Shared-mode backlog epic.
- Connected-device safety incident (2026-07-22): selecting a Pixel 5 serial in a PowerShell helper
  does not constrain AGP/UTP; `connectedDebugAndroidTest` can run on every healthy local emulator.
  Never run the task while user-owned devices are attached. The helper must abort before Gradle when
  any non-gate device is present, and only a parsed Pixel 5 result may be called device verification.
- Connected-test tooling (2026-07-28): `scripts/mp-runner-instrumented-android.ps1` parses XML only
  under `app/build/...`; for `:feature:*:connectedDebugAndroidTest` run `run_connected_test_on_host_avd.ps1`
  directly and parse `<module>/build/outputs/androidTest-results/connected/**/TEST-*.xml` manually.
  Every `@HiltAndroidTest` androidTest module also needs its own `TestDataStoreModule`
  (@TestInstallIn replacing DataStoreModule, unique `test_settings_${UUID}` file per component) —
  without it, multiple test classes in one instrumentation process collide on the production
  `app_settings.preferences_pb` file (fixed in :feature:lockscreen, commit f01272c9).
- Git commit hygiene on Windows (2026-07-28, SPEC review-2026-07-35): never emit a PowerShell
  here-string (`git commit -m @"..."@`) into a Git Bash shell — bash strips the quotes and git
  folds the literal `@` lines into the subject (4 commits shipped with a stray "@ " prefix:
  f451c240, b41aecad, 5cdddc78, 8ddb79cf). Use plain `git commit -m "..."` or a bash heredoc.
- `git mv` of a just-edited file stages ONLY the 100% rename; the fresh content edits stay
  unstaged and the next commit records the rename alone (bit SPEC activation commit 4f0d2aaf,
  folded into the close commit). After `git mv` of a file you just edited, always `git add <dest>`
  and check `git status` before committing.
- Gradle-property fan-out (2026-08-16, SPEC plus-subscription-gating-09): `sync.playReleaseEnabled`
  (and its sibling `sync.playInternalEnabled`) is declared independently in **three**
  `build.gradle.kts` files — `app/`, `core/sync/`, and `core/network/` — each feeding its own
  `BuildConfig` field, no single source of truth. A SPEC's `CHANGED_HINT` named only two of the
  three as "both required"; the planner, the developer (who matched the SPEC exactly), and the
  first semantic-review pass all missed the third site (`core/network/build.gradle.kts:19-21`,
  read by `SharedConfigModule.kt` for `SupabaseConfig.enabled`) — it was only caught by a
  pre-existing CI-contract test (`PlayInternalSyncCiContractTest`) written for an unrelated
  purpose. Before trusting a SPEC's declared file count for any `sync.*`/gradle-property default
  flip, `grep -rn "propertyName" --include=build.gradle.kts .` first (fixed in commit 54df5cf2).
- Privacy-policy status-header test pin (2026-08-16, SPEC plus-subscription-gating-10):
  `PrivacyPolicyAdvertisingContractTest` asserts literal substrings of
  `docs/legal/privacy-policy-monetization-draft.md`'s status header (line 3) and per-block table
  rows, not just the four policy HTML files. A SPEC that legitimately rewrites that header (e.g.
  "Block 4 applied" -> "Blocks 1-4 all applied") can silently break this test even when the edit is
  correct; neither the semantic reviewer nor the independent critic caught it (their acceptance
  matrix only covers the SPEC's own declared cells) — only running the test directly did. After any
  edit to that draft file's status header/table rows, run
  `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew --console=plain :app:testDebugUnitTest --tests "com.kshavrin.mymoney.PrivacyPolicyAdvertisingContractTest"`
  before trusting a clean review (fixed in commit e55a843e).
- Epic `plus-subscription-gating` closed 2026-08-16 (10/10 SPECs shipped, overview moved to
  `.claude/specs/done/`). This lifts the cross-epic block on `support-rewarded-ads-05` (its
  CONSTRAINTS named `plus-subscription-gating` as a hard dependency) — it is now the lowest-NN
  runnable backlog file for the next `--feature --next`, pending a staleness check of its
  CHANGED_HINT paths against the now-shipped `:feature:support` module.

## Monetization backend setup (2026-08-12, Codex)

- Server-side monetization was configured for `com.kshavrin.mymoney` without changing Android production code. The implementation is ready for the Android integration phase.
- Supabase project: `shwzjlkhlpgbmzgnxhxi` (EU West / Ireland, Free plan). Applied migrations created `entitlements`, `supporters`, `ad_rewards`, `provider_events`, and `activation_codes`, with RLS, protected redemption RPCs, provider tracking, and the five verified AdMob rewards -> 24-hour Plus rule.
- Permanent Plus whitelist is stored as `provider = whitelist`: owner `desvingns@gmail.com` / `ba7a8711-9593-452b-9294-ef652a28ab37`; tester `desving123456@gmail.com` / `5227cbf1-5b25-4362-8a85-c24ef679277b`.
- Edge Functions are active: `redeem-activation-code`, `create-ad-reward-token`, `admob-ssv` (version 10, public callback), `google-play-rtdn` (OIDC-protected public webhook), and `bind-google-play-purchase`.
- Google Cloud project `my-money-502807` (project number `622958532340`) has Google Play Android Developer API and Cloud Pub/Sub enabled. Play service account `mymoney-play-api@my-money-502807.iam.gserviceaccount.com` is linked to Play Console for `com.kshavrin.mymoney`; its JSON is stored only as the Supabase secret `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`, never in the repository.
- RTDN uses topic `projects/my-money-502807/topics/mymoney-play-rtdn`, existing push subscription `mymoney-play-rtdn-sub`, OIDC identity `mymoney-pubsub-push`, and audience `https://shwzjlkhlpgbmzgnxhxi.supabase.co/functions/v1/google-play-rtdn`. Test notifications were processed successfully. `RTDN_WEBHOOK_TOKEN` was removed after OIDC verification and must not be reintroduced.
- Supabase secrets configured by name only (values are intentionally not recorded): `AD_REWARD_TOKEN_SECRET`, `AD_REWARD_TOKEN_TTL_SECONDS`, `ADMOB_REWARDED_AD_UNIT_ID`, and `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`. The AdMob rewarded unit is `ca-app-pub-2270788427402644/2408550762`; the AdMob app ID is `ca-app-pub-2270788427402644~3554111471`.
- AdMob SSV callback is `https://shwzjlkhlpgbmzgnxhxi.supabase.co/functions/v1/admob-ssv`. The handler verifies rotating AdMob signatures, enforces the real ad unit for production callbacks, accepts the AdMob dummy `ad_unit` only for signature-valid URL verification without `custom_data`, and deduplicates by `transaction_id`.
- Firebase project `mymoney-analytics` was created on the Spark plan with Android app `com.kshavrin.mymoney`; `C:\Users\Admin\Downloads\google-services.json` was downloaded but not copied into the Android repository. Adding Firebase SDKs remains an Android-side task.
- Future Android integration contract: authenticated client calls `create-ad-reward-token`, passes returned `custom_data` to the rewarded ad request, optionally passes the same Supabase user ID as `user_id`, and uses `bind-google-play-purchase` with a Play purchase token. Actual reward end-to-end testing is intentionally deferred until this client work exists.
- Free-tier constraint is locked for this setup: no Google Cloud billing account or trial was activated; Supabase Free, Firebase Spark, and low-volume server-side paths remain the baseline.

## Connected services

- Supabase MCP plugin is connected and available to both Codex and Claude for this project.
