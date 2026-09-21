# Android 10/11 support — runtime compatibility audit
Epic: android-10-11-support
Order: 02 of 03
Status: done
Completed: 2026-09-20
Depends-on: android-10-11-support-01-sdk-floor-api29
Acceptance-matrix: sdk_band=29-30,31-32,33plus; haptics=enabled,disabled; vibrator=present,absent
Risk-signals: platform-api, haptics, lint-newapi
Date: 2026-08-27

## SPEC
=== SPEC ===
TASK: refactor
PLATFORM: android
WHAT: Make API-sensitive MyMoney runtime paths safe on API 29 and API 30, preserving current behaviour on API 31+ and retaining graceful no-op/fallback behaviour when a device lacks optional hardware or platform capability.
LAYERS: platform | presentation | test
CHANGED_HINT:
  - core/ui/src/main/java/com/kshavrin/mymoney/core/ui/haptic/HapticPlayer.kt:3-7,40,57-90 — remove the unguarded API 31 dependency from the API 29/30 execution path; keep the API 31/32 composition path and API 33+ celebratory branch, and provide a compatible legacy fallback for Android 10/11 (G15).
  - core/ui/src/test/java/com/kshavrin/mymoney/core/ui/haptic/HapticPlayerImplTest.kt:22-35,38-90 — replace the placeholder coverage with deterministic JVM tests over an extracted pure API-selection seam for disabled haptics, missing vibrator, API 29/30 legacy fallback, API 31/32 composition path, and API 33+ branch (G15).
  - core/ui/src/test/java/com/kshavrin/mymoney/core/ui/haptic/HapticPlayerContractTest.kt:17-22 — retain the enum/contract coverage and add the compatibility contract without introducing a mocking framework (G9, G15).
  - app/src/main/java/com/kshavrin/mymoney/ui/theme/Theme.kt:20 — preserve the API S dynamic-colour guard while verifying the API 29/30 theme path (G16).
  - feature/lockscreen/src/main/java/com/kshavrin/mymoney/feature/lockscreen/setup/BiometricSetupScreen.kt:334 — preserve the existing API R guard and verify API 29/30 behaviour (G16).
  - core/sync/src/main/java/com/kshavrin/mymoney/core/sync/notification/EntitlementNotifierImpl.kt:49 — preserve the API T notification-permission guard and verify pre-T behaviour (G16).
  - TDD/MyMoney/MyMoney_TDD.md:462-469,1033-1035,1463-1470 — revise behavioural claims that currently assume minSdk/API 31, documenting the API 29/30 haptic fallback and retaining the existing API 31+ paths (G17).
  - app/build.gradle.kts:402-467, core/*/build.gradle.kts, feature/*/build.gradle.kts, */src/main/AndroidManifest.xml — inspect dependency and merged-manifest minimums; change a dependency only when it demonstrably blocks API 29, and avoid unrelated upgrades (G7, G13).
TEST_TYPES: unit | contract | static-analysis | build | Pixel-5-connected-regression
CONSTRAINTS:
  - API 29/API 30 connected-device execution is deliberately outside the current test loop; the haptic contract must be proven through the extracted pure `HapticEffectSelector` API-selection seam, while the separate legacy order owns real-device confirmation (D5, G9, G10).
  - No direct access to API 31+ classes may occur before an API guard or a compatibility abstraction; lint must not report NewApi errors for supported paths (G15).
  - Preserve haptic enable/disable semantics, no-vibrator no-op behaviour, the API 31/32 composition path, the API 33+ branch, and the project's no-mocking/fakes-only testing rule (G9, G15).
  - Do not remove optional features solely to pass API 29; use a safe fallback or explicit capability gating.
  - Do not alter database schema, navigation, business rules, monetization, or UI design.
=== END SPEC ===

## Acceptance

Feature: Android 10/11 runtime compatibility
  Covers US-003, US-004. Source facts: G7, G13, G15, G16.

  @US-003 @haptics @android10 @android11
  Scenario: Haptics do not crash on Android 10 or Android 11
    Given the `HapticEffectSelector` unit test supplies SDK value 29 or 30
    And haptics are enabled
    When the application requests any supported haptic kind
    Then the request completes without a class-verification or missing-API crash
    And the legacy fallback is selected without invoking API 31 composition classes

  @US-003 @haptics @disabled
  Scenario: Disabled haptics remain silent on every supported API
    Given haptics are disabled in application settings
    When the application requests a haptic effect
    Then no vibration is emitted
    And no API-sensitive vibration path is invoked

  @US-004 @optional-capabilities
  Scenario: Optional platform capabilities degrade gracefully
    Given the `HapticEffectSelector` unit test supplies SDK value 29 or 30 and `hasVibrator` is false
    When the related feature is initialized or invoked
    Then the application remains usable
    And the optional effect is skipped or replaced by its documented fallback

  @US-004 @regression
  Scenario: Existing Android 12+ behaviour is preserved
    Given the current Pixel 5/API 34 regression device is healthy and boot-complete
    When the relevant unit, lint, build, and connected regression checks run
    Then the existing API 31+ behaviour remains green
    And no unrelated screen or business-rule regression is introduced

## Gap / context

Lowering the manifest floor exposes a concrete API 31 assumption in `HapticPlayerImpl`; the rest of
the platform-sensitive paths appear guarded but must be verified after the floor change.

## Implementation links

- commit: cf8024df, 56ad01ef
- files: core/ui/src/main/java/com/kshavrin/mymoney/core/ui/haptic/HapticEffectSelector.kt, core/ui/src/main/java/com/kshavrin/mymoney/core/ui/haptic/HapticPlayer.kt, core/ui/src/test/java/com/kshavrin/mymoney/core/ui/haptic/HapticPlayerImplTest.kt, core/ui/src/test/java/com/kshavrin/mymoney/core/ui/haptic/HapticPlayerContractTest.kt, TDD/MyMoney/MyMoney_TDD.md
