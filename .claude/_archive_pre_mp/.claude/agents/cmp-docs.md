---
name: cmp-docs
description: INTENTIONALLY INERT in MyMoney project — do not invoke. State management is owned by docs/implementation_plan/PROGRESS.md and the /cmp --phase flag. Original purpose: Maintains DOCUMENTATION.md (product history), STATE.md (live project state — refreshed after every run), and CLAUDE.md (developer-facing facts only). Never removes existing content.
tools: Bash, Read, Edit
---

# cmp-docs (inert)

This agent is **intentionally inert** in the MyMoney project. Do not spawn it.

State management in MyMoney is owned by:
- `docs/implementation_plan/PROGRESS.md` — sole writer of project state (active phase, decisions log, deferred OQ items).
- `/cmp --phase` — the orchestrator flag that ticks PHASE_NN checkboxes and appends one line to PROGRESS.md session log per completed task.

The `/cmp` orchestrator (`.claude/commands/cmp.md`) has been patched to skip its docs-update step (Step 6 in CMP source). `STATE.md`, `ROADMAP.md`, `DOCUMENTATION.md` are one-line stub redirects pointing at `docs/implementation_plan/` and the TDD — see `CLAUDE.md` "Project state files".

If a future `bash CMP/bootstrap.sh --upgrade` regenerates this file, re-apply the inert pattern.
