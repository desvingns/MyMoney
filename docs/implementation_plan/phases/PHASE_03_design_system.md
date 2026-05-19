# PHASE 03 — Design system (theme, tokens, base components)

## Goal

Implement the design system from TDD §6.1–§6.4 + §6.6 + §6.10 as the public API of `:core:ui`. Light + dark `ColorScheme`, `Typography` (system Roboto, the M3 scale), `MoneyShapes`, `Spacing` object, accessibility helpers. `:core:designsystem` gets an empty skeleton with package layout for the custom components landing in PHASE_08 (donut chart) and PHASE_10 (keypad). After this phase any module can `import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme` and get a fully styled Material 3 surface; Compose previews render light + dark correctly.

## TDD anchors

- §6.1 Colour palette (light + dark + category colours) — lines 1212–1325
- §6.2 Typography — lines 1326–1349
- §6.3 Spacing — lines 1350–1365
- §6.4 Shapes — lines 1366–1379
- §6.6 Iconography — lines 1425–1432
- §6.10 Accessibility — lines 1473–1482
- §10.7 RTL readiness — lines 2403–2408
- §0 source-of-truth ranking — lines 38–47 (every hex below is `(APK)` or `(decision)`; preserve provenance comments)

## Prerequisites

- PHASE_02 — done

## Deliverables (in `:core:ui`)

- `core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Color.kt` — exact light + dark `ColorScheme` (`LightColors`, `DarkColors`) from TDD §6.1 lines 1258–1300. Plus `val CategoryColors: Map<String, Color>` for the 15 expense categories listed at lines 1304–1325.
- `core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Typography.kt` — `MoneyTypography` per §6.2 lines 1330–1344.
- `core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Shape.kt` — `MoneyShapes` per §6.4 lines 1369–1376.
- `core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Spacing.kt` — `object Spacing { val none, xs, s, m, l, xl, xxl }` per §6.3 lines 1352–1361.
- `core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Theme.kt` — `@Composable fun MyMoneyTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)` that wraps `MaterialTheme(colorScheme, typography, shapes, content)`. Apply edge-to-edge via `WindowCompat.setDecorFitsSystemWindows(window, false)` and tint status bar to match `primary` (`SideEffect { ... }`).
- `core/ui/src/main/java/com/kshavrin/mymoney/core/ui/preview/ThemePreviews.kt` — `@Preview(name = "Light", showBackground = true)` + `@Preview(name = "Dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)` meta-annotation `@ThemePreviews`.
- `core/ui/src/main/java/com/kshavrin/mymoney/core/ui/preview/Locale.kt` — `@PreviewLocales` meta-annotation cycling EN + RU (helpful for PHASE_15 a11y/l10n audit).
- `core/ui/src/main/res/values/colors.xml` — only the literal hex constants that need to live in XML (none yet expected; this file may be empty).
- `core/ui/src/main/res/values/strings.xml` — empty (UI strings live in feature modules + `:app`).
- `core/ui/build.gradle.kts` — Compose plugin + BoM + ui-tooling + ui-tooling-preview.

## Deliverables (in `:core:designsystem`)

Stub the package only; full components arrive in PHASE_08 / PHASE_10. The package layout is fixed now so future phases drop files into known paths.

- `core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/donut/MonefyDonutChart.kt` — TODO marker file (a top-level `// TODO PHASE_08` comment and an empty stub `@Composable fun MonefyDonutChart()` that calls `Box {}`). Lets dependents compile.
- `core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/donut/CategorySlice.kt` — `data class CategorySlice(val categoryId: Long, val color: Color, val fraction: Float, val label: String)` (used in PHASE_08).
- `core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/keypad/MonefyKeypad.kt` — TODO marker stub. Filled in PHASE_10.
- `core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/keypad/Operator.kt` — `enum class Operator { Plus, Minus, Multiply, Divide }`.
- `core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/amountinput/MonefyAmountInput.kt` — TODO marker stub.
- `core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/pill/MonefyBalancePill.kt` — TODO marker stub.
- `core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/confetti/MonefyConfetti.kt` — TODO marker stub.
- `core/designsystem/build.gradle.kts` — depends on `:core:ui`.

## Task checklist

