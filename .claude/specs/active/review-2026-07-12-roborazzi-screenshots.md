# Roborazzi screenshot regression suite for designsystem + key screens
Epic: review-2026-07
Order: 12 of 35
Status: draft
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Add JVM screenshot regression testing with Roborazzi + Robolectric: first add @Preview-style composable entry points for the complex :core:designsystem components (MonefyDonutChart, BalanceTrendChart, Aurora balance panel, calculator keypad), then record baseline screenshots for them plus 3–4 key screen states (dashboard day view, add-expense, transactions list) in light+dark, and wire verifyRoborazziDebug as an opt-in Gradle task documented for the mp-runner "optional Roborazzi screenshot verify" hook.
LAYERS: [presentation]
CHANGED_HINT: core/designsystem (previews + tests), gradle/libs.versions.toml (roborazzi, robolectric — test-only deps), feature/dashboard test sources
TEST_TYPES: unit [screenshot]
CONSTRAINTS: test-only dependencies (production stack stays TDD-locked); baselines committed to VCS with a documented re-record command; deterministic rendering (fixed locale/fontScale/time, seeded data); JVM-based — no device or CI minutes needed
=== END SPEC ===

## Gap / context
Visual culture is manual today (hand-pulled device PNGs); Roborazzi automates it and
the /mp runner already supports the verify hook. Source: review items 12+49 (P2/M).

## Implementation links
- commit: (pending)
- files: (pending)
