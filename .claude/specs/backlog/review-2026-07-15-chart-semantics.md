# TalkBack semantics for the Canvas charts (donut + balance trend)
Epic: review-2026-07
Order: 15 of 35
Status: draft
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Give the two custom Canvas charts a spoken contract: MonefyDonutChart exposes a semantics description of the period totals plus per-slice category/percentage values (e.g. "Food, 42%"; honoring the AS-14 ≥3% label rule for which slices are enumerated), and BalanceTrendChart exposes a summary description (metric, period, start→end values, direction); verify with Compose semantics-tree assertions in instrumented tests.
LAYERS: [presentation]
CHANGED_HINT: core/designsystem/**/MonefyDonutChart.kt (1004 lines), core/designsystem/**/BalanceTrendChart.kt, their existing androidTest classes
TEST_TYPES: [compose-ui]
CONSTRAINTS: semantics only — zero visual/behavioral change (screenshot-identical; SPEC 12 baselines if landed); strings localized EN+RU; AS-14 stays locked; device gate for the instrumented verification
=== END SPEC ===

## Gap / context
The app's two central visualizations are drawn on Canvas and are almost certainly
mute for TalkBack users. Source: review item 31 (P2/M).

## Implementation links
- commit: (pending)
- files: (pending)
