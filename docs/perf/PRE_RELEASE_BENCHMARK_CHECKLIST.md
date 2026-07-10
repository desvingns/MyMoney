# Pre-release benchmark checklist (local only)

Repeatable local procedure to check cold-start performance before cutting a release.
Benchmarks run **LOCALLY only** — never in CI (the project stays on free tiers, so the
CI budget is reserved for unit tests and lint, not device benchmarks).

## 1. Device gate

Run on the host AVD `Pixel_5_API_34` (Pixel 5, Android 14 / API 34). Confirm before measuring:

```bash
adb -s <serial> shell getprop ro.boot.qemu.avd_name   # Pixel_5_API_34
adb -s <serial> shell getprop ro.build.version.sdk     # 34
adb -s <serial> shell getprop sys.boot_completed       # 1
```

All three must match. If not, start/attach the AVD and retry — do not measure blind.

## 2. Regenerate the committed baseline profile

```bash
./gradlew :app:generateReleaseBaselineProfile
```

This refreshes `app/src/release/generated/baselineProfiles/baseline-prof.txt` (the committed
profile). `automaticGenerationDuringBuild = false`, so the profile is regenerated only when this
task is run explicitly, then committed.

## 3. Run the startup benchmark

```bash
./gradlew :macrobenchmark:connectedBenchmarkReleaseAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.kshavrin.mymoney.macrobenchmark.StartupBenchmark#coldStartupToDashboard
```

## 4. Where the numbers land

```
macrobenchmark/build/outputs/connected_android_test_additional_output/benchmarkRelease/connected/<device>/com.kshavrin.mymoney.macrobenchmark-benchmarkData.json
```

Read `timeToInitialDisplayMs` (median / min / max) from that JSON.

## 5. Verify the profile is actually packaged

```bash
unzip -l app/build/outputs/apk/release/app-release.apk | grep dexopt
```

Must show `assets/dexopt/baseline.prof`. If it is missing, the release APK ships without the
profile and startup numbers are invalid.

The `staging` build type now consumes the same committed release profile
(`android.sourceSets.getByName("staging").baselineProfiles.srcDir(...)`), and its
`matchingFallbacks = listOf("release")` lets the library modules resolve their release variant.
Verify staging packages the profile too:

```bash
./gradlew :app:assembleStaging --console=plain
unzip -l app/build/outputs/apk/staging/app-staging.apk | grep dexopt
```

Expected output (both entries present):

```
assets/dexopt/baseline.prof
assets/dexopt/baseline.profm
```

If `baseline.prof` is absent from the staging APK, the staging baselineProfiles srcDir wiring is
broken — fix the wiring in `app/build.gradle.kts`, not this checklist.

## 6. TDD §11 budget vs. emulator reality

- TDD §11 budget: **cold start <= 600 ms to first interactive** on a **PHYSICAL Pixel 5**, and
  **dashboard <= 16.6 ms/frame** (60 fps).
- **Emulator numbers are NOT comparable to that 600 ms budget.** An AVD on a shared host is slower
  and noisier than the physical target device. Treat emulator measurements only as a **before/after
  delta on the same AVD** — a relative regression guard, never an absolute pass/fail against §11.
- The §11 pass/fail verdict must come from a physical Pixel 5 run.

## Recorded baseline

- 2026-07-10, `Pixel_5(AVD) - 14` (`emulator-5554`, SDK 34), 10 iterations:
  `timeToInitialDisplayMs` **median 1199.3 ms** (min 1135.1, max 1858.8).
  Use this as the before-value when checking the deferred-init change delta on the same AVD.
