---
name: mp-dev
description: Run the MyMoney mobile development pipeline through native Codex subagents. Use when the user invokes $mp, /mp, mp-dev, or asks for MP feature, bugfix, planning, phase, device, coverage, improvement, reflection, or state checks.
---

# MyMoney MP Dev Pipeline

1. Read `AGENTS.md` and the compact head of `docs/implementation_plan/PROGRESS.md` first. Do not open `docs/implementation_plan/log/*.md` unless the current task cites historical detail from that month.
2. Read the selected-mode and token-budget guides:
   - `.claude/mp/RUNBOOK_SELECTED_MODE.md`
   - `.claude/mp/extras/mp-token-budget.md`
3. Read the shared project MP config and only the needed overrides:
   - `.claude/mp/config.json`
   - `.claude/mp/extras/<agent>.md` only for the role about to run
   - `.claude/specs/README.md` when using the backlog board.
4. Read the canonical Claude `mp-dev` command body from `C:/Users/Admin/.codex/plugins/cache/personal/mp-dev/1.10.0+codex.20260701151538/commands/mp.md` only when the selected-mode runbook does not cover the requested mode or a plugin contract is ambiguous.
5. Interpret `$mp` and `/mp` as the Codex equivalent of that canonical `/mp` workflow. Supported modes include `--feature`, `--bugfix`, `--discuss`, `--spec`, `--coverage`, `--device`, `--fit`, `--plan`, `--phase`, `--check`, `--improve`, and `--reflect`.
6. Use native Codex subagents from `.codex/agents/mp-*.toml` for reasoning roles. Use deterministic reviewer/runner scripts first when they are available; a user request for "subagents" does not force LLM runner/reviewer fallback.
7. Preserve the canonical output contracts exactly: JSON-only for implementation/test/review/run/verify/docs agents and `=== BRAINSTORM ===` blocks for `mp-architect`. Retry invalid structured output once, then stop.
8. Codex Bash compatibility: if Bash is unavailable, use native Codex fallback paths. Instrumented Android runs prefer `scripts/mp-runner-instrumented-android.ps1`, which wraps the sanctioned host-AVD helper and emits parsed JSON.
9. Device discovery fallback: when a visual/device gate cannot attach to the documented `10.0.2.2:5555`, do not stop immediately. Run local ADB discovery (`adb devices -l`), inspect every `device` serial, and accept a serial only when it reports `ro.boot.qemu.avd_name=Pixel_5_API_34`, `ro.build.version.sdk=34`, and `sys.boot_completed=1`. Local `emulator-5554` is valid when Codex is on the Windows host side. Stop and ask the user only after both documented attach and local discovery fail, or the discovered device is wrong/offline/unauthorized.
10. Keep MyMoney project state single-sourced: `docs/implementation_plan/PROGRESS.md` owns phase/release state, and `.claude/specs/{backlog,active,done}` owns MP backlog work. `mp-docs` is inert in this project through `.claude/mp/extras/mp-docs.md`.
11. Claude/Codex sync rule: project-specific improvements go into `.claude/mp/extras/*` first so both Claude and Codex consume the same behavior. Plugin-level lessons go through `/mp --improve` or `/mp --reflect`.
12. Parallel-use rule: Claude and Codex may share the MP board, but only one agent should implement a given active SPEC at a time. Parallel work is allowed only for explicitly disjoint backlog SPECs.
13. `$cmp` is legacy. Do not use CMP for new Codex work unless the user explicitly asks for a historical fallback.

Use the compact runbook first, then fall back to the canonical `mp-dev` command body for uncovered modes; this skill is the Codex bridge that keeps MyMoney aligned with the Claude MP Dev pipeline without paying the full prompt tax on every common run.