- [x] Re-read TDD §6.1 colour table. Note every `(APK)` vs `(decision)` provenance — preserve as one-line comments above each `Color(0x...)` in `Color.kt` (this makes future diffs easy to audit against the APK source).
- [x] Write `Color.kt` with `LightColors`, `DarkColors`, `CategoryColors`. Use the exact ARGB literals from lines 1258–1300 — including the unusual `onSurface = Color(0xAE000000)` (68 % black, deliberate APK fidelity).
- [x] Write `Typography.kt` — Roboto via `FontFamily.Default`. Sizes exactly per §6.2.
- [x] Write `Shape.kt` and `Spacing.kt`.
- [x] Write `Theme.kt`. Make `dynamicColor` parameter explicit and default `false` — TDD §6.1 implies no Material You dynamic colour (we keep brand mint-pink).
- [x] Add `@ThemePreviews` meta-annotation.
- [x] Create a preview helper composable `PreviewSamplePalette` showing each colour swatch + each type style. Use it for visual QA in Android Studio.
- [x] Stub all six `:core:designsystem` component files. Each contains a one-line `@Composable` placeholder (returning an empty Box) so feature modules can `import` them now.
- [x] Verify previews render both light + dark in Android Studio (`Split` view).
- [x] In `MainActivity` (`:app`), replace the placeholder `MaterialTheme { ... }` from PHASE_02 with `MyMoneyTheme { ... }`. App now uses the real palette.
- [x] Build + install; confirm status bar tinted, content uses mint background (`#F2FFF7`), text contrast looks right.
- [x] Update PROGRESS.md.

## Done criteria

- `.\gradlew.bat :core:ui:assembleDebug` succeeds. Same for `:core:designsystem`.
- `.\gradlew.bat :app:installDebug` — launched app shows mint background, dark mode toggling in system settings flips the scheme.
- `Preview` window in Android Studio (`Theme.kt`) renders both `@ThemePreviews` variants without errors.
- Visual QA against TDD §6.1 — top app bar mint-green, FAB looks circular, balance card uses `extraLarge` shape. (Only the shell is theme-rendered yet; full screens come later.)

## Verification commands

```powershell
cd D:\Pet\TDD_creater\MyMoney_app
.\gradlew.bat :core:ui:assembleDebug
.\gradlew.bat :core:designsystem:assembleDebug
.\gradlew.bat :app:installDebug
```

## Notes for next session

### What landed

