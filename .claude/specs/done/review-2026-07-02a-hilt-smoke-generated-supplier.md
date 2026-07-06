# Fix Hilt generated supplier missing in device smoke
Epic: review-2026-07
Order: 02a of 35
Status: done
Depends-on: review-2026-07-02-slice5-release-qa
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: bugfix
WHAT: Repair the connected `MainActivityLaunchTest` Hilt test packaging/generation path so the sanctioned device smoke can run on Pixel_5_API_34 before Slice 5 manual QA/macrobenchmark work resumes. The failing evidence is `app/build/outputs/androidTest-results/connected/debug/TEST-Pixel_5(AVD) - 14-_app-.xml`: `MainActivityLaunchTest_TestComponentDataSupplier` is missing from the test APK. Confirm the fix by running `scripts/run_connected_test_on_host_avd.ps1 -TestClass 'com.kshavrin.mymoney.MainActivityLaunchTest'` and requiring `tests=1`, `failures=0`, `errors=0`, `skipped=0`.
LAYERS: [presentation]
CHANGED_HINT: app/src/androidTest/java/com/kshavrin/mymoney/MainActivityLaunchTest.kt, app/build.gradle.kts, scripts/run_connected_test_on_host_avd.ps1
TEST_TYPES: [instrumented-compose-ui]
CONSTRAINTS: hard device gate; do not weaken or remove the smoke assertion; no product UI changes unless the root cause proves a real startup defect; keep fakes-only/no mocks; record exact device evidence and XML report path
=== END SPEC ===

## Gap / context
Slice 5 release QA was attempted on 2026-07-06 with `emulator-5554` booted on SDK 34. The connected smoke failed before Activity launch because Hilt could not load the generated test component data supplier, blocking manual QA, clean release walk, and macrobenchmark execution.

## Implementation links
- commit: 2a697cc9
- files: app/build.gradle.kts
- verification: `scripts/run_connected_test_on_host_avd.ps1 -TestClass 'com.kshavrin.mymoney.MainActivityLaunchTest'` passed on `Pixel_5(AVD) - 14`; XML `app/build/outputs/androidTest-results/connected/debug/TEST-Pixel_5(AVD) - 14-_app-.xml` reports tests=1, failures=0, errors=0, skipped=0.
