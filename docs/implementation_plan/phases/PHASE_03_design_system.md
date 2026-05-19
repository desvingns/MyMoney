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
- [ ] Write `Typography.kt` — Roboto via `FontFamily.Default`. Sizes exactly per §6.2.
- [ ] Write `Shape.kt` and `Spacing.kt`.
- [ ] Write `Theme.kt`. Make `dynamicColor` parameter explicit and default `false` — TDD §6.1 implies no Material You dynamic colour (we keep brand mint-pink).
- [ ] Add `@ThemePreviews` meta-annotation.
- [ ] Create a preview helper composable `PreviewSamplePalette` showing each colour swatch + each type style. Use it for visual QA in Android Studio.
- [ ] Stub all six `:core:designsystem` component files. Each contains a one-line `@Composable` placeholder (returning an empty Box) so feature modules can `import` them now.
- [ ] Verify previews render both light + dark in Android Studio (`Split` view).
- [ ] In `MainActivity` (`:app`), replace the placeholder `MaterialTheme { ... }` from PHASE_02 with `MyMoneyTheme { ... }`. App now uses the real palette.
- [ ] Build + install; confirm status bar tinted, content uses mint background (`#F2FFF7`), text contrast looks right.
- [ ] Update PROGRESS.md.

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

(empty — fill at end of session)
