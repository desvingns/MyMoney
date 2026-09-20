# Android Support Matrix

## Active support targets

| Android version | API level | Status |
|---|---|---|
| Android 10 | 29 | Actively supported |
| Android 11 | 30 | Actively supported |
| Android 12 | 31 | Actively supported |
| Android 12L | 32 | Actively supported |
| Android 13 | 33 | Actively supported |
| Android 14 | 34 | Actively supported — current primary regression device |
| Android 15 | 35 | Actively supported |
| Android 16 | 36 | Actively supported |

`minSdk = 29`. All API levels 29 and above receive compatibility fixes on regression.

## Current primary regression device

**Pixel 5 / API 34 AVD** (`Pixel_5` or `Pixel_5_API_34`, SDK 34).

- Serial resolved by discovery — never hardcoded (see `AGENTS.md` §Emulator access and
  `docs/DEVICE_SETUP.md`).
- Connected runs use `scripts/run_connected_test_on_host_avd.ps1`.
- A green Pixel 5/API 34 run is **API 34 regression evidence only**. It does not validate
  Android 10 (API 29) or Android 11 (API 30) runtime behaviour.

## Legacy-device validation — separate follow-up order

Real API 29/API 30 device validation is tracked under the follow-up order
**`android-10-11-legacy-device-validation`**.

That order must define before any validated legacy-device claim can be made:

- API 29 and API 30 device images or physical devices to be used.
- Smoke-test coverage scope for Android 10 and Android 11.
- OEM-specific fixes and known compatibility constraints.
- Ownership and merge criteria.

Until that order is resolved, API 29/30 runtime behaviour is covered by source-level compatibility
guards (Kotlin `Build.VERSION.SDK_INT` branches, `@RequiresApi` boundaries) and JVM unit tests,
not by validated on-device runs.

## CI shape (current)

The current CI workflow (`.github/workflows/ci.yml`) contains:

- `lintDebug` and `testDebugUnitTest` — JVM job, runs on every push and pull request.
- API 34 connected module checks (`connected` job, needs `jvm`) — runs on the API 34 AVD using
  `reactivecircus/android-emulator-runner@v2` with `api-level: 34`.

An API 29/API 30 CI matrix is **not added in this SPEC**; it is part of the
`android-10-11-legacy-device-validation` follow-up order.
