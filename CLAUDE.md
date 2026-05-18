# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# MyMoney

(One-sentence project description — replace this placeholder.)

## Project State Files

Three project-root markdown files track state and history (all committed):

- `STATE.md` — **live state**, refreshed by `cmp-docs` after every `/cmp` run. Current iteration, last completed work, recent commits, up-next.
- `ROADMAP.md` — **planned work**, ordered by iteration. Edit manually.
- `DOCUMENTATION.md` — **history**: product features, user flows, architecture decisions log.

Cross-session memory lives in `/c/Users/k.shavrin/.claude/projects/D--Pet-TDD-creater-MyMoney_app/memory`; `MEMORY.md` is its index and is auto-loaded into every session.

## Package

`com.kshavrin.mymoney`

## User-facing language

All UI strings (labels, buttons, hints, error messages) must be in **en**. Code identifiers stay in English.

## Stack & Versions (Android)

- Kotlin (latest stable) · AGP (current) · KSP (matching Kotlin)
- Compose BOM + Material3
- Hilt + hilt-navigation-compose
- Room + DataStore Preferences
- Coroutines + Lifecycle
- minSdk (project-specific) · targetSdk (current) · JVM 17

## Architecture (Clean Architecture — Android)

```
domain/
  model/          — pure Kotlin data classes
  repository/     — interfaces
  usecase/        — one class per use case
data/
  local/
    entity/       — Room entities
    dao/          — DAOs
    converter/    — Room TypeConverters
  mapper/         — entity ↔ domain mappers
  repository/     — *Impl classes
di/               — Hilt modules
presentation/
  navigation/     — Routes, BottomNavItem, AppNavHost
  screen/         — UiState → ViewModel → Screen + Content
  components/     — shared composables
  theme/          — Color, Type, Theme
  util/           — formatters / helpers
```

## Build (Android)

```bash
# KSP code generation (after changing Room/Hilt annotations)
./gradlew :app:kspDebugKotlin

# Full debug build
./gradlew :app:assembleDebug

# Unit tests
./gradlew :app:testDebugUnitTest

# Static analysis
./gradlew :app:detekt

# Screenshot tests (Roborazzi, if used)
./gradlew :app:recordRoborazziDebug
./gradlew :app:verifyRoborazziDebug
```

**JAVA_HOME** must point to a JDK 17+ runtime. Outside Android Studio, prefer its bundled JBR.
Cross-platform JBR detection (Linux, macOS, Windows under Git Bash):

```bash
for c in \
    "$HOME"/.jbr/jbr_jcef-17* \
    /snap/android-studio/current/jbr \
    /opt/android-studio/jbr \
    /Applications/Android\ Studio.app/Contents/jbr/Contents/Home \
    "/c/Program Files/Android/Android Studio/jbr" \
    "$LOCALAPPDATA/Programs/Android Studio/jbr"; do
  if [ -x "$c/bin/java" ] || [ -x "$c/bin/java.exe" ]; then
    export JAVA_HOME="$c"
    export PATH="$JAVA_HOME/bin:$PATH"
    break
  fi
done
```

Add to `~/.bashrc` (Linux), `~/.bash_profile` (Git Bash on Windows), or `~/.zshrc` (macOS).
The `/cmp` pipeline runs all shell commands through the `Bash` tool, so no PowerShell-specific setup is required.

## Testing Stack (Android)

- JUnit 4 · Turbine · kotlinx-coroutines-test
- Robolectric (DAO + Compose UI tests on JVM)
- Roborazzi (screenshot regression, optional)
- **Fakes only — no mocking framework.** See `app/src/test/.../data/Fake*.kt`



## Screens & Navigation

(populated as screens are added — see `DOCUMENTATION.md` → Screens for behavioural detail)

| Route | Screen |
|-------|--------|
| (none yet) | |

## Key Technical Decisions

(populated as architecture decisions accumulate — see `DOCUMENTATION.md` → Architecture Decisions Log for the full story)

- (none yet)
