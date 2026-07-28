# Repo hygiene: root log litter, stray "@ " commit prefix, dead lock overload
Epic: review-2026-07
Order: 35 of 35
Status: done
Depends-on: —
Date: 2026-07-06
Revised: 2026-07-28 (re-scoped after staleness pre-check)

## SPEC
=== SPEC ===
TASK: feature
WHAT: Clean the repository working surface (re-scoped 2026-07-28: item (a) already delivered — .codex/hooks.json + .codex/agents/selfimprove-retro.toml committed in 4115685d, outputs/ git-ignored in a4e41e06, *.log pattern already in .gitignore; verify-only): (b) move the 6 ignored root log files (.tmp-detekt.log, .tmp-jacoco.log, .tmp-lint.log, .tmp-test.log, at.log, test_output.log) to archive/ for manual deletion; (c) find and fix the source of the stray "@ " prefix in commit subjects (f451c240, b41aecad, 5cdddc78, 8ddb79cf — likely a hook/script gluing an argument during automated commits; inspect .git/hooks, .codex/hooks.json, mp scripts); (d) remove the now-unused no-arg LockController.markUnlocked() overload flagged as a hygiene candidate by the 2026-07-28 critic pass.
LAYERS: [data]
CHANGED_HINT: .git/hooks/*, .codex/hooks.json, .claude/scripts/*, archive/, :feature:lockscreen LockController + call sites
TEST_TYPES: unit
CONSTRAINTS: NEVER delete — archive/ + report exact paths for manual removal (project policy); do not rewrite existing git history for the "@ " commits (fix the source only); if the "@ " source lives in an unversioned path (e.g. .git/hooks), fix it locally and report instead of committing; any scope beyond (b)/(c)/(d) is a one-line user gate at implement time
=== END SPEC ===

## Gap / context
Original scope from review items 53+54 (P2/S + P3/S). Staleness pre-check 2026-07-28 found
(a) fully delivered incrementally; remaining work is (b) archiving 6 root logs, (c) the "@ "
tooling investigation, and (d) the dead `markUnlocked()` overload.

## Implementation links
- commit: e7b9d1b5 (remove dead no-arg markUnlocked overload), 30ed4fe1 (migrate stale test
  references); items (b)/(c) intentionally produced no git delta — the 6 logs moved between two
  git-ignored locations and the "@ " source turned out to be a transient agent-command artifact
  (PowerShell here-string passed to Git Bash `git commit -m`, 2026-06-26 JournalSync runs), so
  there was no versioned tooling to fix; history untouched per CONSTRAINTS.
- files: feature/lockscreen/src/main/java/com/kshavrin/mymoney/feature/lockscreen/overlay/LockController.kt,
  feature/lockscreen/src/test/java/com/kshavrin/mymoney/feature/lockscreen/overlay/LockControllerTest.kt,
  archive/ (6 root logs, awaiting manual deletion), .git/hooks/post-commit (local unversioned
  null-byte probe fix).
- evidence: reviewer pass (0 violations); runner 1694/0/0 + detekt ok + lint ok; verifier pass
  (tests_exist=ok, stale_tests=ok); completed 2026-07-28.
