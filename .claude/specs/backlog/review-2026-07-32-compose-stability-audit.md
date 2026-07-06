# Compose stability audit: compiler metrics + immutable collections
Epic: review-2026-07
Order: 32 of 35
Status: draft
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Run the Compose compiler metrics/reports once across all Compose modules, review every UNSTABLE class/parameter that participates in hot recomposition paths (dashboard state, list rows), and fix the flagged ones — kotlinx.collections.immutable (ImmutableList) for collection-bearing UiState fields and/or @Immutable annotations where structural immutability is real but not inferred; commit the metrics summary and the fix list as the SPEC report.
LAYERS: [presentation]
CHANGED_HINT: root/module build.gradle.kts (compiler metrics flags, temporary), feature/*/**/ *UiState/ *State classes, gradle/libs.versions.toml (kotlinx-collections-immutable)
TEST_TYPES: unit
CONSTRAINTS: evidence-driven — fix ONLY what the metrics flag on real recomposition paths, no blanket annotation spraying; kotlinx.collections.immutable is a runtime dep addition → one-line user ack at implement time; existing tests updated per stale-test rule
=== END SPEC ===

## Gap / context
Stability rests on compiler inference today; one metrics pass either proves it fine
or pinpoints the recomposition leaks. Source: review items 41+50 (P3/S).

## Implementation links
- commit: (pending)
- files: (pending)
