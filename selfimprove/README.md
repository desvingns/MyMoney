# selfimprove/ — the self-improvement loop

A closed **observe → reflect → propose → gate** loop for this project's AI agents. Principle from
the architecture review: *self-improvement is a loop, not a store* — persist the structured
signals the agents already emit (the runner / reviewer JSON verdicts), reflect on them, and route
fixes through a human gate.

## Two folders, two lifetimes
- **Telemetry (raw, gitignored):** `runs/*.jsonl` — one JSON event per agent/run, append-only,
  never hand-edited.
- **Distilled lessons (git-tracked signal):** `lessons.md` — the reflection step promotes
  repeatable patterns here; raw telemetry never pollutes the signal.

## The loop
1. **Capture (L1):** `./record-run.sh --agent <name> --verdict pass|fail|partial [--model M]
   [--metric "tests=42/0;cov=67%"] [--retry N] [--note "..."]` appends one JSON line. Wire it into
   the test/build runner so every agent run leaves a trace.
2. **Reflect (L2):** `./reflect.sh` aggregates the JSONL → `retro/retro-<date>.md` (per-agent
   pass-rate, failure clusters, flaky signals). Deterministic, no LLM.
3. **Propose (L3):** run `REFLECTION-PROMPT.md` in Claude/Codex — or the `selfimprove-retro`
   agent — to turn the retro into minimal, evidence-backed change proposals.
4. **Gate:** a human approves; the lesson is appended to `lessons.md` and the relevant agent
   prompt / rule is edited. Re-run and confirm the metric improves.

## Wire-in tip
This project's runner agent already returns a JSON verdict (e.g. `{"pass":true,"tests":"42 passed
/ 0 failed","coverage":"67%"}`). Map that one result to a single `record-run.sh` call at the end
of the runner/CI step, and the loop fills itself with real data — no manual logging.

_(The generator repo `claude-mobile-pipeline` runs the same loop wired into its `.ai/changes →
lib/sync` rail. Works for both Claude Code and Codex.)_
