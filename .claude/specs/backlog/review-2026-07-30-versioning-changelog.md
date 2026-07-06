# Tag-based versioning + CHANGELOG.md
Epic: review-2026-07
Order: 30 of 35
Status: draft
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Replace hardcoded versionCode=1/versionName="1.0" with git-derived versioning (versionName from the latest tag, versionCode from a monotonic scheme — tag-count or CI run number — with a documented local fallback so IDE builds still work offline), tag the current state v1.0.0, and start CHANGELOG.md in Keep-a-Changelog format seeded from the shipped epics (v1.0 baseline, journal-sync, dashboard-summary, Aurora).
LAYERS: [data]
CHANGED_HINT: app/build.gradle.kts (lines ~52–53), new CHANGELOG.md, .github/workflows/ci.yml if versionCode uses run number
TEST_TYPES: unit
CONSTRAINTS: versionCode must be strictly monotonic across ALL build sources (local vs CI) — Play rejects regressions; scheme documented in the file it lives in; CHANGELOG seeding from PROGRESS/done-SPECs is summary-level, no history rewriting
=== END SPEC ===

## Gap / context
First public release needs reproducible versions and a human-readable history that
isn't a 263KB log. Source: review items 20+55 (P3/S).

## Implementation links
- commit: (pending)
- files: (pending)
