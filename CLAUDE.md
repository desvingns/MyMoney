@AGENTS.md

# CLAUDE.md — Claude Code-specific notes (MyMoney)

The canonical project cheatsheet lives in `AGENTS.md` (imported above): project overview,
glossary, locked Monefy deviations, namespace, stack/versions, module structure, architecture
pattern, data/Hilt/persistence conventions, build commands, testing stack, comments policy,
file-deletion policy, project-state files note, where-to-find table, JBR snippet, graphify
rules. This file holds ONLY Claude-side deltas — never duplicate `AGENTS.md` content here
(duplication is exactly how the two files drifted before the 2026-07-05 dedupe).

## On-device / emulator testing (Claude runs on the Windows HOST)

Single machine: run the emulator in Android Studio (or attach a device over USB),
then call the instrumentation task directly — no NAT bridge, proxy, or helper script.
(The VirtualBox-guest NAT protocol in `AGENTS.md` is for Codex-in-guest sessions.)

```bash
adb devices                                    # confirm the emulator/device is listed
./gradlew :app:connectedDebugAndroidTest
./gradlew :core:designsystem:connectedDebugAndroidTest
./gradlew :core:database:connectedDebugAndroidTest
./gradlew :core:datastore:connectedDebugAndroidTest
```

A connected, booted device is **mandatory** before any on-device run — never fake or
skip it. If `adb devices` is empty, start the AVD and retry.

## Token-efficient Claude workflow

Keep sessions cheap without lowering rigor:

- **Code search → `Explore` subagent.** When a question means sweeping many files/dirs (find a symbol, trace a convention, locate call-sites), delegate to `Explore` and keep only its conclusion — don't fan raw file dumps into the main context. Use direct `Grep`/`Read` only for a known file or a single targeted lookup.
- **Model routing.** Mechanical work (renames, small/boilerplate edits, formatting) → Sonnet/Haiku. Reserve Opus for architecture, ambiguous design, and hard debugging.
- `/clear` between unrelated tasks; run Gradle with `--console=plain` and grep logs instead of dumping full build output into context.

## /mp plugin (Claude side)

This project uses the `/mp` marketplace plugin (`mobile-pipeline/mp-dev`), not the legacy `/cmp` fork — `/cmp` and its Codex `$cmp` mirror were archived under `.claude/_archive_pre_mp/` on 2026-06-03 (reversible; see that folder's README). `/mp` provides `--phase` and `--check` natively. The `mp-docs` agent is made **inert** here via `.claude/mp/extras/mp-docs.md` (it returns `{"committed":false}` and writes nothing), so `PROGRESS.md` stays the sole writer of project state.

## /mp auto-push policy (project override)

When a `/mp` pipeline run completes **successfully** — Reviewer pass, Runner green (or a verified-manual pass when the runner script throws its known false negative), and Verifier `pass:true` — **push to `main` automatically, without the Step 4.5 `y/N` gate.** Do NOT stop to ask "Ready to push?"; just print the manual checklist for the record and push. This overrides the orchestrator's default push-confirmation prompt for `--feature` (and the equivalent point in `--bugfix`). The gate is only re-introduced if Verifier returns `pass:false`, the run did not pass cleanly, or the user explicitly asks to hold a given run.

## Cross-session memory (Claude)

- On the Windows host: `~/.claude/projects/<flat-path>/memory/` for this checkout (auto-loaded).
- In the VirtualBox guest (user `desvi`, checkout `C:\Pet\MyMoney`): `C:\Users\desvi\.claude\projects\C--Pet-MyMoney\memory\`.
- Durable facts BOTH tools need belong in the git-tracked `.ai/memory/MEMORY.md` — never only in a tool-local memory.
