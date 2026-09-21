# Android 10/11 support — Pixel 5 regression and support policy
Epic: android-10-11-support
Order: 03 of 03
Status: done
Completed: 2026-09-20
Depends-on: android-10-11-support-01-sdk-floor-api29, android-10-11-support-02-runtime-compatibility
Acceptance-matrix: platform_api=29,30,34; artifact=agents_md,support_matrix_doc,mp_extras,contract_test
Risk-signals: documentation, contract-tests
Date: 2026-08-27

## SPEC
=== SPEC ===
TASK: refactor
PLATFORM: android
WHAT: Make the support promise explicit: Android 10 and Android 11 are actively supported, Pixel 5/API 34 is the current primary regression device, and real API 29/API 30 validation plus legacy-specific fixes are tracked as a separate follow-up order.
LAYERS: test | documentation | tooling
CHANGED_HINT:
  - AGENTS.md:54,124-152 — replace the stale minimum-SDK wording with API 29 and active Android 10/11 support while preserving the discovered Pixel 5/API 34 gate and non-hardcoded serial rule (G1, G10).
  - .claude/mp/extras/mp-developer-android.md:48 — update developer guidance from minSdk 31 to the API 29 policy without changing the compile/target SDK contract (G18).
  - .claude/mp/extras/mp-tester-android.md:10,59 — distinguish active API 29/30 support from the current API 34 connected-test gate; do not require API 29/30 devices in the current loop (G9, G10, G18).
  - docs/ANDROID_SUPPORT_MATRIX.md (new) — record API 29/API 30 as active support targets, Pixel 5/API 34 as current primary verification, and the separate legacy validation/fix order (assumption, grounded by G9–G12).
  - app/src/test/java/com/kshavrin/mymoney/AndroidSupportPolicyContractTest.kt (new) — pin the support-policy text/contract and prevent drift back to minSdk 31 or an implied API 29/30 device gate (assumption, grounded by G1, G10, G18).
  - .github/workflows/ci.yml:76,448-473 — verify that the existing JVM and API 34 connected jobs remain the current CI shape; do not add an API 29/30 matrix in this SPEC (G12).
TEST_TYPES: unit | contract | lint | build | Pixel-5-connected-regression
CONSTRAINTS:
  - “Actively supported” means Android 10/API 29 and Android 11/API 30 are valid product targets and receive compatibility fixes; it does not mean that every current change is blocked on a legacy device run.
  - Current connected verification must use the documented discovered Pixel 5/API 34 AVD and the existing helper; never hardcode a serial (G10, G11).
  - A green Pixel 5/API 34 run must be reported as API 34 regression evidence only; it must not be presented as API 29/API 30 validation.
  - The follow-up order is explicitly named `android-10-11-legacy-device-validation`; it must define API 29 and API 30 devices/images, smoke coverage, OEM-specific fixes, and ownership before claiming validated legacy-device runtime behaviour (D5, O1).
  - Keep the existing CI shape and do not weaken failure/error/skipped handling (G11, G12).
  - SPEC 01 and this SPEC both touch `AGENTS.md`; run them sequentially and reconcile the final wording once.
=== END SPEC ===

## Acceptance

Feature: Active Android 10/11 support policy and current verification gate
  Covers US-005, US-006. Source facts: G9–G12, G18.

  @US-005 @policy
  Scenario: Android 10 and Android 11 are documented as active targets
    Given the project support matrix and developer guidance are current
    When a maintainer checks the supported Android versions
    Then Android 10/API 29 and Android 11/API 30 are listed as actively supported
    And Android 12 and newer remain supported

  @US-006 @pixel5
  Scenario: Current regression testing remains on Pixel 5/API 34
    Given a connected Pixel 5/API 34 AVD is discovered and boot-complete
    When the current connected verification suite is run through the documented helper
    Then the suite uses the discovered serial
    And success requires zero failures, errors, and skipped tests

  @US-006 @scope
  Scenario: Legacy-device testing is explicitly separate
    Given the current Pixel 5/API 34 regression loop is green
    When the support documentation describes verification status
    Then it states that API 29/API 30 device runs and legacy-specific fixes are a separate follow-up order
    And it does not claim that API 34 evidence validates Android 10 or Android 11 runtime behaviour

  @US-005 @ci
  Scenario: CI does not silently lose the existing gates
    Given the CI workflow is evaluated after the support-policy change
    When JVM and connected jobs are inspected
    Then `lintDebug` and `testDebugUnitTest` remain present
    And the existing API 34 connected module checks remain present

## Gap / context

The repository currently mixes a minSdk 31 policy with API 34-only connected verification. This SPEC
separates the product promise from the current test-device scope and creates an explicit handoff for
legacy-device validation.

## Implementation links

- commit: 616f2d3e
- files: AGENTS.md, .claude/mp/extras/mp-tester-android.md, docs/ANDROID_SUPPORT_MATRIX.md, app/src/test/java/com/kshavrin/mymoney/AndroidSupportPolicyContractTest.kt
