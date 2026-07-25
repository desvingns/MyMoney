# Handoff — MyMoney (cross-tool scratchpad)

Phase/release state authority: `docs/implementation_plan/PROGRESS.md` (do not restate it here).

## DONE
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
- (owner of the next session) Read PROGRESS.md for the active phase as usual.

## OWNER
- none (idle)

## BLOCKERS
- none
