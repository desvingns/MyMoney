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
