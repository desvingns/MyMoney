# Icon library expansion — epic overview
Epic: icon-library-expansion
Order: 00 of 04
Status: done
Depends-on: —
Date: 2026-06-05

## Goal
Grow the selectable icon library so users have far more icons when creating accounts and income/expense categories, in the SAME visual style as today's icons. Today there are 4 account icons and 17 category icons; the picker also renders icons as a 3-letter text abbreviation instead of the real glyph. This epic adds 20 account icons + 50 category icons (34 expense + 16 income) = **70 new icons**, centralises the account-icon registry into :core:designsystem, and makes the picker (and the account/category list circles) render the real ImageVector.

## Style contract (applies to every SPEC in this epic)
- **HYBRID sourcing (user decision):** use Material Icons Extended **Outlined** wherever a faithful semantic match exists; author a custom 24dp stroke-only `ImageVector` (via the existing `categoryVector()`-style helper: defaultWidth/Height 24dp, viewport 24x24, stroke `SolidColor(Color.Black)`, strokeLineWidth 1.6, round cap/join) ONLY for meanings with no faithful Material match.
- `material-icons-extended` is ALREADY in `libs.bundles.compose` — **NO dependency change** in any SPEC.
- `iconKey` is a plain `String` Room column — adding new keys needs **NO DB migration and NO seeder change**. New keys are new *picker choices* only; existing seeded data is untouched.
- Naming: account keys `ic_account_<name>`, category keys `ic_cat_<name>` (lowercase snake_case), matching the current convention.
- Each registry SPEC ships/extends a **contract unit test**: every key listed in the catalog (IconCatalog.kt) resolves through its registry function to a NON-fallback ImageVector.
- **Resilience clause:** suggested Material icon names below are guidance. If a suggested `Icons.Outlined.*` name does not resolve against the bundled material-icons-extended, substitute the closest existing Outlined icon (or author a custom vector) and record the substitution in the SPEC's "Deviations" footer at actualization. Prefer visually distinct glyphs; reuse the same Material vector for two keys only if no better match exists.

## SPECs (run via `/mp --feature --next` in Order)
| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `icon-library-expansion-01-account-icons-registry-and-20-new.md` | — | presentation | Create `core/designsystem/.../icon/AccountIcons.kt` (`fun accountIcon(iconKey): ImageVector`); migrate the 4 existing keys out of TransferScreen's private fun; add 20 new `ic_account_*`; list all 24 in `ACCOUNT_ICON_KEYS`; repoint TransferScreen. + AccountIconsTest. |
| 02 | `icon-library-expansion-02-icon-picker-renders-vectors.md` | 01 | presentation | IconPickerSheet renders the real ImageVector (resolver param) instead of 3-letter text; AccountsList / CategoriesList circles render the real icon too. |
| 03 | `icon-library-expansion-03-category-icons-expense.md` | 02 | presentation | Add 34 new expense `ic_cat_*` to `categoryIcon()` + `EXPENSE_ICON_KEYS`. Extend CategoryIconsTest. |
| 04 | `icon-library-expansion-04-category-icons-income.md` | 02 | presentation | Add 16 new income `ic_cat_*` to `categoryIcon()` + `INCOME_ICON_KEYS`. Extend CategoryIconsTest. |

## Why this ordering
02 (picker render) resolves account icons via `accountIcon()`, which only exists in :core:designsystem after 01 (the generic IconPickerSheet lives in :feature:dictionaries, which cannot see TransferScreen's private fun). 03/04 follow 02 so newly added category icons are immediately visible in the picker. 03 and 04 are independent of each other.

## Key facts (verified)
- Category registry: `core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/icon/CategoryIcons.kt` — `categoryIcon()` when-expression (12 Material Outlined + 4 custom `CategoryVectors.*` + `Icons.Outlined.Category` fallback) and the `categoryVector(name) { ... }` helper at L132-147.
- Account icons today: private `accountIcon()` in `feature/transaction/.../transfer/TransferScreen.kt:449-453` (cash/card/bank + fallback; `ic_account_savings` currently falls through to the fallback — fix to `Savings` in SPEC 01).
- Catalog lists: `feature/dictionaries/.../common/IconCatalog.kt` — `EXPENSE_ICON_KEYS` (15), `INCOME_ICON_KEYS` (2), `ACCOUNT_ICON_KEYS` (4).
- Picker: `feature/dictionaries/.../common/IconPickerSheet.kt:67` renders `Text(key…take(3))`.
- List circles: `AccountsListScreen.kt:133-137` (`take(2)`), `CategoriesListScreen.kt:249-256` (`take(3)`).
- Existing contract test: `core/designsystem/.../icon/CategoryIconsTest.kt`.

## Implementation links
- commit: <hash>
- files:  <changed files>
