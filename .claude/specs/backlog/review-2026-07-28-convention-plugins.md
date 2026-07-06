# build-logic convention plugins for the 16-module build
Epic: review-2026-07
Order: 28 of 35
Status: draft
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
- commit: (pending)
- files: (pending)
