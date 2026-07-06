# Sentry Free-Tier Crash Reporting

This project uses Sentry for crash/error events only. Keep the Sentry account on the
free Developer plan and do not enable paid quota controls, replay, profiling, or
performance features.

## DSN injection

- Local builds: add one of these keys to the git-ignored `local.properties`:
  - `sentry.dsn=https://...`
  - `SENTRY_DSN=https://...`
- CI builds: create a GitHub Actions repository secret named `SENTRY_DSN`.
- If neither value is present, `BuildConfig.SENTRY_DSN` is blank and `MyMoneyApp`
  skips Sentry initialization.
- Never commit a DSN, `sentry.properties`, or a Sentry auth token.

Gradle resolves the DSN in this order: `-Psentry.dsn`, `local.properties`
`sentry.dsn`, `local.properties` `SENTRY_DSN`, environment `SENTRY_DSN`, blank.

## SDK mode

The Android SDK is initialized only when `BuildConfig.SENTRY_DSN` is non-blank.
The configuration is locked to error collection:

- `tracesSampleRate = 0.0`
- tracing disabled
- profiles sample rate `0.0`
- app-start profiling disabled
- activity lifecycle tracing disabled
- Performance V2 disabled
- frame tracking disabled
- auto session tracking disabled
- screenshots and view hierarchy attachments disabled
- no Session Replay dependency is included

Keep `libs.sentry.android.core`; do not replace it with the umbrella
`sentry-android` dependency.

## Manual Sentry setup

1. Create a new Sentry Android project for `com.kshavrin.mymoney`.
2. Do not reuse the DSN from the original Monefy APK.
3. Copy the project DSN from Project Settings -> Client Keys (DSN).
4. Add the DSN to local `local.properties` and to GitHub Actions secret
   `SENTRY_DSN`.
5. Keep the subscription on Developer/free and do not add pay-as-you-go budget.
6. Do not enable or rely on Spike Protection, per-DSN rate limits,
   Delete & Discard, or any other quota guard that requires a paid plan.

## Quota guard

The free Developer plan budget is 5,000 accepted errors per month for this
project. Sentry's Usage Stats page shows accepted, dropped, and filtered events;
only accepted events count toward quota. Sentry sends quota notification emails
to organization Owners when usage approaches or exceeds the quota. Per-DSN rate
limits and Delete & Discard are Business/Enterprise features, so do not use them
for this project.

Operational rule:

- Check Stats -> Usage after every release and whenever an Owner receives a quota
  notification.
- If accepted errors reach 4,500 in the current month, remove or blank the
  GitHub `SENTRY_DSN` secret before the next CI build and fix the noisy issue
  before restoring it.
- If a flood happens locally, remove the local `sentry.dsn` / `SENTRY_DSN` entry
  from `local.properties` until the issue is fixed.
- Do not increase quota or upgrade plan to keep crash reporting active.

References:

- https://docs.sentry.io/pricing/quotas/
- https://docs.sentry.io/pricing/quotas/manage-event-stream-guide/
