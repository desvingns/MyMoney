# ADR: crash reporting (OQ-10) + Remote Config (OQ-5) on free tiers
Epic: review-2026-07
Order: 21 of 35
Status: done
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Close the two dangling service decisions with one ADR: OQ-10 — commit to Sentry (free Developer plan, errors-only, code already wired; Crashlytics/Spark documented as the unlimited-free fallback if 5k errors/mo ever becomes tight) vs Crashlytics; OQ-5 — decide whether Firebase Remote Config (free on Spark) is wanted for v1.1 feature flags or explicitly dropped from scope; record consequences for the CI secret set and Firebase config materialization step.
LAYERS: [domain]
CHANGED_HINT: new docs/DECISIONS/ADR-*.md; read .github/workflows/ci.yml Firebase conditional step and PROGRESS.md OQ table for context
TEST_TYPES: unit
CONSTRAINTS: FREE TIER is a hard requirement of the decision matrix (see .ai/memory/MEMORY.md); decision itself is user-gated — prepare the comparison, ask, then record; no SDK wiring changes in this SPEC
=== END SPEC ===

## Gap / context
OQ-5/OQ-10 deferred-to-v1.1 markers keep ambiguity alive in every planning pass.
Source: review item 5 (P2/S).

## Implementation links
- commit: ff307d5e
- files: docs/DECISIONS/ADR-0008-crash-reporting-and-remote-config-scope.md
- decisions: OQ-10 = Sentry-only (errors-only, free Developer plan; Crashlytics/Spark documented as the fallback if the 5k errors/mo cap becomes tight); OQ-5 = keep Firebase Remote Config gated-OFF scaffolding, deferred to v1.1 (feature flags + min_supported_version_code=1). No SDK wiring changes.
