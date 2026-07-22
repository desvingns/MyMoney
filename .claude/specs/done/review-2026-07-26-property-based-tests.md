# Property-based tests for pure deterministic logic
Epic: review-2026-07
Order: 26 of 35
Status: done
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Add property-based tests for the pure calculators where invariants matter more than examples: OperationMerger.resolve (idempotence: resolve(resolve(x))==resolve(x); order-independence of input permutations; tombstone dominance; LWW total ordering), BalanceTrendCalculator.buildAutoSeries (series length/reindex invariants, stagnation preservation, metric consistency), and backupsToDelete (never deletes newest 3, monotone in input size) — using a property-testing library added as a TEST-ONLY dependency (jqwik or kotest-property; pick what coexists cleanly with JUnit4).
LAYERS: [domain]
CHANGED_HINT: core/domain/src/test — alongside OperationMergerTest, BalanceTrendCalculatorTest, backup rotation tests; gradle/libs.versions.toml (test-only dep)
TEST_TYPES: unit
CONSTRAINTS: new dependency is test-only and needs a one-line user ack at implement time (stack versions are TDD-locked for production only); properties must use fixed seeds for reproducibility with seed logging on failure; existing example-based tests stay untouched
=== END SPEC ===

## Gap / context
Deterministic merge/trend/rotation logic is exactly where property tests find the
cases example tests never enumerate. Source: review item 13 (P3/M).

## Implementation links
- commit: `21d9ac19`, `efcdc029`, `be8897d9`, `0f697d18`
- files: `core/domain/build.gradle.kts`, `gradle/libs.versions.toml`, `core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/property/PropertyTestSupport.kt`, `core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/repository/BackupRotationPropertyTest.kt`, `core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/sync/OperationMergerPropertyTest.kt`, `core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/usecase/BalanceTrendCalculatorPropertyTest.kt`
