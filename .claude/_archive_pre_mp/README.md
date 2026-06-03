# `_archive_pre_mp` — archived `/cmp` fork + Codex `$cmp` mirror

**Archived: 2026-06-03.** Superseded by the `/mp` marketplace plugin (`mobile-pipeline/mp-dev`
v1.5.0) and its Codex mirror `$mp-dev`. These files are kept **verbatim** as a safe fallback — they
were NOT deleted. Nothing here is active: the paths are nested one level under
`.claude/_archive_pre_mp/`, so neither Claude Code (`.claude/agents/`, `.claude/commands/`) nor Codex
(`.codex/agents/`, `.agents/skills/`) scans them.

## Why archived

MyMoney migrated from the project-local `/cmp` fork to `/mp`. `/mp` is a functional **superset** —
native `--phase` / `--check` / `--plan --phases` / `--device` / `--feature --next` — and reads all
MyMoney-specific rules from `.claude/mp/config.json` + repo `CLAUDE.md` + `.claude/mp/extras/<agent>.md`.
The 4 customization gaps (inert `mp-docs`, host-AVD device runner, `--plan` muscle-memory, extras
parity) were reconciled before archival; `/mp --check` passed and the migration extras were committed
(`1fe5834`). See memory `mymoney-cmp-to-mp-migration`.

## Contents (mirror of original paths)

```
.claude/commands/cmp.md                     → /mp (plugin command, no repo file)
.claude/agents/cmp-*.md (9)                  → mp-* agents (from mp-dev plugin)
.claude/cmp-mymoney/*-extras.md (4)          → .claude/mp/extras/mp-*-android.md
.claude/.cmp-version                         → (retired)
.agents/skills/cmp/ (Codex skill)            → .agents/skills/mp-dev/
.codex/agents/cmp-*.toml (7)                 → .codex/agents/mp-*.toml
```

## Rollback (restore `/cmp` as the active fallback)

From the repo root, reverse every move, e.g.:

```bash
ARCH=.claude/_archive_pre_mp
git mv "$ARCH/.claude/commands/cmp.md" .claude/commands/cmp.md
# …and the same for each .claude/agents/cmp-*.md, .claude/cmp-mymoney/*, .codex/agents/cmp-*.toml,
#    .agents/skills/cmp/*, and .claude/.cmp-version
```

Then restart Claude Code so `/cmp` reloads. Do **not** delete this folder — it is the migration safety net.
