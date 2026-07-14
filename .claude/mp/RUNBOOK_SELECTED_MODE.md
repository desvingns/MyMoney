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
2. Before activation, apply a scope admission gate. A SPEC is too broad for one ordinary
   `--feature --next` run when it uses whole-app language (`every`, `all screens`, `sweep`, or
   `across modules`) and is expected to touch more than 3 modules or roughly 12 production files.
   Split it into independently testable child SPECs unless the user explicitly requests one atomic
   cross-cutting migration. Do not silently turn a backlog audit into a release-sized run.
3. Move the SPEC `backlog/` to `active/`, set `Status: active`, and use its `=== SPEC ===` block as
   the only feature contract.
4. Build a context capsule before every agent call: SPEC, relevant files, changed files, failing
   command/report, and the exact role task. Do not paste full progress archives or broad logs.
5. Developer and tester are LLM roles. Reviewer and JVM runner are deterministic script roles when
   scripts are available; use LLM fallback only on script failure or unsupported checks.
6. Compile instrumented sources, then run changed/added connected test classes in small deterministic
   groups. Run the full `connectedDebugAndroidTest` suite only for a release gate, a shared-foundation
   change, or an explicit user request. If the full suite exposes unrelated baseline failures, record
   them and isolate the feature-owned classes instead of expanding the feature without evidence.
7. Android instrumented test method names added by MP must be DEX-safe ASCII identifiers
   (`[A-Za-z0-9_]`); do not use backtick prose names for new on-device tests.
8. Run verifier after green feature-owned tests. Push is still gated by the user.
9. Close the SPEC by moving it to `done/`, filling commits/files, and adding a short Current state
   bullet. Archive older Current state bullets instead of growing `PROGRESS.md`.

## `--bugfix` Runtime / Cold-Start

1. Reproduce the literal bug before code changes.
2. For import/cold-start/persistence bugs, run the triage checklist in `mp-token-budget.md` before
   assigning implementation.
3. After JVM checks pass, re-run the literal device scenario. A self-authored unit test is not enough.

## `--device`

Write and run exactly one instrumented test class. Use `scripts/mp-runner-instrumented-android.ps1`
for deterministic execution and parsed JSON. Stop after one green/red/escalated control.