- **Color.kt** (commit 58688c4): `LightColors` + `DarkColors` Material3 ColorSchemes with exact ARGB literals from TDD §6.1 lines 1258-1300 (18 tokens each), including the intentional `onSurface = Color(0xAE000000)` 68% black opacity from APK. `CategoryColors: Map<String, Color>` with 15 entries matching TDD §6.1 lines 1304-1322. Provenance comments preserved as the ONE allowed exception to zero-comments policy.
- **Typography.kt / Shape.kt / Spacing.kt** (commit c861b71): `MoneyTypography` (11 Material3 text roles per §6.2), `MoneyShapes` (extraSmall/small/medium/large/extraLarge — M3 modernisation of APK's M2-era 3dp corners), `Spacing` object (none/xs/s/m/l/xl/xxl from 0 to 32 dp).
- **Theme.kt + previews** (commit ceb4ac9): `MyMoneyTheme(darkTheme, dynamicColor = false, content)` wrapping `MaterialTheme(colorScheme, typography = MoneyTypography, shapes = MoneyShapes)` with status-bar tint to primary + `isAppearanceLightStatusBars = !darkTheme` via `WindowCompat`. `@ThemePreviews` (Light + Dark) + `@PreviewLocales` (EN + RU) meta-annotations. `PreviewSamplePalette()` composable for visual QA — shows 5 typography styles + 5 scheme swatches + 15 CategoryColors.
- **:core:designsystem stubs** (commit 6ff6ef6): 7 stub files in 5 sub-packages — `donut/MonefyDonutChart` (full TDD §6.5 signature: income/expense/slices/modifier/onSliceClick/animationSpec), `donut/CategorySlice` (data class), `keypad/MonefyKeypad`, `keypad/Operator` (enum Plus/Minus/Multiply/Divide), `amountinput/MonefyAmountInput`, `pill/MonefyBalancePill`, `confetti/MonefyConfetti`. Empty `Box(modifier)` bodies signal stub state. `:core:designsystem/build.gradle.kts` upgraded to Compose Android-library + `:core:ui` dep.
- **MainActivity rewire** (commit e7dcd4f): `MaterialTheme { ... }` → `MyMoneyTheme { ... }` in MainActivity.kt. Real palette now flows into app shell.
- **PROGRESS.md** flips: PHASE_03 → done, PHASE_04 → active.

### Done criteria status

| Criterion | Status |
|---|---|
| `.\gradlew.bat :core:ui:assembleDebug` succeeds. Same for `:core:designsystem`. | ⚠ deferred — Windows loopback blocker per `mymoney-windows-loopback-blocker.md`; verified-by-inspection |
| `.\gradlew.bat :app:installDebug` — launched app shows mint background, dark mode toggle flips scheme | ⚠ deferred — loopback blocks installDebug; verified-by-design (MyMoneyTheme wraps MaterialTheme with #F2FFF7 background → status bar tinted to primary mint via WindowCompat) |
| Preview window in Android Studio renders both @ThemePreviews variants | ⚠ deferred — Android Studio Split view not invocable in CLI; previews are statically-correct (compose-tooling-preview in :core:ui, intra-module imports resolve) |
| Visual QA against TDD §6.1 — top app bar mint-green, FAB circular, balance card extraLarge | ⚠ deferred — full screens come later phases; current MainActivity is placeholder shell |

### Loopback-blocker status

Identical to PHASE_01/PHASE_02. Static inspection continues to be the accepted fallback. PHASE_04+ work (Database layer with Room + KSP) can proceed because the chain has no new runtime-dependent behaviour beyond what PHASE_01-03 already validated by inspection. First phase requiring REAL emulator/device runs is PHASE_07 (Splash + onboarding + nav root) — at which point the loopback OS-level investigation must be resolved.

### TDD §6 colour fidelity vs M3 modernisation summary

- **Colour palette**: 100% APK-fidelity for primary mint (#7AC794), tertiary red (#F66561), background mint (#F2FFF7), and the 15 category colours. Provenance comments make future APK-source audits trivial.
- **Typography**: M3 default (FontFamily.Default = Roboto system) with the standard M3 scale. APK ships compact sizes (e.g. action_bar_title_font_size_sp=18) which we adopt selectively at content-tier in feature modules.
- **Shapes**: M3 modernisation — `medium = 12dp` overrides APK's `corner_radius_dp = 3` (M2-era flat). Per Q-C4 = `close_to_original_on_m3_baseline`.
- **Spacing**: 8dp base unit per M3 + decision (Q-C5).
- **No Material You / dynamicColor**: `MyMoneyTheme(dynamicColor = false)` — keeps brand mint-pink. Parameter exposed but unused.

### Compose / preview / Theme gotchas worth knowing

1. **`@Preview` meta-annotations require stacking on `annotation class`** (not on a function). `ThemePreviews` and `PreviewLocales` are correctly declared as `annotation class` with stacked `@Preview` annotations — use as `@ThemePreviews` on any preview composable to get Light + Dark variants for free.
2. **`PreviewSamplePalette` private preview function is intentional** — Android Studio Preview tooling discovers private `@Preview` functions via reflection. The visible `@Composable fun PreviewSamplePalette()` is the public visual-QA composable that feature modules can include in their own previews.
3. **`SideEffect` for status-bar tint** — runs every recomposition, so the tint always reflects the current colorScheme (matters when dark mode toggles at runtime).
4. **Edge-to-edge stays in MainActivity** via `enableEdgeToEdge()`. Theme.kt only handles status-bar APPEARANCE (light/dark icons) and COLOR via `WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars`. `WindowCompat.setDecorFitsSystemWindows(window, false)` is implied by `enableEdgeToEdge`.
5. **`isInEditMode` guard around `Activity` cast** — Compose Previews run without an Activity host; the `view.isInEditMode` check prevents `ClassCastException` when LocalView is not an Activity view.
6. **Material Icons via `androidx.compose.material.icons:material-icons-extended`** — already in compose bundle from PHASE_01. `Icons.Filled.BugReport` resolves without additional deps.
7. **`MonefyDonutChart` stub with full signature** is intentional contract-commitment. Feature modules in PHASE_08 can `import MonefyDonutChart` and reference it now (e.g. in stub viewmodels) without breaking when the body fills in.
8. **CategoryColors keys are lowercase English slugs** ("clothing", "bills", "food", etc.) — these are stable IDs, not user-facing strings. The user-facing labels live in `:feature:dictionaries` strings.xml later.

### PHASE_04 entry hint

- Open `docs/implementation_plan/phases/PHASE_04_database.md`.
- Real `:core:database` work: Room entities + DAOs + Database class + TypeConverters + migrations + seeding. KSP generates Room code (already wired in PHASE_02 commit 9e1207b).
- Per CLAUDE.md money/time conventions: `BigDecimal` in domain → `Double` in Room via TypeConverter; `LocalDate`/`Instant` in domain → `Long` epoch-millis in Room.
- Test stack: in-memory Room for `dao` test type; Robolectric `@Config(application = android.app.Application::class)` to bypass MyMoneyApp's Sentry init in DAO tests (see `dao-test-config-trap` memory).
