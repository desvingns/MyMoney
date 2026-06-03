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
  /cmp --spec <description>            — author SPEC(s) ONLY → write to .claude/specs/backlog/ (no code, no approval gate). Fills the backlog.
  /cmp --feature --next | --feature --backlog <slug> — implement a SPEC already in the backlog (skips create + approval): move backlog→active, run Phase 2, then →done.
  /cmp --phase                         — assisted phase progression: read PROGRESS.md, pick next unchecked task from the active PHASE_NN, synthesise SPEC, run the --feature pipeline, then tick the checkbox and append to PROGRESS log. MyMoney-specific.
  /cmp --check                         — read-only state validator: PROGRESS↔PHASE_NN↔TDD consistency. Makes no changes. MyMoney-specific.
  /cmp --device <Sxx>                  — one on-device test slice: pick the next un-green control for screen Sxx from DEVICE_VERIFICATION_PROGRESS.md, write ONE instrumented test, run it on Pixel_5_API_34 via the host-AVD helper, then update the tracker. MyMoney-specific.
  /cmp --plan [--sync|--bootstrap|--phase NN] — design→plan bridge: turn an /app-spec-creator spec bundle (or the TDD + 00_overview spine) into phases/*.md + PROGRESS/00_overview deltas via cmp-planner-android, behind a y/d/n write gate; migrates line-anchors to content-addressed (slug+hash). MyMoney-specific.

## Platform resolution

This project supports the following platforms (see `CLAUDE.md` → Stack section): **see `.claude/.cmp-version` `platforms:` field**.

When this project has **one** platform, agent names with `<platform>` suffix below resolve to that single platform — e.g. `cmp-developer-<platform>` means `cmp-developer-android` for an android-only project.

When this project has **multiple** platforms, every SPEC must include an explicit `PLATFORM: <name>` field, and orchestrator spawns the matching platform's agent for each step. If a task spans both platforms, run two SPECs sequentially (one per platform) — do not interleave.

## Visual autotest device pre-flight (MyMoney Android)

Run this hard gate before implementation/test execution for MyMoney tasks that are explicitly visual
and require visual/device autotests. Do not apply it to every presentation-layer change; apply it when
the SPEC/task/phase mentions visual, layout, theme, animation, screenshot, fidelity/reference
comparison, visual QA, `screenshot`, `compose-ui`/instrumented Compose UI, `--device`, or a phase
done-criterion that requires device-rendered visual autotests.

Use the documented host AVD `Pixel_5_API_34` (Android 14 / API 34). Before spawning any agent, confirm
the device is connected and booted: `ro.boot.qemu.avd_name` must be `Pixel_5_API_34`, SDK must be
`34`, and `sys.boot_completed` must be `1`. If the device is absent, wrong, offline, unauthorized, or
loses attachment, STOP immediately and ask the user to start/connect `Pixel_5_API_34` first. Correct
development cannot proceed without visual testing. Never continue blind, never replace the device
visual gate with JVM-only checks or screenshot baselines, and never claim visual tests ran or passed
without connected-device evidence.

## Startup

1. Read `CLAUDE.md` (at the repository root) for tech stack and architecture.
2. Read `docs/implementation_plan/PROGRESS.md` for the active phase and deferred OQ items. **In MyMoney, this — not STATE.md — is the authoritative state file.** STATE.md is a one-line stub redirect.
3. Confirm task type. If flag missing → ask: "Это новая фича / баг / brainstorm / фаза / проверка?"

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

**Mode select (read first).** If `--feature` carries `--next` or `--backlog <slug>` (or has no description while `.claude/specs/active/` holds a SPEC) → this is **backlog-consume mode**: the SPEC already exists and was approved when it entered the backlog, so SKIP Phase 0 + Phase 1 (no brainstorm, no questions, no SPEC re-draft, no approval gate) and jump to Phase 2:

1. Resolve the file — `--backlog <slug>` → the `.claude/specs/backlog/` file whose name matches `<slug>`; `--next` (or bare `--feature` with no description) → resume the SPEC already in `.claude/specs/active/` if one exists, else the top-ordered runnable file in `backlog/` (lowest `NN`; ignore `*-00-overview.md` index files).
2. Move it `backlog/ → active/`, set front-matter `Status: active`, announce which SPEC, then run **Phase 2** using the `=== SPEC === … === END SPEC ===` block read verbatim from the file.
3. On ship (Verifier pass / push), move it `active/ → done/`, fill `Implementation links` (commit + files), set `Status: done`.

If a free-text description was given instead → run Phase 0 → Phase 1 → Phase 2 as normal.

### Phase 0 — Brainstorm trigger (optional)

Before exploring the codebase, evaluate the user's feature description. Trigger heuristics:

- Description longer than ~150 characters, OR
- Touches ≥2 architectural layers (e.g. "new screen + new entity" → presentation + domain + data), OR
- User signals uncertainty ("thinking about", "not sure", "what's better", "options for", "how do I")

If any trigger fires → ask:
"This looks like a large feature. Run brainstorm before SPEC? (y/N)"

If **y** → spawn `cmp-architect` (same prompt as `--discuss` Phase 1), show the BRAINSTORM block, then ask:
"Which option do we take? (1 / 2 / 3 / cancel)"

- If user picks a number → proceed to Phase 1. Include the choice in `WHAT` or `CONSTRAINTS` of the SPEC so the developer knows which option was chosen.
- If user says "cancel" → stop. Do not generate a SPEC.

If no trigger fires, or user answers **N** → proceed directly to Phase 1.

### Phase 1 — Spec

Explore the relevant codebase area. Then ask ≤3 questions to close ambiguities:
- Affected screen(s)? New screen or extension?
- New use case or extend existing?
- New persistence? (Storage layer: Room entity / DataStore key / Core Data entity / etc.)
- UI validation rules? Edge states (loading/empty/error)?

When answers are clear, output SPEC block and wait for user approval:

```
=== SPEC ===
TASK: feature
PLATFORM: [android | ios — only required when project has multiple platforms]
WHAT: [one sentence]
LAYERS: [domain] [data] [presentation]
CHANGED_HINT: [existing files to read, or "explore"]
TEST_TYPES: unit [dao] [compose-ui] [screenshot]
CONSTRAINTS: [specific rules or "none"]
```

**Large features → split into a SPEC backlog.** Before emitting a single SPEC, judge the size. If the feature naturally decomposes into **two or more** independently-shippable SPECs (each roughly one focused slice — one screen group, one design layer, one subsystem), do NOT cram it into one. Instead:

1. Draft the full ordered set (SPEC 1..N), each its own self-contained SPEC block.
2. Write each as a file in `.claude/specs/backlog/` — see **SPEC backlog board** below for layout + file format — behind ONE y/N gate: *"Записать N SPEC в backlog? (y/N)"*. Add an `<epic-slug>-00-overview.md` index listing the ordered SPECs, their dependencies, and any cross-cutting notes.
3. Promote the first SPEC: move its file `backlog/ → active/`, then continue Phase 2 on it.
4. The remaining SPECs stay in `backlog/` for the next `/cmp --feature` run (the top-ordered one is next).

A single-SPEC feature skips the board — emit the SPEC inline as before.

**Do not proceed until user confirms SPEC.**

### Phase 2 — Implement

**Mode selection.** If the user passed `--tdd` after `--feature` → use the **TDD order** described at the end of this Phase (after Step 6). Otherwise use the **default order** below.

Before spawning any Phase 2 agent, apply the **Visual autotest device pre-flight (MyMoney Android)**
when the SPEC is explicitly visual and requires visual/device autotests. If the gate fails, stop
before Developer/Tester/Runner.

Spawn agents in sequence. Pass SPEC to each. Use `<platform>` resolution as described in the "Platform resolution" section above.

**Step 1 — Developer** (implement feature):
Spawn agent `cmp-developer-<platform>` with prompt:
```
Implement strictly per SPEC below. Return JSON: {"changed_files":[...], "commit":"hash"}

SPEC:
[paste SPEC block]
```

**Step 1.5 — Reviewer** (check layer boundaries):
Spawn agent `cmp-reviewer-<platform>` with prompt:
```
Check Clean Architecture boundaries for the files below.
Return JSON: {"pass": bool, "violations": [...]}

CHANGED_FILES:
[output from developer agent]
```

If Reviewer returns `pass=false` → stop immediately, show violations to user. Do NOT proceed to Tester.

**Step 2 — Tester** (write comprehensive tests):
Spawn agent `cmp-tester-<platform>` with prompt:
```
Write tests per SPEC and for CHANGED_FILES below.
Return JSON: {"test_files":[...], "screenshot_record_needed": bool}

SPEC:
[paste SPEC block]

CHANGED_FILES:
[output from developer agent]
```

**Step 3 — Runner** (verify everything passes):
Spawn agent `cmp-runner-<platform>` with prompt:
```
Run verification. screenshot_record_needed=[bool from tester]
Return JSON: {"pass": bool, "tests":"N passed/M failed", "detekt|lint":"ok|N violations", "screenshots":"ok|skipped|N failures"}
```

**Step 4** — If Runner returns `pass=false`, attempt ONE automatic fix:

Spawn `cmp-developer-<platform>` with prompt:
```
Fix the failing checks below. Do NOT add new logic or change behaviour — only make the checks pass.
Return JSON: {"changed_files":[...], "commit":"hash"}

SPEC:
[original SPEC block]

FAILED CHECKS:
tests:  [tests value from Runner]
lint:   [lint/detekt value from Runner]
errors: [errors array from Runner]
```

Then spawn `cmp-runner-<platform>` again with the same prompt as Step 3.
If the second run still returns `pass=false` → stop, show both failure reports to user and ask for guidance.

**Step 4.5 — Verifier** (static wiring checks + manual checklist gate before push):
Spawn agent `cmp-verifier-<platform>` with prompt:
```
Verify the implementation is wired into the app and generate a manual checklist.
Return JSON: {"pass": bool, "static_checks": {...}, "manual_checklist": [...]}

SPEC:
[paste SPEC block]

CHANGED_FILES:
[union of all changed files from Developer step(s)]
```

If Verifier returns `pass=false` → stop. Show `static_checks` failures to user and ask:
"Fix and continue? Describe the fix or run `/cmp --bugfix`."

If Verifier returns `pass=true` → print `manual_checklist` verbatim to the user. Do not
push yet: tested files must be committed first.

**Step 5** — Persist tests before the final push gate:
```bash
git add -- [each test_file returned by Tester]
git diff --cached --quiet || git commit -m "test: cover [feature description]"
```

**Step 6** — Final manual gate and push:

Ask:
"Pre-push verification: run the checklist on emulator/device. Ready to push all implementation and test commits? (y/N)"

- If user answers **N** → stop. Do NOT push. Keep local commits for review.
- If user answers **y** → push the complete branch:
```bash
# Token is provided via the GITHUB_TOKEN env var (configured in ~/.claude/settings.json,
# so it is available to every Bash invocation on all platforms).
# Reuse whatever remote is configured for `origin` instead of hard-coding the URL.
remote_path=$(git remote get-url origin | sed -e 's#^https://[^/]*@#https://#' -e 's#^https://##')
git push "https://x-access-token:${GITHUB_TOKEN}@${remote_path}" HEAD
```
If push fails → show the error and report that all commits remain local.

Docs remain **skipped in MyMoney**. The `cmp-docs` agent is inert (see
`.claude/agents/cmp-docs.md`). State is owned by `docs/implementation_plan/PROGRESS.md`. If
this run was invoked via `--phase`, the `--phase` workflow's post-pipeline hook ticks the
PHASE_NN checkbox and appends one line to PROGRESS.md. Otherwise no state file is touched.

---

#### TDD mode (--tdd flag, optional)

If the user passed `--tdd`, replace the default Step 1..Step 6 above with the renumbered order below. Prompt formats are identical to default mode unless noted — refer to the matching default step for the full prompt template.

**Step 1 — Tester (RED phase).** Spawn `cmp-tester-<platform>` with this prompt:

    red_phase=true

    Write failing unit tests (ViewModel + UseCase only) for SPEC.WHAT.
    Production code does not exist yet — that's the expected red signal.
    Return JSON per RED phase mode: {"test_files":[...], "screenshot_record_needed": false, "phase":"red", "expected_failures":[...]}

    SPEC:
    [paste SPEC block]

**Step 2 — Runner (expect red).** Spawn `cmp-runner-<platform>` with the default Step 3 prompt. **Interpret the result yourself:**

- If `tests` reports failures AND `lint/detekt` is `ok` AND the failures plausibly match `expected_failures` from Step 1 → red is correct, proceed to Step 3.
- If `tests` reports `0 failed` → tester didn't actually pin a contract. Stop and ask user.
- If failures look like compile errors on the **test code itself** (not on referenced-but-not-yet-existing production classes) → tester broke syntax. Stop and ask user.

**Step 3 — Developer (GREEN phase).** Spawn `cmp-developer-<platform>` with this prompt:

    green_phase=true
    TEST_FILES: [list from Step 1]

    Implement production code until the listed tests are green. Do not modify the tests.
    Return JSON: {"changed_files":[...], "commit":"hash"}

    SPEC:
    [paste SPEC block]

**Step 3.5 — Reviewer.** Same as default Step 1.5 (Clean Architecture boundaries on the new CHANGED_FILES).

**Step 4 — Tester (default phase, second pass).** Spawn `cmp-tester-<platform>` again with the default Step 2 prompt and the now-implemented CHANGED_FILES. This fills in `dao`, `compose-ui`, `screenshot` (or platform analogues) tests for any test types in SPEC.TEST_TYPES that the RED phase skipped.

**Step 5 — Runner (expect green).** Same as default Step 3. From here the chain matches the default order:

- **Step 6** — Auto-fix retry (same as default Step 4).
- **Step 6.5** — Verifier (same as default Step 4.5).
- **Step 7** — Persist tests (same as default Step 5).
- **Step 8** — Final manual gate and Push (same as default Step 6; docs remain skipped).

### Phase 3 — Report

```
feat: [description]
   Commit: [hash]
   Tests: [N passed]
   Lint:  ok
   Pushed: yes / failed: [reason]
   Files: [list of created/changed files]
```

---

## Workflow: --spec

Spec-authoring only — **fills the backlog, writes no code, runs no agents.** Use it to groom a large feature into ready-to-run SPECs ahead of time.

### Phase 1 — Draft
Explore the relevant codebase area (same as `--feature` Phase 1). Ask ≤3 questions ONLY if a choice is genuinely blocking (a strategy or scope fork). Then decide single vs. split:
- **Single SPEC** → one self-contained `=== SPEC === … === END SPEC ===` block.
- **Large feature** → the full ordered set (SPEC 1..N), each its own block, + an epic overview.

### Phase 2 — Write to backlog (no approval gate)
Write the SPEC(s) **straight** to `.claude/specs/backlog/` with `Status: draft` — do NOT stop for a "SPEC ок? (y/n)" gate (approval happens at implement time, in `--feature`). Use the file format in `.claude/specs/README.md`:
- Single → `backlog/<slug>.md`.
- Split → `backlog/<epic-slug>-NN-<short>.md` (NN = order) + `backlog/<epic-slug>-00-overview.md` (goal, ordered list, dependencies, cross-cutting notes).

`Status: draft` marks an auto-written, not-yet-human-reviewed SPEC. Refine by editing the files by hand, or just implement them later — `/cmp --feature --next` (or `--backlog <slug>`) runs them without re-creating or re-approving.

### Phase 3 — Report
```
spec: [topic restated]
   Wrote: N SPEC file(s) → .claude/specs/backlog/ (Status: draft)
   Files: [list]
   Next: /cmp --feature --next   (or --backlog <slug>) to implement
```

---

## Workflow: --bugfix

### Phase 1 — Locate

Read bug description. If reproduction steps unclear, ask only:
- Which screen / flow?
- Actual vs expected behaviour?

Skip questions if bug location is obvious.

### Phase 2 — Fix

Before spawning Developer, apply the **Visual autotest device pre-flight (MyMoney Android)** when the
bugfix is explicitly visual and requires visual/device autotests. If the gate fails, stop before any
fix work.

**Step 1 — Developer**:
Spawn agent `cmp-developer-<platform>` with prompt:
```
Fix bug per SPEC. Do not write tests; the Tester step owns regression coverage.
Return JSON: {"changed_files":[...], "commit":"hash"}

SPEC:
TASK: bugfix
PLATFORM: [android | ios — only required when project has multiple platforms]
WHAT: [root cause one sentence]
LAYERS: [affected layers]
CHANGED_HINT: [files to read]
TEST_TYPES: unit
CONSTRAINTS: regression test required from Tester, conventional commit fix:
```

**Step 1.5 — Reviewer** (if fix touches `presentation/` or `domain/`):
Spawn agent `cmp-reviewer-<platform>` with the changed files from Step 1.
If `pass=false` → stop, show violations.

**Step 2 — Tester**:
Spawn agent `cmp-tester-<platform>` with prompt:
```
Write regression tests for this bugfix and the changed production files.
Return JSON: {"test_files":[...], "screenshot_record_needed": false}

SPEC: [bugfix SPEC block]
CHANGED_FILES: [output from Developer]
```

**Step 3 — Runner**:
Spawn agent `cmp-runner-<platform>` with prompt:
```
Run verification. screenshot_record_needed=false
```

**Step 4** — If `pass=false`, attempt ONE automatic fix:

Spawn `cmp-developer-<platform>` with:
```
Fix the failing checks below. Do NOT change the bugfix logic — only make checks pass.
Return JSON: {"changed_files":[...], "commit":"hash"}

ORIGINAL SPEC: [bugfix SPEC block]
FAILED CHECKS: [errors from Runner]
```

Then spawn `cmp-runner-<platform>` again. If still `pass=false` → stop, show failures to user.

**Step 5** — Persist regression tests:
```bash
git add -- [each test_file returned by Tester]
git diff --cached --quiet || git commit -m "test: cover bugfix [description]"
```

**Step 6** — Confirm and push the complete bugfix branch:

Ask the user whether to push all implementation and test commits. Push only on `y`:
```bash
remote_path=$(git remote get-url origin | sed -e 's#^https://[^/]*@#https://#' -e 's#^https://##')
git push "https://x-access-token:${GITHUB_TOKEN}@${remote_path}" HEAD
```
If push fails → show the error and report that all commits remain local.

Docs remain skipped in MyMoney (`cmp-docs` is inert).

### Phase 3 — Report

```
fix: [description]
   Root cause: [one sentence]
   Commit: [hash]
   Tests: [N passed]
   Lint:  ok
   Pushed: yes / failed: [reason]
```

---

## Workflow: --phase (MyMoney-specific)

Assisted progression through the 15-phase implementation plan. Wraps the `--feature` pipeline with phase-state awareness.

### Phase 1 — Load context

1. Read `docs/implementation_plan/PROGRESS.md`. Locate the row marked **active phase** (status `in progress` or `active`). Extract `<NN>` (e.g. `01`).
2. Open `docs/implementation_plan/phases/PHASE_<NN>_*.md`.
3. Identify the first unchecked `- [ ]` task line. If none exist:
   - Report: "PHASE_<NN> has no remaining tasks. Run `/cmp --check` to verify completion criteria and update PROGRESS.md → status=done; advance the next phase to active. Then re-run `/cmp --phase`."
   - Stop.
4. Re-read the TDD anchor line ranges cited at the top of the phase file (typically a "TDD anchors" block referencing `C:\Pet\MyMoney\TDD\MyMoney\MyMoney_TDD.md` line ranges).
5. Identify any AS-x decisions referenced in the phase file's task list or notes.

### Phase 2 — Synthesise SPEC

Generate a SPEC block from the phase context (no questions — the phase file is the spec):

```
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: [first unchecked task line, verbatim from PHASE_<NN>]
LAYERS: [inferred from task language — see below]
CHANGED_HINT: docs/implementation_plan/phases/PHASE_<NN>_*.md (this phase file); C:\Pet\MyMoney\TDD\MyMoney\MyMoney_TDD.md lines [anchor ranges from phase file]; existing files mentioned in the task line if any
TEST_TYPES: [inferred from task — typically `unit` for domain/data work, `unit dao` for Room work, `unit compose-ui` for presentation work]
CONSTRAINTS: respect AS-x decisions cited in PHASE_<NN>; respect MyMoney conventions in CLAUDE.md and `.claude/cmp-mymoney/developer-extras.md`; English code identifiers; no comments unless WHY is non-obvious
=== END SPEC ===

PHASE CONTEXT:
- Active phase: PHASE_<NN> from docs/implementation_plan/PROGRESS.md
- Task being worked: [verbatim task line]
- TDD anchors: [line ranges]
- Relevant AS-x: [comma-separated list, or "none"]
```

**LAYERS inference heuristic:**
- "Add … entity / DAO / migration / database" → `data`
- "Add … repository / use case / domain model" → `domain`
- "Add … screen / Composable / ViewModel / Hilt module for presentation" → `presentation`
- Mixed → list both, e.g. `domain data` or `domain data presentation`.

Show the SPEC to the user and ask:
"SPEC выглядит ок? (y / r — re-synthesise, edit the task line manually first / n — cancel)"

- **y** → proceed to Phase 3.
- **r** → stop and let user edit `phases/PHASE_<NN>_*.md` task line, then re-run `/cmp --phase`.
- **n** → stop.

### Phase 3 — Run pipeline

Before running the default `--feature` Phase 2, apply the **Visual autotest device pre-flight
(MyMoney Android)** if the synthesised SPEC is explicitly visual and requires visual/device autotests.

Execute the **default --feature pipeline** (Step 1 through Step 5 from the `--feature` workflow above) with the synthesised SPEC.

**Skip Step 6 (Push) by default** — pushing is per-phase, not per-task. Ask:
"Phase task complete. Push now? (y/N — default N, push at end of phase)"

### Phase 4 — Record progress

After the pipeline completes successfully (Verifier `pass=true` or user confirms manual checklist):

1. In `docs/implementation_plan/phases/PHASE_<NN>_*.md`, change the task line from `- [ ] <task>` to `- [x] <task>`.
2. In `docs/implementation_plan/PROGRESS.md`, append to the session log:
   ```
   - YYYY-MM-DD: PHASE_<NN> — completed task "<task line>" (commit <hash>)
   ```
3. Check whether PHASE_<NN> now has zero unchecked tasks remaining. If yes → report:
   "PHASE_<NN> all tasks ticked. Run `/cmp --check` and the phase's verification commands (in PHASE_<NN>_*.md → 'Verification' section) to confirm done. Then update PROGRESS.md → status=done."

### Phase 5 — Report

```
phase: <NN> — completed task "<task line>"
   Commit: [hash]
   Phase progress: [M of total] tasks ticked
   Tests: [N passed]
   Pushed: yes / no (deferred to end of phase)
```

---

## Workflow: --check (MyMoney-specific)

Read-only validator. **Makes no changes.** Reports inconsistencies, exits.

### Checks

1. **Active phase resolves.** Parse `docs/implementation_plan/PROGRESS.md` → find the row with status `active` or `in progress`. Confirm exactly one such row exists. Extract `<NN>`.
2. **Phase file exists.** Confirm `docs/implementation_plan/phases/PHASE_<NN>_*.md` exists and is readable.
3. **Phase has unchecked tasks.** Confirm PHASE_<NN> has ≥1 line matching `^- \[ \]`. If zero → phase is complete; warn user.
4. **Previous phase done.** If `<NN>` > 01 → confirm PHASE_<NN-1> row in PROGRESS.md has status `done` AND PHASE_<NN-1>_*.md has zero `^- \[ \]` lines (all checkboxes ticked).
5. **TDD anchors resolve (content-addressed).** Prefer the new anchor form `slug:<slug> h:<hash>` emitted by `/cmp --plan`: if the TDD is reachable, confirm a heading with that `slug` exists and recompute the section's content hash — on mismatch report `§X.Y drifted since plan — run /cmp --plan --sync` (not a hard fail). For phase files still on the legacy `lines NNN-MMM` form, do the old range-within-line-count check. **If the TDD is not reachable on this host** (it lives at `C:\Pet\MyMoney\TDD\MyMoney\MyMoney_TDD.md`, which may be off-machine), report Check 5 as `skipped (TDD off-host)` rather than a false pass/fail — the slug form is what makes the rest of the plan host-independent.
6. **No drift in customisation layer.** Confirm:
   - `.claude/cmp-mymoney/{developer,reviewer,tester}-extras.md` all exist.
   - Each of `.claude/agents/cmp-{developer,reviewer,tester}-android.md` ends with the line `Read .claude/cmp-mymoney/<role>-extras.md before starting.`
   - `.claude/agents/cmp-docs.md` body contains the string `intentionally inert`.
7. **Memory seed present.** Confirm `~/.claude/projects/C--Pet-MyMoney/memory/MEMORY.md` exists and lists the `mymoney-*` memos.

### Report format

```
/cmp --check
=================
Active phase:     PHASE_<NN> ✓ / ✗ [reason]
Phase file:       phases/PHASE_<NN>_<slug>.md ✓ / ✗
Tasks remaining:  <M> unchecked ✓ / 0 unchecked (phase done) ⚠ / ✗ [missing file]
Previous phase:   PHASE_<NN-1> done ✓ / ✗ [reason]
TDD anchors:      <K>/<K> resolve ✓ / ✗ [list of unresolvable ranges]
Customisation:    extras layer + neutered docs ✓ / ✗ [missing/changed files]
Memory seed:      MyMoney memos indexed ✓ / ✗ [missing memos]
=================
Status: CONSISTENT / INCONSISTENT (N issues)
```

If `Status: INCONSISTENT` → enumerate fixes the user should apply. **Do not auto-fix.**

If `Status: CONSISTENT` → suggest next action: "Ready for `/cmp --phase` to work on PHASE_<NN>."

---

## Workflow: --device (MyMoney-specific)

One on-device instrumented-test slice for a single control. This is the device analogue of
`--bugfix`: it swaps the JVM runner (`cmp-runner-android`) for the device runner
(`cmp-runner-instrumented-android`) and ends by updating the device tracker. **One control per
invocation** — this enforces the runbook's "write one test → run on AVD → green → STOP" loop for a
less-capable model.

The authoritative runbook is `docs/DEVICE_VERIFICATION_PLAN_FOR_SONNET.md`; the live progress tracker
is `docs/DEVICE_VERIFICATION_PROGRESS.md`. Read both before starting.

### Phase 1 — Load context

1. Read `docs/DEVICE_VERIFICATION_PROGRESS.md`. For screen `<Sxx>` (the argument), find the next
   control whose cell is `Pending` (or the next un-green control listed in its row's notes). If the
   screen has no remaining un-green control → report that and stop.
2. Open the matching **slice card** in `docs/DEVICE_VERIFICATION_PLAN_FOR_SONNET.md` §8. It names the
   `*Content` composable, the `State`/`Event` types and paths, the controls→events mapping, any
   seams/skips, and the test-class FQN.
3. Apply the **Visual autotest device pre-flight (MyMoney Android)**. **Ensure a test device is connected — mandatory, never run without one.** Take the connection
   from the `mymoney-device-connection` memory memo (default: host AVD `Pixel_5_API_34` via
   `adb connect 10.0.2.2:5555`) and run the runbook §3 preflight. If `adb devices` is empty, the AVD
   is wrong, or the connection was lost → **STOP and ask the user (AskUserQuestion) where/how the test
   device is connected now** (address / serial / method), **write their answer to the
   `mymoney-device-connection` memo**, then retry the preflight. Do not spawn any agent until a device
   is confirmed connected and booted.

### Phase 2 — Add a seam only if the card says one is needed

If (and only if) the card flags a missing seam that policy permits closing (a `testTag`, a
`contentDescription`, or making a `*Content` public), spawn `cmp-developer-android`:
```
Add ONLY the minimal test seam below. Do NOT add UI, events, or behaviour. Return JSON: {"changed_files":[...], "commit":"hash"}

SPEC:
TASK: bugfix
PLATFORM: android
WHAT: expose <seam> on <Screen> for an instrumented test (testTag/contentDescription/public visibility only)
CHANGED_HINT: <Screen>Screen.kt
TEST_TYPES: compose-ui
CONSTRAINTS: device-test seam only — see .claude/cmp-mymoney/device-extras.md missing-seam policy; no new UI/events
```
Then spawn `cmp-reviewer-android` on the changed files. If `pass=false` → stop, show violations.
If the card says the control has **no** production seam → do NOT add one; record the gap in the
tracker notes (`<control> — no production seam, escalated`) and stop.

### Phase 3 — Write ONE test

Spawn `cmp-tester-android`:
```
Write exactly ONE instrumented Compose-UI @Test for the control below, using the canonical Pattern B template in docs/DEVICE_VERIFICATION_PLAN_FOR_SONNET.md §5. New file or one new @Test in the screen's existing *ContentUiTest. No batching. Return JSON: {"test_files":[...], "screenshot_record_needed": false}

CONTROL: <control + expected event/state from the slice card>
TEST CLASS: <FQN from the card>
```

### Phase 4 — Run it on the AVD

Spawn `cmp-runner-instrumented-android`:
```
Run this one instrumented test class on Pixel_5_API_34 and return parsed JSON.
TEST_CLASS: <FQN>
TASK: :app:connectedDebugAndroidTest
```

### Phase 5 — Record or recover

- **Green** (`pass=true`, `failures=0`, `skipped=0`): commit the test (`git commit -m "test: cover
  <Sxx> <control>"`); any seam from Phase 2 stays in its own `feat/fix:` commit. Then in
  `docs/DEVICE_VERIFICATION_PROGRESS.md` flip the screen's matrix cell to `Green: <control> N/N` and
  append one dated *Session Log* line. **Do not push** (device coverage pushes per session, not per
  control — same as `--phase`).
- **Red** (`pass=false`): if it's a real defect, spawn `cmp-developer-android` once for a minimal fix,
  then `cmp-runner-instrumented-android` once more. Still red → STOP, show the report, and write the
  gap in the tracker notes. Never weaken the test.

### Phase 6 — Report and stop

```
device: <Sxx> — <control> <green N/N | red | escalated>
   Test: <FQN>::<method>
   Commit: <hash> (test) [+ <hash> seam]
   Tracker: updated (matrix + session log)
   Next un-green control: <suggestion from the card / tracker>
```
Stop. Do not start the next control in the same run.

---

## Workflow: --plan (MyMoney-specific)

The design→implementation bridge. Turns a design source — an `/app-spec-creator` `spec/` bundle, or the legacy TDD + the `00_overview.md` phase spine — into the `phases/PHASE_NN_*.md` files + `PROGRESS.md`/`00_overview.md` deltas that `--phase` consumes. The orchestrator never authors these by hand: `cmp-planner-android` proposes, you approve, the orchestrator writes. Closes the formerly-manual TDD→phase gap and migrates fragile line-anchors to content-addressed (slug+hash) anchors.

### Modes
- `--plan --sync` (default) — reconcile the existing 15-phase plan with the current design: re-derive anchors, append/flag drift, **never** clobber `done` phases or ticked checkboxes.
- `--plan --bootstrap` — greenfield: generate the whole `phases/` tree from a readable design bundle (or an existing §1 phase map). Refused if neither is available.
- `--plan --phase NN` — regenerate ONE phase file (the common, safe case).

### Phase 1 — Plan
Spawn `cmp-planner-android` with:
```
mode: [sync|bootstrap|phase]      (add phase: "NN" when --phase NN)
design_source: [bundle dir or TDD path; default C:\Pet\MyMoney\TDD\MyMoney\MyMoney_TDD.md]
repo_root: [git rev-parse --show-toplevel]
```
Stamp `generated` with today's date in the prompt (the agent has no clock). It returns one `=== PLAN ===` block — parse it, do not summarise.

### Phase 2 — Preview + gate
Print a preview: files to create/merge, per-file merge summary (`preserved/updated/added/conflict`), the PROGRESS/00_overview deltas, and every `warnings[]` entry. Then ask:
"Plan выглядит ок? Записать/смёржить N файлов фаз + PROGRESS/overview deltas? (y / d — полный diff / n — отмена)"
- **d** → dump each `rendered_markdown` + a unified diff vs the on-disk file, then re-ask.
- **n** → stop, write nothing.
- **y** → Phase 3.

### Phase 3 — Gated write (orchestrator — the ONLY writes allowed here)
For each phase in the PLAN, merge `rendered_markdown` into `phases/PHASE_NN_*.md` honouring the sentinels: regenerate `<!-- cmp:plan:gen … -->` regions only, **never** touch `## Notes for next session`, and preserve checkbox state by `TASK-NN.k`. If a human edited inside a gen region (region hash ≠ stored `hash=`) → write the proposal to `phases/.proposed/PHASE_NN.md` instead and report it. Append the `progress_delta` rows + the one decisions-log line to `PROGRESS.md` (append-only — never rewrite prose or existing session-log lines). Apply the `overview_delta` to `00_overview.md`. Write nothing else — no source code, no TDD edits. (This write-set is exactly what the orchestrator is already permitted to touch under Rules.)

### Phase 4 — Report
```
plan: <mode> — <N> phase files (<created>/<merged>/<conflict→.proposed>)
   Anchors: content-addressed (slug+hash); <K> sections; drift: <none|list>
   PROGRESS: +<rows> rows, +1 decisions line    00_overview: +<edges>
   Conflicts to review: phases/.proposed/PHASE_*.md (if any)
   Next: /cmp --check, then /cmp --phase
```

---

## SPEC backlog board

`.claude/specs/` is a file-based task board for SPECs — full contract (layout, file format, lifecycle) in `.claude/specs/README.md`. It persists a **large feature that splits into several SPECs** so it is ordered and resumable across sessions, not stuck in one chat.

- `backlog/` — SPECs queued, not started (+ an `<epic-slug>-00-overview.md` index).
- `active/` — the SPEC being implemented now (normally one).
- `done/` — shipped SPECs, with `commit` + changed files filled in.
- A SPEC's **status is the folder it lives in**; an epic's SPECs share a filename prefix `<epic-slug>-NN-<short>.md` (NN = order).

**Lifecycle the orchestrator drives:** `--feature` Phase 1 writes a multi-SPEC feature's SPEC files into `backlog/` behind one y/N gate → on starting a SPEC, move `backlog/ → active/` and confirm it with the user before Phase 2 → on ship (Verifier pass / push), move `active/ → done/` and fill `commit` + `files`. Creating/moving these markdown files is a planning action the orchestrator may do directly; it never skips the human SPEC-approval gate. In MyMoney this board is for ad-hoc `--feature` epics — separate from the 15-phase plan under `docs/implementation_plan/`.

---

## Rules

- Orchestrator NEVER writes mobile production code (Kotlin/Swift/Compose/Gradle/Xcode build scripts) or tests.
- Orchestrator NEVER modifies application source files directly. (Writing markdown artifacts to `.claude/specs/` during `--discuss` is allowed — these are planning documents, not code. Ticking checkboxes in `phases/PHASE_NN_*.md` and appending session-log lines to `PROGRESS.md` during `--phase` is also allowed — these are state artifacts.)
- The orchestrator may create, edit, and move SPEC markdown files under `.claude/specs/{backlog,active,done}/` (the SPEC backlog board) — planning/state artifacts, not code. Moving a file between those folders is how a SPEC's status changes. This board is for ad-hoc `--feature` epics and is separate from the 15-phase plan under `docs/implementation_plan/`.
- `--spec <desc>` authors SPEC(s) and writes them straight to `.claude/specs/backlog/` with `Status: draft` — it runs NO agents and has NO approval gate (backlog grooming only).
- `--feature --next` / `--feature --backlog <slug>` implement a SPEC already in the backlog: it is treated as already created + approved, so Phase 0 + Phase 1 are SKIPPED — move `backlog/ → active/`, run Phase 2, then `active/ → done/`. `--next` resumes a SPEC already in `active/` if present, else takes the top-ordered backlog file (ignoring `*-00-overview.md`).
- All code changes happen inside spawned agents.
- If a spawned agent fails — stop the chain and report immediately.
- Maximum 3 clarifying questions before generating SPEC. `--phase` generates SPEC without questions (the phase file is the spec).
- `cmp-reviewer-<platform>` runs after every Developer pass, before Tester. A reviewer violation blocks the chain.
- Runner gets at most 2 runs per task (1 main + 1 retry after auto-fix). Never loop more than once.
- `cmp-verifier-<platform>` runs after Runner pass on `--feature` and `--phase` only. A static_checks failure blocks the chain; on pass, push waits for explicit user `y` after the manual checklist is shown. (`--bugfix` skips Verifier — bugfixes rarely touch wiring.)
- Push happens exactly once and only after implementation and test commits are present.
- `--tdd` flag (only on `--feature`) reorders Phase 2: Tester writes failing unit tests first (`red_phase=true`), Runner verifies the red, then Developer implements until green (`green_phase=true`). Opt-in only; default order remains developer-first. `--bugfix` delegates regression-test creation to Tester.
- `cmp-docs` agent is **inert in MyMoney** (`.claude/agents/cmp-docs.md` body replaced). All `--feature`/`--bugfix`/`--phase`/`--tdd` workflows skip the docs step. State is owned by `docs/implementation_plan/PROGRESS.md` exclusively.
- `cmp-runner-instrumented-android` runs the on-device suite (`connectedDebugAndroidTest`) for **one** test class via `scripts/run_connected_test_on_host_avd.ps1`. It is the **only** agent permitted to invoke PowerShell, and only for that helper — every other agent stays Bash-only. It trusts the parsed connected report, never "BUILD SUCCESSFUL". `cmp-runner-android` remains JVM-only and unchanged.
- Visual/device autotest work has a hard MyMoney Android pre-flight gate before implementation/test execution. Trigger it only for explicitly visual tasks (visual/layout/theme/animation/screenshot/fidelity/reference comparison/visual QA, `screenshot`, `compose-ui`/instrumented Compose UI, `--device`, or visual device done-criteria). If `Pixel_5_API_34` is not connected and booted, stop and ask the user to start/connect it; correct development cannot proceed without visual testing. Never continue blind or claim visual tests ran.
- `--device` slices update `docs/DEVICE_VERIFICATION_PROGRESS.md` (the device tracker), never `PROGRESS.md`. They never push. One control per invocation.
- `--phase`, `--check`, `--device`, and `--plan` are MyMoney-specific extensions, not from CMP source. They will not be modified by `bash CMP/bootstrap.sh --upgrade`. Future CMP versions may add new flags — manually re-apply these four (and the `cmp-runner-instrumented-android` + `cmp-planner-android` agents) if a future upgrade overwrites this file.
