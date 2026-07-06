# Decompose DashboardViewModel into sub-coordinators
Epic: review-2026-07
Order: 33 of 35
Status: draft
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Decompose the 1343-line, 12-dependency DashboardViewModel into focused sub-coordinators (trend series, operations summary, period/filter selection), each owning its slice of state and composed into the single exposed DashboardState — preserving the external UiState/Event/Action contract byte-for-byte so DashboardScreen and all 238 existing unit tests keep passing (tests may be reorganized per coordinator, never weakened).
LAYERS: [presentation]
CHANGED_HINT: feature/dashboard/**/DashboardViewModel.kt, new coordinator classes alongside, DashboardViewModelTest.kt (2760+ lines — split opportunity)
TEST_TYPES: unit [compose-ui]
CONSTRAINTS: NOT an architecture simplification — same UDF pattern, same repository interfaces, pure internal decomposition; zero behavior change (existing tests are the contract); TIMING: intentionally last — implement only alongside the next substantial dashboard work, not as standalone churn
=== END SPEC ===

## Gap / context
Largest class in the codebase; justified today but each new dashboard feature makes
it worse — decompose when next touching it. Source: review item 48 (P3/L).

## Implementation links
- commit: (pending)
- files: (pending)
