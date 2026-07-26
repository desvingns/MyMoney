# Native Codex Pipeline ($mp)

Codex-side `$mp` operating rules, extracted from `AGENTS.md` on 2026-07-26 — they were being
loaded into every Claude `/mp` agent context for no benefit. Claude-side notes live in `CLAUDE.md`.


- Invoke `$mp --feature <description>`, `$mp --feature --next`, `$mp --bugfix <description>`,
  `$mp --discuss <topic>`, `$mp --spec <description>`, `$mp --coverage`, `$mp --device <Sxx>`,
  `$mp --fit`, `$mp --plan`, `$mp --phase`, `$mp --check`, `$mp --improve`, or `$mp --reflect`.
- `$mp --feature --next` and `$mp --backlog <slug>` are execution authorization for an
  already-approved SPEC: activate it and start Phase 2 without another SPEC/pre-agent `y/N`.
  Announce activation informationally; keep real safety gates at the transition they protect.
- `$mp` is provided only by the official `mp-dev@mobile-pipeline` marketplace plugin. The companion
  `mp-spec@mobile-pipeline` plugin is used for full mobile specification bundles.
- Claude and Codex share the same project configuration and overrides:
  `.claude/mp/config.json`, `.claude/mp/extras/`, and `.claude/specs/{backlog,active,done}/`.
  Put project-specific skill/agent improvements in `.claude/mp/extras/*` first so both surfaces stay
  synchronized. Use `$mp --improve` / `$mp --reflect` only for plugin-level improvements.
- Codex MP startup is token-budgeted: read `.claude/mp/RUNBOOK_SELECTED_MODE.md` and
  `.claude/mp/extras/mp-token-budget.md`, then read only the role-specific extra being used. Do not
  bulk-load `docs/implementation_plan/log/*.md` or every MP extra unless the task explicitly needs it.
- Before spawning an MP subagent, build a compact context capsule: SPEC, task, files to read,
  changed files, modified-existing list, failure evidence, commands to run, and rules in force.
  Do not paste broad progress archives or unrelated logs into agent prompts.
- Claude and Codex may both use the MP Dev board, but only one active SPEC should be implemented at a
  time unless the work is explicitly split into disjoint backlog SPECs. Before starting implementation,
  check `.claude/specs/active/` and avoid racing another agent on the same SPEC.
- `$mp --device <Sxx>` runs one on-device instrumented-test slice for a single control: it reads
  `docs/DEVICE_VERIFICATION_PROGRESS.md` and `docs/DEVICE_VERIFICATION_PLAN_FOR_SONNET.md`, writes one
  Compose-UI test, runs it on `Pixel_5_API_34` via deterministic
  `scripts/mp-runner-instrumented-android.ps1` (LLM runner fallback only when the wrapper needs
  diagnosis), then updates the tracker. One control per run; never pushes.
- `$mp --phase`, `$mp --feature`, `$mp --bugfix`, `$mp --device`, and `$mp --fit` must run the
  visual-change device gate above before any agent work when the task is explicitly visual and needs
  visual/device autotests.
- Native specialists live in `.codex/agents/mp-*.toml`. They read the matching canonical `mp-dev`
  agent body, `.claude/mp/extras/mp-token-budget.md`, and then `.claude/mp/extras/<agent>.md` if
  present. Role-based extras keep subagent prompts small.
- Deterministic reviewer/runner first: when Bash is available, use the plugin
  `mp-reviewer-android.sh` / `mp-runner-android.sh` scripts before LLM fallback. A user request for
  "subagents" does not force LLM reviewer/runner if a deterministic script can emit the required JSON.
- Repair-loop economy: send direct test/reviewer failures back to the same developer/tester subagent
  when it is still healthy and the failure is a continuation. Spawn a fresh agent only after a hang,
  invalid architecture, or contaminated context.
- Keep `docs/implementation_plan/PROGRESS.md` as the only phase/release state writer. The MP docs step
  remains inert via `.claude/mp/extras/mp-docs.md`.
- `$cmp` is an archived fallback (`.claude/_archive_pre_mp/`, archived 2026-06-03 — not deleted). Do not
  use it for new Codex work; restore it from the archive only if the user explicitly asks for historical CMP behavior.
- Push only after tested files are committed and the user explicitly approves the final gate.

