---
name: cmp
description: Run the MyMoney delivery pipeline through native Codex subagents. Use when the user invokes $cmp for feature, bugfix, brainstorm, phase progression, or state checks, especially when they request subagents.
---

# MyMoney CMP Pipeline

1. Read `AGENTS.md` and `docs/implementation_plan/PROGRESS.md` first.
2. Read `.claude/commands/cmp.md` as the project-customized canonical workflow. Preserve its MyMoney-only `--phase`, `--check`, and `--device` modes and its rule that `PROGRESS.md` owns project state (`--device` updates `docs/DEVICE_VERIFICATION_PROGRESS.md`, the device tracker, instead).
3. Interpret `$cmp` as the Codex equivalent of the `/cmp` invocation documented there.
4. When the user requests subagents, use the matching native agent from `.codex/agents/` for each named specialist step:
   - `cmp-architect` for `--discuss`.
   - `cmp-developer-android`, `cmp-tester-android`, `cmp-reviewer-android`, `cmp-runner-android`, and `cmp-verifier-android` for Android implementation flow.
   - `cmp-runner-instrumented-android` for the on-device test run in `--device` (runs one `connectedDebugAndroidTest` class on `Pixel_5_API_34` via `scripts/run_connected_test_on_host_avd.ps1`; the sole PowerShell-invoking agent).
5. The native Codex agent TOML files intentionally pin model tiers instead of inheriting the parent session:
   - `cmp-runner-android` and `cmp-runner-instrumented-android`: `gpt-5.4-mini` / `low`.
   - `cmp-reviewer-android` and `cmp-verifier-android`: `gpt-5.4-mini` / `medium`.
   - `cmp-architect` and `cmp-tester-android`: `gpt-5.4` / `high`.
   - `cmp-developer-android`: `gpt-5.5` / `high`.
   Do not override these tiers ad hoc unless a task explicitly requires a different model.
6. Preserve each JSON or BRAINSTORM output contract exactly. Retry invalid structured output once, then stop.
7. Keep MyMoney-specific constraints: `cmp-docs` is intentionally inert; `--phase` updates only the phase files and `PROGRESS.md` as specified in the canonical workflow.
8. Never push until tests are committed and the user explicitly approves the final push gate.

Use the canonical workflow rather than reproducing it here, because MyMoney has local phase-management extensions not present in the upstream generator.
