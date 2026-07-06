# Restructure PROGRESS.md: compact current state + monthly log archives
Epic: review-2026-07
Order: 08 of 35
Status: draft
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Split the 263KB docs/implementation_plan/PROGRESS.md into a compact head file (Current state ≤50 lines: active phase pointer, last 3 session entries, phase matrix, deferred-work table, pointers) plus append-only monthly archives docs/implementation_plan/log/2026-MM.md holding the full historical session entries verbatim; update the session-protocol references (implementation_plan/README.md §2, AGENTS.md/CLAUDE.md mentions) so both tools read the cheap head first and archives only on demand.
LAYERS: [data]
CHANGED_HINT: docs/implementation_plan/PROGRESS.md, new docs/implementation_plan/log/*.md, docs/implementation_plan/README.md
TEST_TYPES: unit
CONSTRAINTS: MOVE content verbatim, never delete or summarize away historical entries; PROGRESS.md remains the SOLE writer of phase/release state (mp-docs stays inert); the "read this first" contract must keep working for both Claude and Codex sessions
=== END SPEC ===

## Gap / context
The mandatory-read state file costs ~33k tokens per session — directly against the
project's token-conscious workflow. Source: review item 51 (P1/S).

## Implementation links
- commit: (pending)
- files: (pending)
