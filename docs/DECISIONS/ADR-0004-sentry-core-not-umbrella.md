# ADR-0004: Sentry dependency uses sentry-android-core, not umbrella sentry-android

- Status: Accepted
- Date: 2026-05-26

## Context

Retrospective record (2026-07-17) from PROGRESS.md decision-log line 78, PHASE_15 release
notes, commit range associated with the PHASE_15 R8 release build.

The TDD lists crash-free sessions ≥ 99.5 % as a success metric (line 99) and specifies
Sentry for crash reporting (§2.4, line 237; §2.6, lines 257–259). The dependency inventory
at §8 names `io.sentry:sentry-android`. The release APK size budget is ≤ 15 MB (§8.5,
line 2092).

Building the release APK with the umbrella `sentry-android` artifact — which bundles native
crash capturing and session replay integrations — produced an R8-minified APK of 16.87 MB,
exceeding the budget. Neither native crash capturing nor session replay is required by the
TDD for v1.0.

## Decision

Use `io.sentry:sentry-android-core` in `:app` and the base `io.sentry:sentry` artifact in
`:core:sync`, instead of the umbrella `io.sentry:sentry-android`. This excludes the native
crash and session-replay integrations that are not specified by the TDD and that inflate the
binary.

The resulting R8-minified release APK measures 12.35 MB, within the ≤ 15 MB budget.

## Rejected alternatives

- Umbrella `io.sentry:sentry-android` (TDD §8 inventory value): rejected because native
  crash capture and session replay are not required by the TDD and pushing the release APK
  to 16.87 MB violates the §8.5 size budget of ≤ 15 MB.

## Consequences

- Native crash symbolication via the Sentry native SDK is not active. Kotlin/JVM crashes
  are reported via the `sentry-android-core` JVM integration as specified.
- If a future requirement adds native crash capture or session replay, this decision must
  be revisited with an updated APK-size measurement and a budget amendment.
