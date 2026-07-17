# Enable real cloud sync: Dropbox app + Google Drive OAuth (free quotas)
Epic: review-2026-07
Order: 22 of 35
Status: active
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Take cloud sync from gated-off to actually testable: register the Dropbox app (development mode, free, ≤500 users) and the Google Drive OAuth client (personal quota, free), inject credentials via local.properties/CI secrets into the existing BuildConfig seams, flip the cloud-sync feature gate on for debug/staging, and run + record ONE real end-to-end journal-sync round-trip per provider on Pixel_5_API_34 (upload from a fresh install, pull on second install/emulator profile, verify merged state).
LAYERS: [data] [presentation]
CHANGED_HINT: :core:sync Drive/Dropbox transports, :feature:cloudsync gate, app/build.gradle.kts BuildConfig fields, .github/workflows/ci.yml secrets
TEST_TYPES: unit [compose-ui]
CONSTRAINTS: FREE TIER ONLY — no Google Cloud billing enablement, no Dropbox production-approval steps beyond the free flow; user-side prerequisite: perform the registrations / consent screens (implementation prepares exact click-by-click instructions); secrets never committed; device gate for the round-trip evidence
=== END SPEC ===

## Gap / context
OQ-2/OQ-3: the whole journal-sync epic shipped without one real network round-trip.
Source: review item 4 (P2/M).

## Status note (2026-07-17)
BLOCKED on user-side external registrations — kept in `active/`, not shipped to `done/`.

**Prep slice shipped** (commit `ea53550e`, all gates green; runner false-negative verified via
`:core:sync:testDebugUnitTest` BUILD SUCCESSFUL):
- Debug-only gate-flip: `SYNC_FORCE_ENABLED` (from `-Psync.forceEnabled`), DEBUG-guarded so release
  stays gated OFF — `syncForced() = BuildConfig.DEBUG && BuildConfig.SYNC_FORCE_ENABLED`.
- CI step to materialize `dropbox.appKey` from `secrets.DROPBOX_APP_KEY` (mirrors SENTRY_DSN pattern,
  all job blocks; no-op when the secret is absent).
- `docs/cloud-sync-setup.md` — free-tier click-by-click for Dropbox App Console + Google Cloud OAuth.
- Design note: Drive uses account-based OAuth (`GoogleAccountCredential`), so NO Drive key/secret is
  embedded — only the Cloud Console OAuth client (package + SHA-1) + a signed-in Google account.
- Release-safety of the DEBUG guard was raised as an uncertainty and accepted: `BuildConfig.DEBUG` is
  set by AGP and cannot be flipped by a `-P` property, so a release build stays OFF even with the flag.

**Remaining before this SPEC can close** (user-side + deferred):
1. User registers the Dropbox app + Google Cloud OAuth client per `docs/cloud-sync-setup.md`, puts
   `dropbox.appKey` in `local.properties`, and signs a test Google account into the device.
2. Verify a real `Pixel_5_API_34` device, build `-Psync.forceEnabled=true`, run + RECORD one real
   end-to-end journal-sync round-trip per provider (upload fresh install → pull second install →
   verify merged state). Then move `active/ → done/`.

## Implementation links
- commit: ea53550e (prep slice only — round-trip evidence pending)
- files: core/sync/build.gradle.kts, core/sync/src/main/java/com/kshavrin/mymoney/core/sync/remoteconfig/RemoteConfigRepositoryImpl.kt, .github/workflows/ci.yml, docs/cloud-sync-setup.md
