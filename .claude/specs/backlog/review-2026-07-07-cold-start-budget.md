# Bring cold start back inside the TDD budget (~5.5s today)
Epic: review-2026-07
Order: 07 of 35
Status: draft
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Profile and reduce cold start (recorded ~5.5s vs the TDD §11 budget): verify the Baseline Profile is actually generated, packaged, and applied in the release/staging build; defer non-critical initialization off the startup path (Sentry, WorkManager scheduling, sound/haptics warmup) via App Startup or lazy Hilt providers; re-measure with the existing macrobenchmark and document a repeatable local pre-release benchmark checklist run on Pixel_5_API_34.
LAYERS: [presentation] [data]
CHANGED_HINT: :app Application class / MainActivity init order, baselineprofile module + benchmark configs, app/build.gradle.kts profile wiring
TEST_TYPES: unit
CONSTRAINTS: measure before and after (macrobenchmark numbers in the SPEC report — no vibes-based claims); device gate for benchmark runs; FREE TIER — benchmarks run locally, not in CI (review item 42 folded in here); no feature behavior changes
=== END SPEC ===

## Gap / context
PROGRESS records cold start and frame budgets missing TDD targets; Baseline Profile
was generated once but application is unverified. Source: review items 39+42 (P1/M).

## Implementation links
- commit: (pending)
- files: (pending)
