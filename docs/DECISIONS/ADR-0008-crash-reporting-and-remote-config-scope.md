# ADR-0008: Crash reporting commits to Sentry; Remote Config deferred to v1.1

- Status: Accepted
- Date: 2026-07-17

## Context

Two open questions remained after the PHASE_15 release build:

**OQ-10 — crash-reporting service selection.** The TDD specifies Sentry for crash
reporting (§2.4 line 237; §2.6 lines 257–259; §9.5 crash-free sessions ≥ 99.5 %).
ADR-0004 fixed the dependency choice (`sentry-android-core` not the umbrella artifact),
but left the *service commitment* — Sentry vs. Firebase Crashlytics — unresolved.
Both satisfy the free-tier hard constraint: Sentry's Developer plan allows 5 000 error
events per month; Crashlytics is unlimited on Firebase Spark. The codebase is already
Sentry-only (`sentry-android-core` in `:app`, base `sentry` in `:core:sync`).

**OQ-5 — Firebase Remote Config for feature flags.** TDD §2.6 references Remote Config
as a mechanism for server-side feature flags and a minimum supported version gate.
`:core:sync` contains gated-OFF Remote Config scaffolding added in PHASE_13. Whether
to activate it in v1.0 or defer was left open. CI already handles Firebase
materialization conditionally: when `GOOGLE_SERVICES_JSON` is present, `app/google-services.json`
is written and `-Pfirebase.enabled=true` is appended to Gradle invocations (OQ-9).

A hard project constraint applies to both: free-tier only; no paid plan may be proposed
or adopted (see `.ai/memory/free-tier-external-services.md`).

## Decision

**OQ-10 — Sentry only.** Commit to the Sentry Developer (free) plan as the sole
crash-reporting service for v1.0. Errors-only reporting; 5 000 error events per month
cap. No SDK wiring change — the existing `sentry-android-core` / `sentry` dependency
set (ADR-0004) is sufficient and unchanged.

Firebase Crashlytics (Spark plan, unlimited free) is documented as the opt-in fallback
if the 5 000 events/month cap proves consistently too tight in production. The trigger
for reconsidering: sustained production usage that regularly saturates the monthly cap.
At that point a new ADR must be filed, the Sentry dependencies replaced with the
Firebase Crashlytics SDK, and `google-services.json` scoped to include Crashlytics.

**OQ-5 — Remote Config kept, deferred to v1.1.** The gated-OFF Remote Config scaffolding
in `:core:sync` (PHASE_13) is retained as-is. Live use is out of v1.0 scope.
Planned v1.1 scope: server-side feature flags and a `min_supported_version_code` key
with initial value 1. The existing CI Firebase secret path is retained and not removed.

## Rejected alternatives

**OQ-10:**
- Adopting Firebase Crashlytics immediately: rejected because the current Sentry
  integration already satisfies TDD §9.5 at zero additional setup cost, and introducing
  a second SDK for the same concern adds unnecessary binary weight and CI secret surface.
  Crashlytics remains a documented contingency, not a current dependency.
- Running both Sentry and Crashlytics simultaneously: rejected as redundant; doubles
  the SDK contribution for identical coverage and complicates the OQ-1 secret set.

**OQ-5:**
- Removing the Remote Config scaffolding entirely: rejected because TDD §2.6 references
  it as a planned mechanism and PHASE_13 already established the gated path. Removal
  would force re-implementation in v1.1 with higher cost and no v1.0 benefit.
- Activating Remote Config in v1.0: rejected because no v1.0 feature requires
  server-side control. Activation would make the `GOOGLE_SERVICES_JSON` secret
  mandatory in all CI runs before any demonstrated need.

## Consequences

**CI secret set (as of 2026-07-17):**

| Secret | Purpose | Status |
|---|---|---|
| `SENTRY_DSN` | Sentry error reporting | Optional; OQ-1 still open. When absent, Sentry is disabled in CI builds. |
| `GOOGLE_SERVICES_JSON` | Firebase (Remote Config) | Optional; OQ-9 still open. When present, Firebase is enabled via `-Pfirebase.enabled=true`. The step is NOT removed because OQ-5 resolves as "keep and defer", not "drop". |

No Crashlytics-specific secret is added; Crashlytics is a documented fallback only.

**Free-tier compliance:** Sentry Developer plan — 5 000 errors/month, 0 USD. Firebase
Spark Remote Config — unlimited remote config keys/fetches within Spark limits, 0 USD.
Both decisions remain within the hard free-tier constraint.

**APK budget:** No SDK additions result from either decision. The release APK measured
at 12.35 MB (ADR-0004) is unaffected.

**Fallback trigger for Crashlytics:** If sustained production error volume consistently
saturates the 5 000 events/month Sentry cap, migrate to Crashlytics. Migration requires:
a new ADR, replacing `sentry-android-core`/`sentry` with the Crashlytics SDK, and
scoping `google-services.json` to include Crashlytics. Do not add Crashlytics silently
without a recorded decision.

**v1.1 Remote Config scope:** When OQ-9 is resolved, add `GOOGLE_SERVICES_JSON` to
repository secrets and configure the Firebase project with Remote Config. Initial
keys: feature flags as needed at that time, plus `min_supported_version_code = 1`.

## Cross-references

- OQ-10, OQ-5: open questions closed by this ADR; see `docs/implementation_plan/PROGRESS.md`
  OQ table for their original entries.
- ADR-0004 (`docs/DECISIONS/ADR-0004-sentry-core-not-umbrella.md`): dependency-level
  complement to this service-commitment decision.
- TDD §2.4 line 237; §2.6 lines 257–259; §9.5: Sentry specification and crash-free-sessions
  success metric.
- PHASE_13: Remote Config scaffolding in `:core:sync` (gated OFF; retained).
- PHASE_15: release build; R8 APK size budget ≤ 15 MB; baseline 12.35 MB.
- `.github/workflows/ci.yml`: conditional Sentry DSN and Firebase materialization steps
  referenced in the CI secret table above.
