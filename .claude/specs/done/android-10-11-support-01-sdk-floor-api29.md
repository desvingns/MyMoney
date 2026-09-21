# Android 10/11 support — lower SDK floor to API 29
Epic: android-10-11-support
Order: 01 of 03
Status: done
Completed: 2026-09-20
Depends-on: —
Acceptance-matrix: convention_plugin=app,library,test; sdk_value=min29,compile36,target36
Risk-signals: build-config, contract-tests
Date: 2026-08-27

## SPEC
=== SPEC ===
TASK: refactor
PLATFORM: android
WHAT: Change MyMoney's single minimum Android API from 31 to 29 so Android 10 and Android 11 are installable and part of the active support policy, while keeping compile and target SDK at 36.
LAYERS: platform | test | documentation
CHANGED_HINT:
  - gradle/libs.versions.toml:2-6 — set `androidMinSdk = "29"`; keep `androidCompileSdk = "36"` and `androidTargetSdk = "36"` unchanged (G1).
  - build-logic/src/main/kotlin/com/kshavrin/mymoney/buildlogic/MymoneyConventionPlugins.kt:22-27,65-69,81-86 — preserve catalog-driven SDK wiring and do not add module-local overrides (G2, G3, G4).
  - build-logic/src/test/kotlin/com/kshavrin/mymoney/buildlogic/MymoneyBuildConfigurationContractTest.kt:49-71 — update the expected minimum to 29 and retain the single-source-of-truth assertions (G5).
  - build-logic/src/test/kotlin/com/kshavrin/mymoney/buildlogic/MymoneyConventionPluginsTest.kt:42-146,244-249 — update application, library, and test convention expectations to 29 (G6).
  - TDD/MyMoney/MyMoney_TDD.md:22,2004-2006,2115 — reconcile the authoritative SDK tables with the new API 29 policy; API-sensitive behavioural wording is owned by SPEC 02 (G17).
  - AGENTS.md:54 — update only the project SDK cheatsheet minimum from `minSdk: 31` to `minSdk: 29`; active-support and device-policy wording is owned by SPEC 03 (G1, G10).
TEST_TYPES: unit | contract | build | static-analysis
CONSTRAINTS:
  - `:app` must not declare its own SDK values; the catalog and convention plugins remain canonical (G2, G5).
  - Do not lower `compileSdk` or `targetSdk`; this SPEC changes only the minimum supported API.
  - Do not claim old-device runtime validation from a successful Pixel 5/API 34 run; legacy validation is owned by SPEC 03's separate follow-up order.
  - Any generated build output is verification evidence only and must not be committed as a source change.
=== END SPEC ===

## Acceptance

Feature: Android 10 and Android 11 minimum platform contract
  Covers US-001, US-002. Source facts: G1–G6, G17.

  @US-001 @platform
  Scenario: Android 10 is included in the declared support floor
    Given the project uses the canonical Android version catalog
    When the application and Android library/test convention plugins resolve their SDK values
    Then the minimum SDK is API 29
    And Android 10 and Android 11 are within the declared compatibility range

  @US-001 @regression
  Scenario: Newer platform targets remain unchanged
    Given the minimum SDK is API 29
    When the Android build configuration is evaluated
    Then compile SDK remains 36
    And target SDK remains 36

  @US-002 @contract
  Scenario: No module-local SDK override is introduced
    Given the project-wide SDK contract tests are executed
    When the build configuration is inspected
    Then all Android modules obtain the minimum from the version catalog
    And the application build file contains no direct minimum-SDK override

  @US-002 @contract
  Scenario: All convention plugins expose the new minimum
    Given the application, library, and test convention contract suites are executed
    When each convention plugin resolves its Android SDK values
    Then each plugin reports minimum SDK API 29
    And no module-local SDK override is required

## Gap / context

The current APK and all Android modules declare a minimum of API 31, which excludes the user's
Android 10/API 29 and Android 11/API 30 devices even though the application has no ABI restriction.

## Implementation links

- commit: 837730f9, 9bc131d7, 8bfdc517
- files: gradle/libs.versions.toml, build-logic/src/test/kotlin/com/kshavrin/mymoney/buildlogic/MymoneyBuildConfigurationContractTest.kt, build-logic/src/test/kotlin/com/kshavrin/mymoney/buildlogic/MymoneyConventionPluginsTest.kt, TDD/MyMoney/MyMoney_TDD.md, AGENTS.md, .claude/mp/extras/mp-developer-android.md
