# Signed release AAB from CI (OQ-9) within free Actions minutes
Epic: review-2026-07
Order: 03 of 35
Status: draft
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Produce a Play-ready signed release artifact from CI — add :app:bundleRelease (AAB) alongside the APK, feed the signing config from GitHub secrets (keystore base64 + passwords), and restructure workflow triggers to respect free Actions minutes: JVM job (lint/unit/detekt/ktlint/kover) on every push, the emulator job and release packaging on nightly schedule / workflow_dispatch / tags if the repo is private.
LAYERS: [data]
CHANGED_HINT: .github/workflows/ci.yml, app/build.gradle.kts (signingConfig from env when local.properties absent)
TEST_TYPES: unit
CONSTRAINTS: FREE TIER ONLY — check repo visibility first: public → minutes unlimited, keep triggers as-is; private → 2000 min/month budget, move heavy jobs off per-push; keystore/passwords never logged or committed; user-side prerequisite: add the secrets
=== END SPEC ===

## Gap / context
CI builds only an unsigned APK; Play requires a signed AAB. versionCode management
stays hardcoded until SPEC 30. Source: project review 2026-07-06, item 3 (P1/S).

## Implementation links
- commit: (pending)
- files: (pending)
