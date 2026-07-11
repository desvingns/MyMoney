# Fix pre-existing ktlint red in L10nParityTest.kt
Epic: —
Status: done
Depends-on: —
Date: 2026-07-10

## SPEC
=== SPEC ===
TASK: bugfix
WHAT: `app/src/test/java/com/kshavrin/mymoney/L10nParityTest.kt` (added in 657bec12) has 9 ktlint violations, so `:app:ktlintTestSourceSetCheck` is red on main. Reformat the file to the repo's ktlint ruleset (class body not starting with a blank line, multiline expressions on their own line, multiline parameter lists one-per-line with a trailing comma, newline placement around `.` chains) without changing any assertion.
LAYERS: [presentation]
CHANGED_HINT: app/src/test/java/com/kshavrin/mymoney/L10nParityTest.kt
TEST_TYPES: unit
CONSTRAINTS: pure formatting — zero behavioural change; do not touch the string-parity assertions; verify with `./gradlew :app:ktlintTestSourceSetCheck --rerun-tasks` (task can be cached UP-TO-DATE while red); conventional `style:` commit
=== END SPEC ===

## Violations (from ktlint on 2026-07-10)
L10nParityTest.kt: 32:1 class body starts with blank line; 94:36 / 107:36 / 119:30 multiline
expression should start on a new line; 118:28 / 118:44 / 118:69 parameter/parenthesis newlines;
127:35 expected newline before '.'; 132:17 unexpected newline before '.'.

## Gap / context
Surfaced by `:app:ktlintTestSourceSetCheck --rerun-tasks` during
`review-2026-07-07-cold-start-budget`. The MP runner's `lint: ok` does not cover the test
sourceSet ktlint task, so this rode onto main unnoticed.

## Implementation links
- commit: a1dda732 (`style: reflow L10nParityTest to satisfy ktlint`)
- files: app/src/test/java/com/kshavrin/mymoney/L10nParityTest.kt
- verified: `:app:ktlintTestSourceSetCheck` green + `L10nParityTest` unit tests pass (BUILD SUCCESSFUL); pushed to main (515f5ca7..a1dda732)
