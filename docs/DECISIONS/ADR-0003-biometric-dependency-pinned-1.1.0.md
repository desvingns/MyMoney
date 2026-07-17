# ADR-0003: Biometric dependency pinned to androidx.biometric 1.1.0

- Status: Accepted
- Date: 2026-05-26

## Context

Retrospective record (2026-07-17) from PROGRESS.md decision-log line 73, commit 60cfb4a.

PHASE_14 implements the S16 biometric lock setup screen and the `BiometricLockOverlay`
rendered by `MainActivity` above the `NavHost` (TDD §4.15, lines 921–947). The phase
file specified `androidx.biometric:1.2.0-alpha07`. That version does not exist on Google
Maven; the 1.2.0 development line stops at alpha05. Building with the specified coordinate
would fail at dependency resolution.

`BiometricPrompt` is referenced in the component inventory at TDD line 1394. The 1.1.0
stable release provides all APIs required: `BiometricManager`, `BIOMETRIC_STRONG`,
`BiometricPrompt`, and `FragmentActivity` hosting.

## Decision

Pin the biometric dependency to `androidx.biometric:biometric:1.1.0` (stable) in place of
the phase file's non-existent `1.2.0-alpha07`.

## Rejected alternatives

- `androidx.biometric:1.2.0-alpha07` (phase file's stated version): rejected because the
  artifact does not exist on Google Maven; dependency resolution fails at build time.

## Consequences

- S16 setup screen and the lock overlay compile and function against the stable 1.1.0 API.
- If a future 1.2.0 stable release adds a required API, the version pin should be revisited
  in a separate decision at that time.
