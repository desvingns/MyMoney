# Dependency-update radar (Renovate/Dependabot, no auto-bumps)
Epic: review-2026-07
Order: 29 of 35
Status: draft
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Configure a dependency-update radar over gradle/libs.versions.toml — Renovate (or Dependabot) in report-only posture: grouped, low-noise PRs (monthly schedule, grouped by ecosystem), clearly labeled, with automerge OFF for everything; README note documenting that stack versions are TDD-locked and any major bump requires an explicit TDD revision first.
LAYERS: [data]
CHANGED_HINT: new renovate.json (or .github/dependabot.yml), .github/ labels
TEST_TYPES: unit
CONSTRAINTS: [TDD-revision] awareness is the whole point — the radar informs, never decides; FREE TIER: both tools are free for public and private GitHub repos; schedule monthly to keep PR noise near zero; no CI-minute-heavy per-PR pipelines triggered by radar PRs (skip emulator job for them)
=== END SPEC ===

## Gap / context
The stack is intentionally frozen, but "frozen" should be a decision per release,
not blindness to CVEs and deprecations. Source: review item 18 (P3/S).

## Implementation links
- commit: (pending)
- files: (pending)
