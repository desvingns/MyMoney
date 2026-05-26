---
name: cmp
description: Run the MyMoney delivery pipeline through native Codex subagents. Use when the user invokes $cmp for feature, bugfix, brainstorm, phase progression, or state checks, especially when they request subagents.
---

# MyMoney CMP Pipeline

1. Read `AGENTS.md` and `docs/implementation_plan/PROGRESS.md` first.
2. Read `.claude/commands/cmp.md` as the project-customized canonical workflow. Preserve its MyMoney-only `--phase` and `--check` modes and its rule that `PROGRESS.md` owns project state.
3. Interpret `$cmp` as the Codex equivalent of the `/cmp` invocation documented there.
4. When the user requests subagents, use the matching native agent from `.codex/agents/` for each named specialist step:
   - `cmp-architect` for `--discuss`.
   - `cmp-developer-android`, `cmp-tester-android`, `cmp-reviewer-android`, `cmp-runner-android`, and `cmp-verifier-android` for Android implementation flow.
5. Preserve each JSON or BRAINSTORM output contract exactly. Retry invalid structured output once, then stop.
6. Keep MyMoney-specific constraints: `cmp-docs` is intentionally inert; `--phase` updates only the phase files and `PROGRESS.md` as specified in the canonical workflow.
7. Never push until tests are committed and the user explicitly approves the final push gate.

Use the canonical workflow rather than reproducing it here, because MyMoney has local phase-management extensions not present in the upstream generator.
