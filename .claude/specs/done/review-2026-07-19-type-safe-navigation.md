# Migrate to type-safe navigation routes (@Serializable)
Epic: review-2026-07
Order: 19 of 35
Status: done
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Migrate navigation from string route constants + concatenation ("${Destinations.TRANSACTION_DETAIL}/$id" and NavType argument parsing) to navigation-compose 2.8.4 type-safe routes: @Serializable route classes/objects per destination, typed navigate(...) calls, and toRoute<T>() argument extraction — screen by screen, keeping deep-link behavior (app shortcuts!) and back-stack semantics identical.
LAYERS: [presentation]
CHANGED_HINT: :app navigation host + Destinations object, per-feature navigation call sites (lines using route concatenation), app shortcuts intent handling
TEST_TYPES: unit [compose-ui]
CONSTRAINTS: pure refactor — zero user-visible behavior change; existing navigation/instrumented tests must stay green and be updated per the stale-test rule (never weakened); kotlinx.serialization is already in the stack; migrate incrementally (hybrid state is acceptable mid-SPEC, not at the end)
=== END SPEC ===

## Gap / context
Stringly routes defer argument mistakes to runtime; the library already supports
compile-time-safe routes at the pinned version. Source: review item 47 (P2/M).

## Implementation links
- commit: f7956d4c, b8ca1ab4, 3dde2778, 58d2b1fb, 3b35a36a, 59ebf5a, 49c3b368
- files: app navigation, core/ui navigation contract, eight typed-argument ViewModels, navigation unit tests, and TypedNavigationDeviceTest
