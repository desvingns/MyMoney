# Fix macrobenchmark launch/dashboard probes after dashboard redesign
Epic: review-2026-07
Order: 02b of 35
Status: done
Depends-on: review-2026-07-02
Date: 2026-07-08

## SPEC
=== SPEC ===
TASK: bugfix
WHAT: Repair the macrobenchmark launch journey so `StartupBenchmark` recognizes the current release onboarding and neon dashboard surfaces, then re-run `:macrobenchmark:connectedBenchmarkReleaseAndroidTest` on `Pixel_5_API_34`.
LAYERS: [presentation]
CHANGED_HINT: macrobenchmark/src/main/java/com/kshavrin/mymoney/macrobenchmark/BenchmarkJourney.kt, macrobenchmark/src/main/java/com/kshavrin/mymoney/macrobenchmark/StartupBenchmark.kt
TEST_TYPES: [instrumented]
CONSTRAINTS: do not change production app behavior; keep selectors localized/stable; prefer current semantic text/content descriptions from the rendered UI; benchmark must target the minified `benchmarkRelease` app and trust the connected XML, not only Gradle status
=== END SPEC ===

## Gap / context
Slice 5 attempt on 2026-07-08 ran `:macrobenchmark:connectedBenchmarkReleaseAndroidTest`
on `Pixel_5(AVD) - 14`; all 3 `StartupBenchmark` tests failed because
`BenchmarkJourney` still probes for `MyMoney` or `Balance`, while the current dashboard
renders signals such as `FREE BALANCE`, `0 $`, `No expenses this period`, and toolbar
content descriptions. Evidence:
`macrobenchmark/build/outputs/androidTest-results/connected/benchmarkRelease/TEST-Pixel_5(AVD) - 14-_macrobenchmark-.xml`,
`build/visual-check/mymoney-benchmark-after-onboarding-2.png`.

## Implementation links
- commit: 48d4521832064724cc76f307b82d70e59b0656e1
- files: macrobenchmark/src/main/java/com/kshavrin/mymoney/macrobenchmark/BenchmarkJourney.kt
