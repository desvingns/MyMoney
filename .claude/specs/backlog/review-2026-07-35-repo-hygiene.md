# Repo hygiene: untracked strays, root logs, stray "@ " commit prefix
Epic: review-2026-07
Order: 35 of 35
Status: draft
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Clean the repository working surface: (a) decide-and-do for each untracked stray — .codex/hooks.json and .codex/agents/selfimprove-retro.toml (commit if load-bearing for Codex sessions, else archive/), outputs/ (gitignore or archive/); (b) root log litter (.tmp-detekt.log, .tmp-jacoco.log, .tmp-lint.log, .tmp-test.log, at.log, test_output.log) → add patterns to .gitignore and move existing files to archive/ for manual deletion; (c) find and fix the source of the stray "@ " prefix in commit subjects (f451c240, b41aecad, 5cdddc78, 8ddb79cf — likely a hook/script gluing an argument during automated commits; inspect .git/hooks, .codex/hooks.json, mp scripts).
LAYERS: [data]
CHANGED_HINT: .gitignore, .codex/*, .git/hooks/post-commit (graphify hook), archive/
TEST_TYPES: unit
CONSTRAINTS: NEVER delete — archive/ + report exact paths for manual removal (project policy); do not rewrite existing git history for the "@ " commits (fix the source only); each (a) decision is a one-line user gate at implement time
=== END SPEC ===

## Gap / context
Untracked strays accumulate decisions-by-default; the "@ " prefix hints at a live
tooling bug corrupting commit subjects. Source: review items 53+54 (P2/S + P3/S).

## Implementation links
- commit: (pending)
- files: (pending)
