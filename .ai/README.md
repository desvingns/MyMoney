# .ai — shared cross-tool workspace (MyMoney)

Git-tracked seam between Claude Code (Windows host) and Codex CLI (VirtualBox guest).
Transport is git: commit here and the other tool sees it on its next run.

- `memory/MEMORY.md` — durable facts BOTH tools need for this repo (append-mostly,
  English, never delete). Tool-local memories mirror it, never replace it.
- `handoff.md` — cross-tool session scratchpad (DONE / DECISIONS / NEXT / OWNER /
  BLOCKERS). Rewritten at hand-off boundaries.

HARD RULE: phase/release state lives ONLY in `docs/implementation_plan/PROGRESS.md`.
Nothing under `.ai/` competes with it — handoff.md may point at the active phase,
never restate it as authority.

Cross-project knowledge goes one level up — the second brain (`D:\Pet\brain` on host,
`C:\Pet\brain` in the guest): see AGENTS.md "Shared cross-tool workspace + second brain".
