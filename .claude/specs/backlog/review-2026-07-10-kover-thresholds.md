# Raise Kover coverage floors + publish HTML report from CI
Epic: review-2026-07
Order: 10 of 35
Status: draft
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Replace the symbolic 4% Kover floor with a real per-module ladder — measure current line coverage first, then set floors slightly below actuals and ratchet toward targets (:core:domain 80%+, :core:database 60%+, :core:datastore 60%+, feature modules 50%+) — and upload koverHtmlReport as a CI artifact for visual inspection.
LAYERS: [data]
CHANGED_HINT: root build.gradle.kts (kover config, lines ~62–75), .github/workflows/ci.yml (koverVerify already gated; add report artifact upload)
TEST_TYPES: unit
CONSTRAINTS: floors must be set from MEASURED coverage (never below-reality wishful numbers that instantly break CI); raising a floor never justifies weakening or deleting tests; exclude generated code (Hilt/Room/BuildConfig) from measurement
=== END SPEC ===

## Gap / context
A 4% floor on three modules catches nothing; coverage can halve without CI noticing.
Source: review items 9+19 (P2/M + P3/S).

## Implementation links
- commit: (pending)
- files: (pending)
