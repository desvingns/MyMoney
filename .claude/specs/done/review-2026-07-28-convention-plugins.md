# build-logic convention plugins for the 16-module build
Epic: review-2026-07
Order: 28 of 35
Status: done
Depends-on: review-2026-07-27
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Introduce build-logic/ convention plugins (Now-in-Android pattern): mymoney.android.library, mymoney.android.feature (library + Hilt + Compose + common test deps), mymoney.jvm.library, mymoney.android.application — migrating all modules' build.gradle.kts onto them, which also unifies jvmToolchain/compileOptions in ONE place (today only :core:common and :core:domain declare jvmToolchain(17)).
LAYERS: [data]
CHANGED_HINT: new build-logic/ included build, settings.gradle.kts, every module's build.gradle.kts, gradle/libs.versions.toml plugin aliases
TEST_TYPES: unit
CONSTRAINTS: byte-for-byte equivalent build outputs (same dependencies/flags per module — verify via ./gradlew :app:dependencies diff before/after); migrate module-by-module in reviewable steps; keep configuration-cache compatibility from SPEC 27; version catalog stays the single version source
=== END SPEC ===

## Gap / context
16 hand-maintained near-identical build files drift (jvmToolchain already has);
convention plugins pin the conventions. Source: review item 17 (P3/M).

## Implementation links
- commits: e06e9e3f (convention plugins + module migration), 50a01381 (restore feature Compose
  convention), 41aa5b36 (Compose plugin on the build-logic classpath), 330caa8c (test fixtures),
  plus the close-out commits below.
- files: build-logic/settings.gradle.kts; build-logic/build.gradle.kts;
  build-logic/src/main/kotlin/com/kshavrin/mymoney/buildlogic/MymoneyConventionPlugins.kt;
  build-logic/src/test/kotlin/com/kshavrin/mymoney/buildlogic/MymoneyConventionPluginsTest.kt;
  build-logic/src/test/kotlin/com/kshavrin/mymoney/buildlogic/MymoneyBuildConfigurationContractTest.kt;
  settings.gradle.kts; gradle/libs.versions.toml; app/build.gradle.kts; core/*/build.gradle.kts (10);
  feature/*/build.gradle.kts (8); macrobenchmark/build.gradle.kts

## Verification (2026-07-26)

Equivalence constraint proven mechanically rather than by inspection — HEAD compared against the
pre-migration commit `a4e41e06` in a `git worktree`, same commands on both checkouts:

- **Dependency graph: 0 differences.** `<module>:dependencies` for all 19 modules on both trees,
  compared per project × per configuration as sets of resolved `group:artifact:version`
  (ordering/tree shape ignored, AGP `_internal-*` configurations excluded):
  2636 configurations / 32878 coordinate entries on each side, identical.
  Dumps must be taken with `--max-workers=1 -Dorg.gradle.parallel=false`; parallel execution
  interleaves the per-project reports and makes them uncomparable.
- **Unit tests + lint green:** `lintDebug testDebugUnitTest --continue` → BUILD SUCCESSFUL,
  1631 tests / 0 failures / 0 errors (counted from `TEST-*.xml`, not from console text).
- **build-logic tests green:** `--project-dir build-logic test` → 9 tests / 0 failures
  (4 configuration-contract + 5 TestKit convention fixtures).
- **`:app:assembleDebug`** → BUILD SUCCESSFUL; configuration cache entry stored, 0 problems
  (SPEC 27 compatibility preserved).
- **detekt / ktlint / koverVerify:** failing-task sets identical to `a4e41e06` — 48 detekt issues
  on both sides, same ktlint task set, and Kover line coverage equal to four decimal places
  (`:feature:onboarding` 12.9353, `:feature:lockscreen` 30.8197, `:feature:transactionslist`
  34.7360, `:feature:cloudsync` 36.2963). Identical coverage numbers are themselves strong evidence
  that the emitted bytecode did not change. These four Kover floors, the detekt findings and the
  ktlint findings are **pre-existing red on `main`**, not caused by this SPEC.
- **One real regression found and fixed here:** `:macrobenchmark:ktlintKotlinScriptCheck` failed at
  HEAD but not at `a4e41e06` — a blank line left before `}` in `macrobenchmark/build.gradle.kts`
  after the `compileOptions` block was removed. After the fix the ktlint failing-task set matches
  the baseline exactly.

## Follow-ups (not done here — out of this SPEC's scope)

- `build-logic`'s 9 unit tests are an included build, so `.github/workflows/ci.yml` never runs them
  (`./gradlew test` at the root does not reach it). They need an explicit
  `./gradlew --project-dir build-logic test` step or they will silently rot.
- CI has been red on `main` since ~2026-07-20 at the "Run lint and all JVM unit tests" step, which
  skips the detekt/ktlint/kover steps behind it. The same command is green on the Windows host, so
  the failure is environment-specific and still undiagnosed.
