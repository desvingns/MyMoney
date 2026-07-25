# mp-token-budget - MyMoney MP token economy contract

Read this once per MP orchestration session. It applies to the main Codex session and to spawned
MP agents unless a role-specific extra says otherwise.

## Eight active optimizations

1. **Compact progress head.** Read only `docs/implementation_plan/PROGRESS.md` by default. Historical
   entries live in `docs/implementation_plan/log/*.md`; open an archive only when the task cites that
   month or a missing historical fact.
2. **Selected-mode runbook.** Use `.claude/mp/RUNBOOK_SELECTED_MODE.md` for covered Codex modes before
   opening the full canonical `commands/mp.md`.
3. **Deterministic reviewer/runner first.** `using subagents` means reasoning roles may be delegated;
   it does not force LLM reviewer/runner when the project/plugin script can emit structured JSON.
4. **Deterministic instrumented runner.** Use `scripts/mp-runner-instrumented-android.ps1` for one
   connected test class; it wraps the sanctioned host-AVD helper and parses XML into JSON.
5. **Context capsule.** Pass agents a small capsule, never broad state dumps:
   `SPEC`, `TASK`, `FILES_TO_READ`, `CHANGED_FILES`, `MODIFIED_EXISTING`, `FAILURE_EVIDENCE`,
   `COMMANDS_TO_RUN`, `RULES_IN_FORCE`.
6. **Runtime import/cold-start triage.** Before the first production fix, check: imported row counts,
   blank/duplicate UUIDs, account/category FK mapping, `importFocusEpochMs`, `importFocusCurrencyId`,
   inactive currency visibility, dashboard selection mode, and `op_journal` emission.
7. **Reuse repair agents.** If a developer/tester produced a partial patch and the next failure is a
   direct continuation, send the failure back to the same agent. Spawn a fresh agent only after hang,
   invalid architecture, or context contamination.
8. **Role-based extras.** Main orchestration reads this file plus the role extra it is about to spawn.
   Subagents read only their role extra and cited source files. Do not bulk-load every MP extra inside
   every role.

## Quality floor

Do not save tokens by weakening gates: no ignored tests, no skipped device verification for runtime
bugs, no removal of reviewer checks, no uncited deviations from TDD/AS decisions, and no broad claims
from `BUILD SUCCESSFUL` without parsed test evidence.

## Backlog-consume authorization

`--feature --next` and `--backlog <slug>` consume an already-approved SPEC. Their invocation is the
authorization to activate the SPEC and start Phase 2 immediately; do not ask for another SPEC or
pre-agent `y/N`. Announce activation and continue. Device, destructive-action, external-account,
and other safety gates still apply when their actual transition is reached, but they must not be
relabelled as a generic permission to launch Phase 2.
