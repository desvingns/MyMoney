# mp-tester-android — MyMoney extras

Read this **after** the `mp-tester-android` agent body (from the `mp-dev` plugin). These rules are MyMoney-specific.

## Source of truth for testing strategy

`MyMoney_TDD.md` §12 (lines 2553–2661) — read before writing test plans. Key extracts:

- **Unit tests**: JUnit 4 + Turbine + `kotlinx-coroutines-test`. ViewModels, UseCases, repository implementations (with fakes for downstream).
- **Instrumentation tests**: KSP `room-testing` for DAO + migration tests. Real device or emulator API 31+.
- **Compose UI tests**: `compose-ui-test-junit4`. Test screens with fake ViewModels.
- **Roborazzi (optional)**: Screenshot regression on JVM via Robolectric. PHASE_15 territory; earlier phases can skip.

## Database testing — never mock Room

The project seed memory `dao-test-config-trap.md` explains why. Reinforcing it here:

- **Unit tests touching Room**: use in-memory Room (`Room.inMemoryDatabaseBuilder`). Real DAO, real entities, real TypeConverters.
- **Instrumentation tests touching Room**: real on-device Room.
- **Mocking the DAO or Room database is a violation.** Mocks let bugs in migrations, TypeConverters, indices and foreign keys slip through silently. Fakes at the repository boundary are fine; below it everything is real.

## Fakes only at repository boundary

- Test ViewModels with `FakeFooRepository : FooRepository`. Fakes are hand-written, in `:core:testing` (shared) or in the same `:feature:*` module under `src/test/.../testdoubles/`.
- **No Mockito, MockK, Robolectric mocks of domain types.** Fakes only. This is a hard project testing rule — repeated here because it's load-bearing.
- Fake repositories should expose seam hooks for test setup: `fakeRepo.seed(...)`, `fakeRepo.simulateError(SyncError.…)`. Not setters that mutate hidden state.

## Test types per layer

| Layer / artefact | Test type | Where |
|---|---|---|
| ViewModel | unit (JUnit + Turbine + coroutines-test) | `feature/.../src/test/` |
| UseCase | unit | `core/domain/src/test/` |
| Repository impl | unit with fake data source | `core/<datasource>/src/test/` |
| DAO + migrations | instrumentation (real Room) | `core/database/src/androidTest/` |
| Composable | compose-ui (junit4) | `feature/.../src/androidTest/` |
| Snapshot | roborazzi (optional, PHASE_15) | `feature/.../src/test/` |

## Money & time in tests

- `BigDecimal` equality is tricky — use `BigDecimal.compareTo` (returns 0 for equal numeric value regardless of scale) or `BigDecimalCloseTo` Hamcrest matcher. **Never** assert on `BigDecimal.equals` directly (it compares scale too — `1.0 != 1.00`).
- Time: inject a `Clock` (or `() -> Instant`) into things that need now-ish. Tests pass a `Clock.fixed(Instant.parse("2026-05-18T10:00:00Z"), ZoneOffset.UTC)`. Never call `Instant.now()` / `LocalDate.now()` directly inside production code.

## Compose UI testing

- `createAndroidComposeRule<ComponentActivity>()` for screens that don't need a real navigation graph.
- `createComposeRule()` for content composables (no Activity).
- Use **test-tags** sparingly — prefer matching on `onNodeWithText(stringResource(R.string.…))` so localization issues surface. Test-tags only for things that have no semantic text (icons, decorative).
- Don't use `Thread.sleep` — use `composeTestRule.waitUntil { … }` with a timeout.

## Instrumented Compose UI on device (`--device` / the runbook)

For on-device screen coverage (`connectedDebugAndroidTest` on `Pixel_5_API_34`), read
`.claude/mp/extras/mp-runner-instrumented-android.md` and follow the canonical Pattern B template in
`docs/DEVICE_VERIFICATION_PLAN_FOR_SONNET.md` §5 verbatim (it is copied from the already-green
`app/src/androidTest/.../dashboard/DashboardContentUiTest.kt`).

- **Write exactly ONE `@Test` per `--device` slice** (or one new `@Test` in the screen's existing
  `*ContentUiTest`). Never batch device tests — they run one-at-a-time and get marked in the tracker
  one-at-a-time.
- Render the public `<Screen>Content(state, onEvent)` directly inside `MyMoneyTheme { }` with
  `createComposeRule()`; capture events into a `mutableListOf<…Event>()` and assert with
  `runOnIdle { assertEquals(listOf(Event.X), captured) }`.
- Prefer `onNodeWithContentDescription(targetString(R.string.…))` / visible-text matchers over test
  tags; resolve strings via the `targetString(...)` helper, never a literal.
- **Missing-seam policy:** if a control has no event/seam, you may only request a
  testTag/contentDescription/`public` seam from the developer — never invent UI or events. If the
  feature genuinely isn't in production, do not write the test; report the gap so it is logged in the
  tracker. Never weaken a test to get green.

## Roborazzi (optional)

If a SPEC includes `screenshot` in `TEST_TYPES` and the feature has a custom Compose component (e.g. `MonefyDonutChart`, `MonefyKeypad`):

```bash
./gradlew :feature:dashboard:recordRoborazziDebug    # manual capture after baseline approval only
./gradlew :feature:dashboard:verifyRoborazziDebug    # verify against baseline
```

Baseline images go under `feature/dashboard/src/test/roborazzi/`. Always commit them in the same commit as the production change.

## JBR / JDK path on Windows

The project seed memory `cross-platform-bash-jbr.md` has the auto-detect snippet — same one mirrored in `AGENTS.md`. For instrumentation tests, the Gradle daemon must use JDK 21; AGP 8.7+ enforces this. If you see "Unsupported class file major version" in test output → the daemon picked up JDK 17 or 11; restart daemon with explicit `JAVA_HOME`.

## Test naming

- Class: `<Subject>Test` or `<Subject>SpecTest`.
- Method: backtick-quoted Russian or English natural-language form: `` `given valid amount, when add expense, then balance decreases`() ``.
- Avoid `test_…` prefix style — JUnit 4 doesn't need it.

## Output format

Standard tester output: `{"test_files": [...], "screenshot_record_needed": bool}`. `screenshot_record_needed=true` only when a new Roborazzi baseline must be captured. For RED phase (TDD mode), include `"phase": "red"` and `"expected_failures": [...]`.
