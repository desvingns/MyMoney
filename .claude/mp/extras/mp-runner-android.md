# mp-runner-android - MyMoney deterministic runner preference

This role should be a deterministic execution step whenever possible.

- Prefer the plugin script `scripts/mp-runner-android.sh` when Bash is available.
- If the plugin script is unavailable, run the smallest explicit Gradle commands from the context
  capsule and return JSON with pass/fail, commands, and errors.
- Do not read monthly progress archives.
- Do not edit source files.
- Connected tests are not this role. Use `mp-runner-instrumented-android` or
  `scripts/mp-runner-instrumented-android.ps1` for a single device class.

## Known false negatives of `scripts/mp-runner-android.sh` in THIS repo

The plugin script cannot return `pass:true` here, no matter how healthy the build is. Both
causes are structural, not code defects — recognise them and do NOT spend the single allowed
auto-fix retry on them.

1. **`coverage` step asks for JaCoCo; MyMoney uses Kover.** The script runs
   `:app:jacocoUnitTestReport`, which does not exist. It reports
   `jacoco report missing` / `coverage=unknown` and forces `pass:false`.
2. **`tests` step parses a line Gradle only prints on failure.** It greps
   `N tests completed[, M failed]`, which `AbstractTestTask` emits only in its failure
   message. A fully green run therefore yields `tests="no test summary"` and `pass:false`.
   Verified 2026-07-26: a green run of 1631 tests contains zero such lines.
3. Scope: the script only covers `:app` (`testDebugUnitTest`, `detekt`, `lintDebug`). It never
   runs `ktlintCheck`, `koverVerify`, or the other 18 modules, so a green script result is also
   not evidence that the repo is green.

**Required protocol when the script fails on (1) or (2):** do not retry, do not "fix" anything.
Re-run the real CI gates and report a verified-manual pass with parsed evidence:

```bash
./gradlew --project-dir build-logic test --console=plain   # only when build-logic changed
./gradlew lintDebug testDebugUnitTest --console=plain --continue
./gradlew detekt ktlintCheck koverVerify --console=plain --continue
```

Read counts from `*/build/test-results/**/TEST-*.xml` (`tests`/`failures`/`errors` attributes),
never from `BUILD SUCCESSFUL`. Several of those gates are **pre-existing red on `main`**
(detekt, ktlint, four Kover floors). Judge a change by diffing the failure set against the
pre-change commit — a `git worktree` at the base commit plus the same commands — not by whether
the gate is green in absolute terms.

