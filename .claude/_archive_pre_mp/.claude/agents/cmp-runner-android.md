---
name: cmp-runner-android
description: Runs Gradle verification tasks for MyMoney (unit tests for every affected module, a headless androidTest-compile gate, detekt, optional Roborazzi screenshot verify) and returns structured pass/fail JSON. Never reads or modifies source files. Minimal and fast.
tools: Bash
---

# Runner Agent — MyMoney (Android)

Run verification tasks only. Do NOT read, write, or modify any source files.

## Environment (apply before every command)

Use the `Bash` tool for everything (Git Bash on Windows, native bash on Linux / macOS). Never invoke
PowerShell — the commands below are POSIX-only.

The Android Gradle Plugin requires JDK 17+. Prefer the JBR shipped with Android Studio over
the system JDK. The detection loop covers Linux (Ubuntu / snap / manual install), macOS
(Android Studio.app bundle), and Windows (default and per-user Android Studio installs as
seen through Git Bash):

```bash
# Detect JBR (Android Studio bundle). First match wins. Cross-platform.
for candidate in \
    "$HOME"/.jbr/jbr_jcef-17* \
    /snap/android-studio/current/jbr \
    /opt/android-studio/jbr \
    /Applications/Android\ Studio.app/Contents/jbr/Contents/Home \
    "/c/Program Files/Android/Android Studio/jbr" \
    "$LOCALAPPDATA/Programs/Android Studio/jbr"; do
  if [ -x "$candidate/bin/java" ] || [ -x "$candidate/bin/java.exe" ]; then
    export JAVA_HOME="$candidate"
    export PATH="$JAVA_HOME/bin:$PATH"
    break
  fi
done

cd "$(git rev-parse --show-toplevel)"
```

If no JBR is found, fall back to the system JDK — Gradle will fail loudly if it's
incompatible. Do not silently continue with an unset JAVA_HOME.

## Step 1 — Unit tests + androidTest compile gate (always run, one shell)

Run this as a **single** block — the affected-module list is reused across the two Gradle
invocations, so it must stay in one shell session. POSIX sh / bash 3.2-safe (works in Git Bash on
Windows and the default macOS bash); no `mapfile` / associative arrays.

```bash
# --- resolve the Gradle modules this change touched (working tree + staged + last commit) ---
CHANGED="$( { git diff --name-only HEAD; git diff --name-only --cached; git diff --name-only HEAD~1 HEAD 2>/dev/null; } | sort -u )"
MODULES=""
while IFS= read -r f; do
  case "$f" in
    app/*)       m=":app" ;;
    core/*/*)    m=":$(printf '%s' "$f" | cut -d/ -f1-2 | tr '/' ':')" ;;
    feature/*/*) m=":$(printf '%s' "$f" | cut -d/ -f1-2 | tr '/' ':')" ;;
    *) continue ;;
  esac
  case " $MODULES " in *" $m "*) ;; *) MODULES="$MODULES $m" ;; esac
done <<EOF
$CHANGED
EOF
case " $MODULES " in *" :app "*) ;; *) MODULES="$MODULES :app" ;; esac   # :app aggregates instrumented tests
MODULES="$(printf '%s' "$MODULES" | xargs)"; [ -z "$MODULES" ] && MODULES=":app"
echo "AFFECTED_MODULES: $MODULES"

# --- unit tests: every affected module (not just :app) ---
UNIT_TASKS=""; for m in $MODULES; do UNIT_TASKS="$UNIT_TASKS ${m}:testDebugUnitTest"; done
echo "=== UNIT TESTS:$UNIT_TASKS ==="
./gradlew $UNIT_TASKS --no-daemon --console=plain 2>&1 |
  grep -E "PASSED|FAILED|ERROR|tests completed|BUILD (SUCCESSFUL|FAILED)" | tail -n 60

# --- androidTest COMPILE gate (HEADLESS — needs no device). This is the gate that catches a
#     production-API rework leaving instrumented tests on deleted/renamed symbols. ---
AT_TASKS=":app:compileDebugAndroidTestKotlin"
for m in $MODULES; do
  [ "$m" = ":app" ] && continue
  d="$(printf '%s' "${m#:}" | tr ':' '/')/src/androidTest"
  [ -d "$d" ] && AT_TASKS="$AT_TASKS ${m}:compileDebugAndroidTestKotlin"
done
echo "=== ANDROIDTEST COMPILE:$AT_TASKS ==="
./gradlew $AT_TASKS --no-daemon --console=plain 2>&1 |
  grep -E "^e: |error:|Symbol not found|BUILD (SUCCESSFUL|FAILED)|FAILURE:" | tail -n 60
```

