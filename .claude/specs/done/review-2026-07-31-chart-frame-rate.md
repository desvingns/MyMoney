# Chart render performance: donut + balance trend inside frame budget
Epic: review-2026-07
Order: 31 of 35
Status: done
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

## Result

Implemented draw-phase caching for both Canvas charts. Donut geometry, text
layouts, icon painters, and animation-keyed data are reused through
drawWithCache/remember; balance-trend geometry, paths, styles, and text
layouts are cached by chart data and size. AS-14 (labels on slices >=3%) was
left unchanged.

Verification on Pixel_5 (AVD), SDK 34, emulator-5554:

- core design-system JVM/lint/detekt/ktlint gates: PASS
- instrumented compilation: PASS
- MonefyDonutChartUiTest: 44/44 passed
- BalanceTrendChartUiTest: 30/30 passed
- existing screenshot and geometry tests reviewed; no updates required
- deterministic reviewer: PASS
- graphify AST update: PASS

The available pre-change benchmark baseline on the same x86_64 Pixel 5 AVD
reported frameCount median 29, frameDurationCpuMs P50/P90/P95/P99 of
20.767902/143.138542/252.031433/320.041927 ms, and frameOverrunMs
P50/P90/P95/P99 of 6.461231/226.922225/312.165845/557.066492 ms.
A fresh post-change macrobenchmark was attempted on the required device but
was blocked before execution by the repository's release-tag guard; no
post-change number is claimed. Physical-device/release-tag benchmark
re-measurement remains deferred environment work.

## Implementation links
- commits: 994a1c82, 0c78a86b, 66261f17, c94a9552, 61a7d476, de27b70e
- files:
  - core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/donut/MonefyDonutChart.kt
  - core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/chart/BalanceTrendChart.kt
  - core/designsystem/src/androidTest/java/com/kshavrin/mymoney/core/designsystem/donut/MonefyDonutChartUiTest.kt
  - core/designsystem/src/androidTest/java/com/kshavrin/mymoney/core/designsystem/chart/BalanceTrendChartUiTest.kt
