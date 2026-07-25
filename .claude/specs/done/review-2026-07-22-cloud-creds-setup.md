# Enable real private cloud sync: Dropbox App Folder + Google Drive appDataFolder
Epic: review-2026-07
Order: 22 of 35
Status: done
Depends-on: —
Date: 2026-07-06
Revised: 2026-07-22

## SPEC
=== SPEC ===
TASK: feature
WHAT: Replace shared-folder Google Drive sync with private app-owned journal storage, enforce exactly one active cloud binding (Dropbox or Google Drive), make first-connect and provider migration safe, simplify the cloud-sync UI, and prove real bidirectional sync for both providers on two devices.
LAYERS: [data] [presentation]
CHANGED_HINT: :core:datastore cloud binding/checkpoints, :core:sync journal contracts/orchestrator/Dropbox/Google transports, :feature:cloudsync connect-disconnect-switch UX, docs/cloud-sync-setup.md
TEST_TYPES: unit [compose-ui] [instrumented-compose-ui] [real-e2e]
CONSTRAINTS: Journal entities remain Transaction/Account/Category; Google requests only drive.appdata and stores ops-<deviceId>.jsonl under appDataFolder; Dropbox remains App Folder; no folder picker/URL/ID or restricted drive scope; one active provider; account change requires Disconnect; local and remote financial data survive Disconnect; ordinary personal conflicts remain deterministic last-write-wins; provider migration uses a safety backup and one-time conflict review; old shared-folder files are never read, changed, or deleted; external OAuth is not verified until fresh consent, persisted identity, and a real push/pull round-trip succeed; FREE TIER ONLY; preserve unrelated dirty-worktree changes.
=== END SPEC ===

## Product contract

- Personal cloud sync is private to one provider account. Two devices connected to the same account
  converge; a different account has separate app-owned storage.
- Sync scope is financial data and dictionaries only: transactions, accounts, and categories.
  Theme, language, PIN, biometrics, OAuth tokens, and autosync preferences remain device-local.
- Google Connect/Reconnect shows the account chooser. A different selected account cannot replace an
  existing binding; the user must Disconnect first.
- Exactly one provider is active. If an upgrade finds both providers connected, background sync stops
  until the user chooses one. The other provider is not silently disconnected or written.
- First connect is pull-before-push: merge an existing remote journal before publishing the complete
  cumulative local journal. An empty remote receives the local journal.
- Dropbox ↔ Google switching is explicit: create a local safety backup, read and merge the target,
  show a one-time review for conflicting entity versions, then commit the active binding.
- Disconnect clears local credentials, binding, provider/account cursors, and autosync scheduling. It
  does not delete the local database or remote journal.
- The primary screen shows provider, account, last successful sync, Connect/Disconnect/Switch, and
  understandable errors. Folder IDs and remote journal filenames are not primary UI.
- Remote cloud-data deletion is deferred to a separate future SPEC.

## Google Drive transport

- Authorization scope: `https://www.googleapis.com/auth/drive.appdata` only.
- Create journal files with `parents = ["appDataFolder"]`.
- List journal files with `spaces = "appDataFolder"` and the existing journal-name filter.
- The backend owns its storage root; `JournalBackend` callers do not pass a folder ID.
- Store a stable provider account identifier plus display email. Email is a label, not the isolation key.
- Remove the manual shared-folder and full-Drive instructions from `docs/cloud-sync-setup.md`.

## Acceptance and evidence

Automated:

- appData create/list parameters and folder-free backend contract;
- one-active-provider and legacy-two-provider selection;
- account mismatch requires Disconnect;
- pull-before-push bootstrap and provider/account-scoped cursors;
- disconnect keeps financial data and prevents further scheduled sync;
- OAuth cancellation/error never creates a connected binding;
- migration safety backup and one-time conflict review;
- simplified Compose state/content with no folder input.

Device and external-service acceptance:

1. Run automated connected tests on the documented Pixel 5 API 34 gate device.
2. Install the same verified debug build on Pixel 9 and Pixel 8 without clearing their data.
3. The user performs only account selection/consent. Connect Google on both as
   `desving123456`, restart both apps, and verify persisted identity plus Pixel 9 → Pixel 8 and
   Pixel 8 → Pixel 9 operations.
4. Repeat the bidirectional round-trip through Dropbox account `desving123456`.
5. Keep recognizable E2E marker records and record connected state, account label, remote
   upload/list/download evidence, and the resulting records on the peer device.

The SPEC remains active and must be reported as `unverified` or `blocked` until both real provider
round-trips pass. Compilation, unit tests, or a consent screen alone do not close it.

## Retrospective

The previously working `drive.appdata` flow was changed to Picker/shared-folder storage and then to
restricted full-Drive authorization without proving compatibility. The first repair removed the
Picker trigger but retained the failing authorization/storage design. Build and unit-test success was
incorrectly treated as OAuth evidence, and the broken build was handed to the user. Future external
OAuth changes require fresh consent on a target device, persisted account identity, and an observed
remote push/pull before they are described as fixed.

## Completion and evidence

- implementation is intentionally uncommitted: the worktree contains unrelated user changes and
  publishing remains an explicit later gate;
- JVM verification: `:core:sync:testDebugUnitTest` **78/0/0** and
  `:feature:cloudsync:testDebugUnitTest` **4/0/0**; `:app:assembleDebug
  -Psync.forceEnabled=true` passed;
- Pixel 5 / API 34 Compose instrumented evidence: `CloudSyncContentUiTest` **3/0/0** in
  `app/build/outputs/androidTest-results/connected/debug/TEST-Pixel_5(AVD) - 14-_app-.xml`;
- Google Drive real round trip (same `desving123456` account) is recorded in
  `outputs/e2e/google-roundtrip-complete-db-evidence.txt`: Pixel 9 → Pixel 8 and Pixel 8 →
  Pixel 9 both arrived after fresh consent and restart-persisted identity;
- Dropbox real round trip (same `desving123456` account) is recorded in
  `outputs/e2e/dropbox-roundtrip-complete-db-evidence.txt`: the peer records contain 12.0,
  `E2E` 7.0, 9.0, and 6.0 from both device identities;
- runner incident and recovery: an AGP connected-test invocation selected every healthy emulator,
  not just Pixel 5. The helper now refuses to start while another device is attached. The user
  reconnected both reinstalled devices to Dropbox; remote data was never deleted and recovered on
  both. Evidence: `outputs/e2e/recovery/dropbox-recovery-after-runner-incident.txt`.

The active binding left on both Pixel 8 and Pixel 9 is Dropbox / `desving123456@gmail.com`. Google
was explicitly disconnected after its completed E2E in order to preserve the one-active-provider
contract. This SPEC is complete: both providers have real fresh-consent, persisted-identity, and
bidirectional remote-data evidence.
