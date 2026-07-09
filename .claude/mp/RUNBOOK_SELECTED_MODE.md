# MyMoney MP Selected-Mode Runbook

Use this compact runbook for Codex MP sessions before opening the full canonical
`commands/mp.md`. Open the canonical command body only when the requested mode is not covered here
or when a rule below conflicts with the plugin contract.

## Startup

1. Read `AGENTS.md` and the compact head of `docs/implementation_plan/PROGRESS.md`.
2. Do not open `docs/implementation_plan/log/*.md` unless investigating a referenced historical
   month.
3. Read `.claude/mp/config.json`, `.claude/mp/extras/mp-token-budget.md`, and only the role-specific
   extras needed for spawned agents.
4. For backlog work, read `.claude/specs/README.md` and the selected SPEC file only.

## `--feature --next` / `--backlog <slug>`

1. Prefer an existing active SPEC; otherwise take the lowest runnable backlog SPEC, ignoring
   `*-00-overview.md`.
2. Move the SPEC `backlog/` to `active/`, set `Status: active`, and use its `=== SPEC ===` block as
   the only feature contract.
3. Build a context capsule before every agent call: SPEC, relevant files, changed files, failing
   command/report, and the exact role task. Do not paste full progress archives or broad logs.
4. Developer and tester are LLM roles. Reviewer and JVM runner are deterministic script roles when
   scripts are available; use LLM fallback only on script failure or unsupported checks.
5. Run verifier after green tests. Push is still gated by the user.
6. Close the SPEC by moving it to `done/`, filling commits/files, and adding a short Current state
   bullet. Archive older Current state bullets instead of growing `PROGRESS.md`.

## `--bugfix` Runtime / Cold-Start

1. Reproduce the literal bug before code changes.
2. For import/cold-start/persistence bugs, run the triage checklist in `mp-token-budget.md` before
   assigning implementation.
3. After JVM checks pass, re-run the literal device scenario. A self-authored unit test is not enough.

## `--device`

Write and run exactly one instrumented test class. Use `scripts/mp-runner-instrumented-android.ps1`
for deterministic execution and parsed JSON. Stop after one green/red/escalated control.

