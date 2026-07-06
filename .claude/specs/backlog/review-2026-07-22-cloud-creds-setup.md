# Enable real cloud sync: Dropbox app + Google Drive OAuth (free quotas)
Epic: review-2026-07
Order: 22 of 35
Status: draft
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

## Implementation links
- commit: (pending)
- files: (pending)
