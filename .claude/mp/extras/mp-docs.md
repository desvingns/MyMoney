# mp-docs — MyMoney extras (INERT OVERRIDE)

Read this **after** the `mp-docs` agent body. These rules are MyMoney-specific and **override** the
agent's defaults wherever they conflict — explicitly including the "STATE.md always updates when the
file exists" rule.

## In MyMoney, mp-docs is INERT — do nothing

Project state in MyMoney is owned **exclusively** by `docs/implementation_plan/PROGRESS.md` (active
phase, decisions log, deferred OQ items) plus the `.claude/specs/{backlog,active,done}/` SPEC board.
The repo-root `STATE.md` / `ROADMAP.md` / `DOCUMENTATION.md` are one-line **stub redirects** pointing
at `docs/implementation_plan/` and the TDD — they are intentionally NOT live documents here.

Therefore, whenever you are spawned in this project:

- **Make no edits.** Do NOT read, write, create, or rewrite `STATE.md`, `ROADMAP.md`,
  `DOCUMENTATION.md`, or `CLAUDE.md`. The agent body's "STATE.md always updates when the file exists"
  instruction does **not** apply in MyMoney — this override supersedes it.
- **Create no commit.**
- Return **exactly** this single-line JSON and nothing else (no prose, no markdown fences):

  `{"committed": false}`

That is the entire job for mp-docs in MyMoney.

## Why

MyMoney follows the implementation-plan phase model, not the legacy CMP STATE/ROADMAP/DOCUMENTATION
iteration model. Letting mp-docs write `STATE.md` would create a second, competing source of project
state and clobber the stub redirect. The generic `/mp` orchestrator still spawns `mp-docs` on
`--feature`/`--bugfix` (Step 6) and cannot be patched per-project — this extras override is how
MyMoney keeps it inert, the same way the archived `cmp-docs` agent was inert. See `AGENTS.md`
→ "Project state files".
