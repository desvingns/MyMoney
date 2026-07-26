# Handoff — MyMoney (cross-tool scratchpad)

Phase/release state authority: `docs/implementation_plan/PROGRESS.md` (do not restate it here).

## DONE
- 2026-07-26: SPEC `monefy-decoupling-01-ui-component-rename` CLOSED locally in commit
  `3c3ce219`. Renamed the design-system Monefy-prefixed UI components, consumers, tests,
  detekt baseline, and screenshot baselines. Evidence: 1655 JVM tests + 147 connected
  design-system tests green; reviewer, runner, and verifier passed. Close-out commit
  `0489a1e6` was pushed to `origin/main`.
- 2026-07-26: SPEC `review-2026-07-28-convention-plugins` CLOSED (pushed, 748a8efa). Codex had
  stopped it after its 2 runner attempts; the migration itself was already complete and correct.
  Equivalence proven against a `git worktree` at the pre-migration commit `a4e41e06`: dependency
  graphs identical for all 19 modules (2636 configurations, 32878 coordinates, 0 diffs), 1631 unit
  tests green, Kover coverage equal to 4 decimals, detekt/ktlint failure sets unchanged. One real
  regression found and fixed: a stray blank line in `macrobenchmark/build.gradle.kts`.
  **Why Codex could not finish:** `scripts/mp-runner-android.sh` is structurally incapable of
  returning `pass:true` in this repo — it runs `:app:jacocoUnitTestReport` (this project uses Kover)
  and greps for `N tests completed`, a line Gradle prints only on FAILURE. Both are model-independent.
  The protocol to use instead is now in `.claude/mp/extras/mp-runner-android.md`.
- 2026-07-10: Codex default model and all five active native `gpt-5.5` specialists
  (`mp-developer-android`, `mp-fidelity-android`, `spec-evaluator`, and both screenshot
  analyzers) were promoted to `gpt-5.6`; existing reasoning effort and sandbox modes remain
  unchanged. The 17 MP Spec native subagents also now point at the installed personal
  `mp-spec` 1.10.0 cache instead of the absent 1.8.1 cache.
- 2026-07-05: `.ai/` workspace introduced (README, memory/MEMORY.md, this file);
  AGENTS.md gained the file-deletion policy + second-brain section; CLAUDE.md
  collapsed to `@AGENTS.md` + Claude-only deltas (host device testing, token
  workflow, /mp plugin notes, auto-push policy, memory paths).

## DECISIONS
- `.ai/memory/MEMORY.md` is the shared durable memory for both tools; tool-local
  memories are mirrors.

## NEXT
- (owner of the next session) Read PROGRESS.md for the active phase as usual. The next runnable
  backlog SPEC is `monefy-decoupling-02-database-rename-migration.md`.

## OWNER
- none (idle)

## BLOCKERS
- none
