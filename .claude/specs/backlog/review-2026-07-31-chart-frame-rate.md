# Chart render performance: donut + balance trend inside frame budget
Epic: review-2026-07
Order: 31 of 35
Status: draft
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Profile and optimize the two Canvas charts against the TDD frame budgets: move Path building and text measurement out of the draw phase (drawWithCache / remember keyed on data+size, TextMeasurer results cached), eliminate per-frame allocations in animation lambdas, and verify with Compose tracing / macrobenchmark frameTimeline metrics on Pixel_5_API_34 before/after — especially during donut sweep animation and trend scrubbing.
LAYERS: [presentation]
CHANGED_HINT: core/designsystem/**/MonefyDonutChart.kt (1004 lines, DonutAnimationKey memoization exists — extend it), core/designsystem/**/BalanceTrendChart.kt (817 lines)
TEST_TYPES: unit [compose-ui] [screenshot]
CONSTRAINTS: pixel-identical output (SPEC 12 Roborazzi baselines are the regression net if landed; otherwise before/after device screenshots); AS-14 ≥3% label rule untouched; measured frame numbers in the report; device gate for measurement runs
=== END SPEC ===

## Gap / context
PROGRESS records frame rates missing TDD targets; the charts are the obvious
hot path. Source: review item 40 (P2/M).

## Implementation links
- commit: (pending)
- files: (pending)
