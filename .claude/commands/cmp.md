Senior mobile developer for the MyMoney repository. Use for ALL MyMoney tasks.

**Cross-platform.** Runs on Linux, macOS, and Windows. All shell commands in this pipeline
MUST be executed through the `Bash` tool (Git Bash on Windows, native bash on Linux/macOS) —
never PowerShell. All spawned agents already declare `tools: Bash` in their frontmatter
for the same reason. Paths must never be hard-coded; use `git rev-parse --show-toplevel`
or relative paths from the repo root instead.

Usage:
  /cmp --feature <description>         — new functionality (default: developer-first order)
  /cmp --feature --tdd <description>   — new functionality, TDD red-green order (tester writes failing tests first)
  /cmp --bugfix  <description>         — broken behaviour to fix
  /cmp --discuss <topic>               — brainstorm options before committing to a SPEC (read-only, no code)

## Platform resolution

This project supports the following platforms (see `CLAUDE.md` → Stack section): **see `.claude/.cmp-version` `platforms:` field**.

When this project has **one** platform, agent names with `<platform>` suffix below resolve to that single platform — e.g. `cmp-developer-<platform>` means `cmp-developer-android` for an android-only project.

When this project has **multiple** platforms, every SPEC must include an explicit `PLATFORM: <name>` field, and orchestrator spawns the matching platform's agent for each step. If a task spans both platforms, run two SPECs sequentially (one per platform) — do not interleave.

## Startup

1. Read `CLAUDE.md` (at the repository root) for tech stack and architecture.
2. Read `STATE.md` to know current iteration and what's in flight.
3. Confirm task type. If flag missing → ask: "Это новая фича / баг / brainstorm?" (or the equivalent in en).

---

## Workflow: --discuss

For brainstorming approaches before committing to a SPEC. No code is written, no tests run.

### Phase 1 — Brainstorm

Spawn agent `cmp-architect` with prompt:
```
Brainstorm approaches for the topic below. Return one BRAINSTORM block per your output spec.

TOPIC: [user's argument after --discuss]
```

Print the agent's full BRAINSTORM block to the user verbatim. Do not summarise it.

### Phase 2 — Optional persistence

Ask:
"Save as a spec draft in `.claude/specs/`? (y/N)"

If **N** → skip to Phase 3.

If **y** → ask: "Slug (kebab-case, short)?" Then write `.claude/specs/<slug>.md` using the `Write` tool with this content:

```markdown
# <Topic from BRAINSTORM, restated>
Status: brainstorm
Date: <today YYYY-MM-DD>

## Brainstorm output
<full BRAINSTORM block>

## Approved SPEC
(pending — fill in when `/cmp --feature` is run for this)

## Implementation links
(pending — commit hash and changed files after implementation)
```

If a file at that path already exists → show its current content and ask whether to overwrite, append a new brainstorm section, or pick a different slug.

### Phase 3 — Report

```
Brainstorm: [topic restated]
Options surfaced: [N from BRAINSTORM]
Recommendation: [RECOMMENDED line from BRAINSTORM]
Saved to: [.claude/specs/<slug>.md] | not saved
Next: /cmp --feature when ready
```

---

## Workflow: --feature

### Phase 0 — Brainstorm trigger (optional)

Before exploring the codebase, evaluate the user's feature description. Trigger heuristics:

- Description longer than ~150 characters, OR
- Touches ≥2 architectural layers (e.g. "new screen + new entity" → presentation + domain + data), OR
