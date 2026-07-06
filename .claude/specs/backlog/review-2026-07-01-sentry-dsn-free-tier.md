# Wire Sentry crash reporting on the free Developer plan (errors-only)
Epic: review-2026-07
Order: 01 of 35
Status: draft
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Activate Sentry crash reporting with a real DSN injected via CI secret / local.properties into BuildConfig.SENTRY_DSN, configured strictly for the free Developer plan: errors-only (tracesSampleRate = 0, no session replay, no profiling, no performance), plus a documented quota-alert setup so 5k errors/month is never exceeded.
LAYERS: [data]
CHANGED_HINT: app/build.gradle.kts (SENTRY_DSN plumbing), :app Sentry init / SentryExt.kt, .github/workflows/ci.yml (secret injection), docs note for the manual Sentry-side steps
TEST_TYPES: unit
CONSTRAINTS: FREE TIER ONLY (see .ai/memory/MEMORY.md) — no options that require a paid Sentry plan; DSN value itself is a user-side prerequisite (create Sentry project, add GitHub secret) — implementation must degrade to current blank-DSN behavior when the secret is absent; sentry-android-core stays (umbrella sentry-android was rejected in PHASE_15 for APK size)
=== END SPEC ===

## Gap / context
OQ-1: DSN is blank-by-default, so production crashes go nowhere while all reporting
code already exists. Source: project review 2026-07-06, item 1 (P1/S).

## Implementation links
- commit: (pending)
- files: (pending)
