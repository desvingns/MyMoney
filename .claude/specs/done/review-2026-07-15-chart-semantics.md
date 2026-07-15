# TalkBack semantics for the dashboard balance trend chart
Epic: review-2026-07
Order: 15 of 35
Status: done
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Give the dashboard's custom Canvas BalanceTrendChart a spoken contract: it exposes a summary description containing the selected metric, period, start→end values, and direction; verify it with Compose semantics-tree assertions in instrumented tests.
LAYERS: [presentation]
CHANGED_HINT: core/designsystem/**/BalanceTrendChart.kt, dashboard call sites, and its existing androidTest class
TEST_TYPES: [compose-ui]
CONSTRAINTS: semantics only — zero visual/behavioral change (screenshot-identical; SPEC 12 baselines if landed); strings localized EN+RU; retain the dashboard's own graph design and do not integrate MonefyDonutChart; device gate for the instrumented verification
=== END SPEC ===

## Gap / context
The dashboard's custom Canvas trend graph is almost certainly mute for TalkBack users.
Product decision (2026-07-15): the app intentionally has its own dashboard design and does not
use a Monefy-style donut.

## Implementation links
- commits: `9d51e58b`, `902f8d2c`, `6ae9d50c`
- files: `BalanceTrendChart.kt`; dashboard chart-metric call sites; `BalanceTrendChartUiTest.kt`
