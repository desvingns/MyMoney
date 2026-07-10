# Bring cold start back inside the TDD budget (~5.5s today)
Epic: review-2026-07
Order: 07 of 35
Status: done
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

## Result (measured, Pixel_5(AVD) - 14, SDK 34, 10 iterations)

`:macrobenchmark:connectedBenchmarkReleaseAndroidTest` — `StartupBenchmark#coldStartupToDashboard`,
`timeToInitialDisplayMs`:

| | before (2c1dc359) | after (e9905cea) | delta |
|---|---|---|---|
| median | 1199.3 ms | 1101.3 ms | -98.0 ms (-8.2%) |
| min | 1135.1 ms | 983.9 ms | -151.2 ms (-13.3%) |
| max | 1858.8 ms | 1169.5 ms | -689.3 ms (-37.1%) |

The SPEC's "~5.5 s" premise was stale: that figure came from a debug build (see
`docs/audit/2026-06-10-project-audit.md:117`, which itself says "re-measure via :macrobenchmark
on release"). The real starting point on benchmarkRelease was ~1.2 s.

The TDD §11 budget (<= 600 ms to first interactive) is stated for a PHYSICAL Pixel 5. It remains
UNVERIFIED — an x86_64 emulator is not comparable to that budget. These numbers are valid only
as a before/after delta on the same AVD.

Baseline Profile packaging: `assets/dexopt/baseline.prof` confirmed present in `app-release.apk`
and, after the `matchingFallbacks` fix, in `app-staging.apk`. Before this SPEC `:app:assembleStaging`
did not resolve at all, so staging had never shipped a profile.

## Implementation links
- commits: `5f43a63f` (defer init), `402ad667` (tests), `aaa9ea91` (staging build + profile), `e9905cea` (ktlint)
- files:
  - `app/src/main/java/com/kshavrin/mymoney/MyMoneyApp.kt`
  - `app/src/main/java/com/kshavrin/mymoney/MainActivity.kt`
  - `app/build.gradle.kts`
  - `docs/perf/PRE_RELEASE_BENCHMARK_CHECKLIST.md`
  - `app/src/test/java/com/kshavrin/mymoney/LazyPlayerDelegatesTest.kt`
  - `app/src/test/java/com/kshavrin/mymoney/SentryFreeTierContractTest.kt`
