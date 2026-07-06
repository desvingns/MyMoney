# Run the missing androidTest modules in the CI emulator job
Epic: review-2026-07
Order: 09 of 35
Status: draft
Depends-on: review-2026-07-03
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Extend the existing CI connected-tests job to also run :core:sync, :core:network, and :feature:lockscreen connectedDebugAndroidTest (currently only app/designsystem/database/datastore run anywhere in CI), reusing the single emulator boot with per-module timeouts matching the existing pattern.
LAYERS: [data]
CHANGED_HINT: .github/workflows/ci.yml (connected job steps)
TEST_TYPES: unit
CONSTRAINTS: FREE TIER — one emulator boot for all modules; respect the trigger schedule decided in SPEC 03 (nightly/workflow_dispatch if the repo is private); keep if-no-files-found report uploads; do not raise total job timeout beyond what free minutes allow
=== END SPEC ===

## Gap / context
Three modules have instrumented tests that no CI run ever executes — device-only
regressions land silently. Source: review item 8 (P2/S).

## Implementation links
- commit: (pending)
- files: (pending)
