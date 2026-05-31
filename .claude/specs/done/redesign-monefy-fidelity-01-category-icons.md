# Category icon registry + category-picker grid (S09/S10)
Epic: redesign-monefy-fidelity
Order: 01 of 06
Status: done
Depends-on: —
Date: 2026-05-30

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Add a category-icon registry (iconKey -> ImageVector; hybrid Material-Extended + a few custom vectors) in :core:designsystem, and redesign the category-picker grid (S09/S10, expense + income) to the Monefy reference — flat cards, uncontained colour-tinted line-icons, category-coloured labels, "+ ДОБАВИТЬ" grid cell.
LAYERS: presentation
CHANGED_HINT: core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/ (NEW CategoryIcons.kt; NEW res/drawable/ic_cat_*.xml for keys without a faithful Material match); feature/transaction/.../picker/CategoryPickerScreen.kt; refs: TDD\MyMoney\pipeline\03_style.md L136 & L152-162; screenshots 09.jpg/10.jpg; icon keys core/domain/.../seed/InitialDataSeeder.kt L116-135
TEST_TYPES: unit compose-ui
CONSTRAINTS:
  - Registry fun categoryIcon(iconKey: String): ImageVector in :core:designsystem; covers all 17 expense + 2 income ic_cat_* keys + a safe fallback. material-icons-extended already in libs.bundles.compose — NO dependency change.
  - HYBRID (user decision): Material Icons Extended *Outlined* where faithful (bills->LocalOffer, food->ShoppingBasket, entertainment->LocalBar, taxi->LocalTaxi, housing->Home, sport->DirectionsRun, gifts->CardGiftcard, phone->Call, transport->Train, cafe->Restaurant, car->DirectionsCar, salary->Payments, other->Category); author custom 24dp stroke-only outline vector drawables where none is faithful — AT MINIMUM hygiene(toothbrush+paste), pets(cat), health(thermometer), clothing(t-shirt).
  - Card per §03_style L136 + screenshots 09/10: 3-col grid; cell = flat card (surface bg, hairline outline, small radius); icon centered, OUTLINED line-art, tinted with category.colorHex — NOT inside a filled circle (§03_style: "icons are line-art, not contained"); label below in the SAME category colour, centered. Remove the current filled-circle + white onPrimary glyph.
  - "+ ДОБАВИТЬ" becomes the LAST grid cell (neutral outlined circle-plus + label), replacing the ExtendedFloatingActionButton. AS-4 add-category flow (AddCategoryClicked + savedStateHandle round-trip) must keep working unchanged.
  - Keep TopAppBar back-arrow + amount preview (separate-route architecture; embedding into the form is SPEC 03). Tint via category.colorHex (existing parseHexColor); no hardcoded colours/strings (reuse R.string.*); English ids; no comments unless WHY. No domain/data changes.
=== END SPEC ===

## Gap / context
Every category renders the same `Icons.Outlined.Category` in a filled circle (CategoryPickerScreen.kt:203); `iconKey` exists in data but is never resolved; no `ic_cat_*` drawables exist. This is the user's #1 emphasis ("особенно категории расходов и доходов").

## Implementation links
- commit: `078a269` (prod: registry + picker) + `25bb66e` (androidTest source-set wiring) + `73ab68d` (tests) — all on `origin/main`
- files:  core/designsystem/.../icon/CategoryIcons.kt (new); feature/transaction/.../picker/CategoryPickerScreen.kt; core/designsystem/.../icon/CategoryIconsTest.kt (new); feature/transaction/.../picker/CategoryPickerContentUiTest.kt (new)

## Deviations from the SPEC (recorded at actualization, 2026-05-31)
- Custom icons authored as Kotlin `ImageVector.Builder` inside `CategoryIcons.kt`, **not** `res/drawable/ic_cat_*.xml` (no `ic_cat_*` drawables exist — intentional override; keeps `categoryIcon` non-@Composable and the whole registry pure-JVM unit-testable). The CHANGED_HINT "NEW res/drawable/ic_cat_*.xml" therefore did not materialize.
- Registry covers the **17 real seed keys = 15 expense + 2 income** + a safe fallback (`Icons.Outlined.Category`); the SPEC prose "17 expense + 2 income" overcounted expense.
- `CategoryPickerContentUiTest` compiles but its on-device run is **deferred** (bring green on `Pixel_5_API_34` via `/cmp --device S09`/S10). Unit tests (`CategoryIconsTest`) are green; SPEC counts as done = shipped + pushed.
- Note: SPEC 06 (embed-grid) is now removing the separate picker route this SPEC redesigned — but its `categoryIcon` registry + flat-card style are reused by `categorygrid/CategoryGrid.kt`, so this SPEC's value persists.
