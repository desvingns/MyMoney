# Backfill docs/DECISIONS/ with retrospective ADRs
Epic: review-2026-07
Order: 20 of 35
Status: done
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Create docs/DECISIONS/ with the ADR template plus 5–6 retrospective ADRs for already-made decisions currently buried in PROGRESS.md logs: biometric pinned to androidx.biometric 1.1.0 (phase file's 1.2.0-alpha07 does not exist); sentry-android-core instead of umbrella sentry-android (R8 15MB budget); SAF OpenDocumentTree export + OQ-8 keep-newest-3 rotation instead of CreateDocument; AppCompatActivity migration for per-app locale on API 31/32; search debounce 200ms per TDD over phase file's 300ms; MainActivity/AppCompat theme reparenting. Each ADR cites its PROGRESS.md entry and TDD lines.
LAYERS: [domain]
CHANGED_HINT: new docs/DECISIONS/ADR-0003..ADR-0008 (numbering after SPECs 17/18), template file; sources: docs/implementation_plan/PROGRESS.md decision-log lines
TEST_TYPES: unit
CONSTRAINTS: retrospective — record what WAS decided and why, verbatim-faithful to the PROGRESS entries (no reinterpretation); ADR numbering coordinated with SPECs 17/18 outputs; glossary already promises docs/DECISIONS/ADR-*.md as the convention
=== END SPEC ===

## Gap / context
The project's own glossary defines the ADR convention, yet zero ADR files exist —
decisions live in a 263KB log. Source: review item 52 (P2/S).

## Implementation links
- commit: c7f897cf (backfill ADR-0003..0007 + TEMPLATE), efe8043b (ADR-0005 TDD AC1 clarification)
- files: docs/DECISIONS/TEMPLATE.md, docs/DECISIONS/ADR-0003-biometric-dependency-pinned-1.1.0.md, docs/DECISIONS/ADR-0004-sentry-core-not-umbrella.md, docs/DECISIONS/ADR-0005-backup-saf-opendocumenttree-rotation.md, docs/DECISIONS/ADR-0006-mainactivity-appcompat-theme-reparent.md, docs/DECISIONS/ADR-0007-search-debounce-200ms-tdd-wins.md
- note: 5 substantive ADRs (0003–0007); the AppCompatActivity migration + theme reparenting were folded into ADR-0006 as facets of one commit (815d4b7), so no ADR-0008 — within the SPEC's "5–6" range.