Parse:
- **Unit tests** — the Gradle summary line `N tests completed, M failed` is authoritative. If
  `BUILD FAILED` appears, scan back for `FAILED` lines to collect error context. If neither summary
  nor BUILD line shows up, re-run with `--info` to find what swallowed the output.
- **androidTest compile** — `BUILD SUCCESSFUL` with no `e:` lines → `"androidtest_compile": "ok"`.
  Any `e:` line or `BUILD FAILED` → the gate FAILS the whole run (`pass=false`); collect each `e:`
  line (file:line + message) into `errors`. This compile needs **no emulator** — never skip it for
  "no device". Running the tests on-device stays the `--device` flow's job; here we only compile them.

## Step 2 — Detekt (run only if configured)
<!-- numbered Step 2 historically; the unit-tests + androidTest-compile gate is Step 1 above. -->

Detekt is **optional** in MyMoney — the `io.gitlab.arturbosch.detekt` plugin is not currently
applied anywhere in the build, so `:app:detekt` does not exist. A missing task is **n/a, not a
failure**: never fail the run because the static-analysis tool isn't installed. Only real violations
fail this step.

```bash
DETEKT_OUT="$(./gradlew :app:detekt --no-daemon --console=plain 2>&1)"
if printf '%s' "$DETEKT_OUT" | grep -qE "Task '?detekt'? not found|not found in project ':app'"; then
  echo "DETEKT: n/a (plugin not applied in this repo)"
else
  printf '%s' "$DETEKT_OUT" | grep -E "issues found|Build (failed|successful)|FAILED|BUILD" | tail -n 20
fi
```

Parse:
- `DETEKT: n/a …` → `"detekt": "n/a (not configured)"`. This does **not** fail the run.
- Otherwise `Build successful` and zero "issues found" → `"detekt": "ok"`. A real `N issues found:`
  → extract the count and the next 10 lines (file:line: rule); that DOES fail the run.

## Step 3 — Screenshots (verify approved baselines only)

```bash
# Recording new baselines is a separate manual approval action.
./gradlew :app:verifyRoborazziDebug --no-daemon 2>&1 |
  grep -E "FAILED|BUILD" | tail -n 10
```

If `screenshot_record_needed=true`, verify the already-reviewed committed baseline; never
record baseline images inside this gate. If false, skip and set `"screenshots": "skipped"`.

## Return

Output exactly this JSON (no extra text):

**On success:**
```json
{"pass": true, "tests": "42 passed / 0 failed", "androidtest_compile": "ok", "detekt": "ok|n/a (not configured)", "screenshots": "ok|skipped"}
```

**On failure:**
```json
{"pass": false, "tests": "40 passed / 2 failed", "androidtest_compile": "ok|3 errors", "detekt": "3 violations", "screenshots": "skipped", "errors": ["app/src/androidTest/.../FooUiTest.kt:71 Unresolved reference 'Bar'", "TestClass.methodName: expected X but was Y", "..."]}
```

`pass` is `true` only when unit tests are green, **the androidTest source set compiles**, detekt is clean **or not configured**, and screenshots pass-or-skip. A non-compiling androidTest source set is a hard failure even though those tests are not executed here. A missing detekt task is never a failure.
