# Android 10/11 support — epic overview
Epic: android-10-11-support
Order: 00 of 03
Status: done
Completed: 2026-09-20
Depends-on: —
Date: 2026-08-27

## Goal

MyMoney must actively support Android 10 and Android 11 in addition to newer Android versions. The
minimum platform contract becomes API 29, while `compileSdk` and `targetSdk` remain 36. The existing
Pixel 5/API 34 remains the current primary development and regression device; real API 29/API 30
device validation and any device-specific fixes are explicitly scheduled as a separate follow-up
order.

This epic changes compatibility policy and protects the known API-sensitive runtime paths. It does
not redesign the UI, change the database schema, lower the target SDK, or claim that API 34 testing
proves API 29/API 30 behaviour.

The generic platform fallback needed for the current haptic implementation is part of SPEC 02.
OEM-specific behaviour discovered only on particular Huawei/Xiaomi firmware remains outside this
epic and belongs to the separate follow-up order `android-10-11-legacy-device-validation`, owned by
the MP Dev maintainer after this epic is implemented.

## Locked decisions

- Android 10/API 29 is the new minimum supported platform; Android 11/API 30 is also actively supported.
- `compileSdk = 36` and `targetSdk = 36` remain unchanged.
- The single version catalog remains the source of the minimum SDK for app, Android libraries, and Android tests.
- Pixel 5/API 34 remains the current mandatory connected-test gate, with serial discovery and boot checks unchanged.
- API 29/API 30 connected testing and legacy-device fixes are a separate follow-up order, not a replacement for the current Pixel 5 regression loop.
- The follow-up order `android-10-11-legacy-device-validation` runs after this epic; it owns real API 29/API 30 device validation and OEM-specific fixes (assumption: MP Dev maintainer owns the order).
- No UI, business-rule, persistence-schema, monetization, or integration redesign is included.

## SPECs (run via `$mp --feature --next` in Order)

| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `android-10-11-support-01-sdk-floor-api29.md` | — | platform, test, documentation | Lower the canonical minimum API to 29 and align contracts/source documents. |
| 02 | `android-10-11-support-02-runtime-compatibility.md` | 01 | platform, presentation, test | Remove API 31 assumptions from runtime paths, especially haptics, and audit dependencies/manifests. |
| 03 | `android-10-11-support-03-pixel5-regression-policy.md` | 01, 02 | test, documentation, tooling | Record active support policy and preserve Pixel 5/API 34 as the current gate while queuing legacy validation separately. |

## Why this ordering

SPEC 01 changes the platform contract first, so all convention plugins and contract tests agree on
API 29 before runtime code is reviewed. SPEC 02 then handles actual API-level behaviour; it is
separate because the haptic implementation currently calls API 31 classes directly. SPEC 03 closes
the operational/documentation gap after the code contract is stable.

SPEC 01 and SPEC 03 both touch `AGENTS.md` and related support-policy wording, so they are strictly
sequenced. SPEC 02 owns the haptic implementation and its tests and has no planned same-file clash
with the other SPECs.

## Key facts (verified)

- G1: the version catalog currently declares `androidMinSdk = "31"` — `D:/Pet/MyMoney/gradle/libs.versions.toml:2-6`.
- G2–G4: app, library, and test convention plugins consume the catalog SDK values — `D:/Pet/MyMoney/build-logic/src/main/kotlin/com/kshavrin/mymoney/buildlogic/MymoneyConventionPlugins.kt:17-30`, `:60-90`.
- G5–G6: build-logic contract tests hardcode the current minimum as 31 — `D:/Pet/MyMoney/build-logic/src/test/kotlin/com/kshavrin/mymoney/buildlogic/MymoneyBuildConfigurationContractTest.kt:49-71`, `D:/Pet/MyMoney/build-logic/src/test/kotlin/com/kshavrin/mymoney/buildlogic/MymoneyConventionPluginsTest.kt:42-146`.
- G7: the project carries multiple AndroidX/Google/SDK dependencies that need a minSdk/runtime review — `D:/Pet/MyMoney/app/build.gradle.kts:402-467`, `D:/Pet/MyMoney/gradle/libs.versions.toml:63-160`.
- G10–G12: the current device and CI gates are API 34 oriented — `D:/Pet/MyMoney/AGENTS.md:124-152`, `D:/Pet/MyMoney/.github/workflows/ci.yml:76`, `:448-473`.
- G14: the existing APK is multi-ABI, so ABI filtering is not the primary compatibility issue — `D:/Pet/MyMoney/app/build/outputs/apk/debug/output-metadata.json:7-20` and local APK inspection.
- G15: haptic code directly uses `VibratorManager` and `VibrationEffect.Composition` — `D:/Pet/MyMoney/core/ui/src/main/java/com/kshavrin/mymoney/core/ui/haptic/HapticPlayer.kt:3-7`, `:40`, `:57-90`.
- G17–G18: the authoritative TDD and MP guidance still contain API 31/minSdk 31 statements that must be reconciled — `D:/Pet/MyMoney/TDD/MyMoney/MyMoney_TDD.md:22`, `:462-469`, `:1033-1035`, `:1463-1470`, `:2004-2006`, `:2112-2115`, `D:/Pet/MyMoney/.claude/mp/extras/mp-developer-android.md:48`, `D:/Pet/MyMoney/.claude/mp/extras/mp-tester-android.md:10`.

## Implementation links

- commit: 837730f9, 9bc131d7, 8bfdc517, cf8024df, 56ad01ef, 616f2d3e
- files: see the three SPEC files in done/
