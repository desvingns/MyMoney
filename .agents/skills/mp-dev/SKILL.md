---
name: mp-dev
description: Run the MyMoney mobile development pipeline through native Codex subagents. Use when the user invokes $mp, /mp, mp-dev, or asks for MP feature, bugfix, planning, phase, device, coverage, improvement, reflection, or state checks.
---

# MyMoney MP Dev Pipeline

1. Read `AGENTS.md` and `docs/implementation_plan/PROGRESS.md` first.
2. Read the shared project MP config and overrides:
   - `.claude/mp/config.json`
   - `.claude/mp/extras/*.md`
   - `.claude/specs/README.md` when using the backlog board.
3. Read the canonical Claude `mp-dev` command body from `C:/Users/k.shavrin/.claude/plugins/cache/mobile-pipeline/mp-dev/1.8.1/commands/mp.md`.
4. Interpret `$mp` and `/mp` as the Codex equivalent of that canonical `/mp` workflow. Supported modes include `--feature`, `--bugfix`, `--discuss`, `--spec`, `--coverage`, `--device`, `--fit`, `--plan`, `--phase`, `--check`, `--improve`, and `--reflect`.
5. Use native Codex subagents from `.codex/agents/mp-*.toml`. Each Codex agent is a thin wrapper over the matching canonical Claude agent body in `C:/Users/k.shavrin/.claude/plugins/cache/mobile-pipeline/mp-dev/1.8.1/agents/` plus any matching `.claude/mp/extras/<agent>.md` override.
6. Preserve the canonical output contracts exactly: JSON-only for implementation/test/review/run/verify/docs agents and `=== BRAINSTORM ===` blocks for `mp-architect`. Retry invalid structured output once, then stop.
7. Codex Bash compatibility: this Windows environment may not have `bash` in `PATH`. If Bash is unavailable, do not call the Claude deterministic `.sh` scripts; use the native Codex `mp-reviewer-android` and `mp-runner-android` agents directly. `mp-runner-instrumented-android` is the only MP role allowed to invoke the PowerShell host-AVD helper for connected tests.
8. Keep MyMoney project state single-sourced: `docs/implementation_plan/PROGRESS.md` owns phase/release state, and `.claude/specs/{backlog,active,done}` owns MP backlog work. `mp-docs` is inert in this project through `.claude/mp/extras/mp-docs.md`.
9. Claude/Codex sync rule: project-specific improvements go into `.claude/mp/extras/*` first so both Claude and Codex consume the same behavior. Plugin-level lessons go through `/mp --improve` or `/mp --reflect`.
10. Parallel-use rule: Claude and Codex may share the MP board, but only one agent should implement a given active SPEC at a time. Parallel work is allowed only for explicitly disjoint backlog SPECs.
11. `$cmp` is legacy. Do not use CMP for new Codex work unless the user explicitly asks for a historical fallback.

Use the canonical `mp-dev` command body rather than reproducing it here; this skill is the Codex bridge that keeps MyMoney aligned with the Claude MP Dev pipeline.
