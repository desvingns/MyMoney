# mp-runner-android - MyMoney deterministic runner preference

This role should be a deterministic execution step whenever possible.

- Prefer the plugin script `scripts/mp-runner-android.sh` when Bash is available.
- If the plugin script is unavailable, run the smallest explicit Gradle commands from the context
  capsule and return JSON with pass/fail, commands, and errors.
- Do not read monthly progress archives.
- Do not edit source files.
- Connected tests are not this role. Use `mp-runner-instrumented-android` or
  `scripts/mp-runner-instrumented-android.ps1` for a single device class.

## Runner accuracy in THIS repo

**Fixed 2026-07-26** (plugin script rewritten; the two structural false negatives below are gone):

1. ~~coverage asked for JaCoCo while MyMoney uses Kover~~ — the tool is now auto-detected and a
   missing coverage plugin is `n/a`, not an error. `.claude/mp/config.json` sets
   `coverageTargetPct: 0` because the real gate is Kover `koverLineFloors` per module; the
   script's flat bar would contradict it (aggregate root coverage is ~23%).
2. ~~`tests` grepped `N tests completed`, which Gradle prints only on failure~~ — the verdict now
   comes from `*/build/test-results/<task>/TEST-*.xml`, variant-scoped.
3. ~~scope was `:app` only~~ — the default task is now repo-wide `testDebugUnitTest --continue`,
   so a compile break in any `:core:*` / `:feature:*` module is visible. Non-zero gradle exit with
   a clean XML set is reported explicitly as a compile/config failure.

**Still true — do not treat a green script result as proof the repo is green:**

- **`detekt: ok` and `lint: ok` can be lies.** Both parsers grep for one summary shape and fall
  through to `ok` when the task fails in any other shape. Verified 2026-07-25: the script said
  `detekt: ok` while project-wide `detekt` was `BUILD FAILED` across 10 modules.
- The script does not run `ktlintCheck` or `koverVerify` at all.
- Several gates are **pre-existing red on `main`** (detekt, ktlint, four Kover floors).

**When you need first-hand lint/coverage evidence** (a verified-manual pass, or before claiming a
gate is green), run only what the change actually touches:

```bash
./gradlew detekt ktlintCheck koverVerify --console=plain --continue
./gradlew --project-dir build-logic test --console=plain   # only when build-logic changed
```

Read counts from `*/build/test-results/**/TEST-*.xml` (`tests`/`failures`/`errors` attributes),
never from `BUILD SUCCESSFUL`. Judge a change by diffing the failure set against the pre-change
commit — a `git worktree` at the base commit plus the same commands — not by whether the gate is
green in absolute terms.

