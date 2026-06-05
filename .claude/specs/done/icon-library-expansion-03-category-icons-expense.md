# 34 new EXPENSE category icons
Epic: icon-library-expansion
Order: 03 of 04
Status: done
Depends-on: 02
Date: 2026-06-05

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Add 34 new EXPENSE category icons to the shared `categoryIcon()` registry and to `EXPENSE_ICON_KEYS`, in the same Outlined 24dp style as today's category icons. Hybrid: Material Icons Extended Outlined where faithful, custom stroke-only ImageVector only where no faithful match. Extend the contract unit test.
LAYERS: presentation
CHANGED_HINT:
  - core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/icon/CategoryIcons.kt — add 34 new branches to the `categoryIcon()` when-expression (before the `else` fallback). Custom vectors authored via the existing private `categoryVector(name) { pathBuilder }` helper + a new entry in the private `CategoryVectors` object (same pattern as Hygiene/Pets/Health/Clothing).
  - 34 NEW expense keys (suggested Outlined; resilience clause from the epic overview applies — substitute closest existing Outlined or author a custom vector; the two marked CUSTOM have no faithful Material glyph):
      ic_cat_groceries -> LocalGroceryStore
      ic_cat_restaurant -> RestaurantMenu
      ic_cat_fastfood -> Fastfood
      ic_cat_coffee -> LocalCafe
      ic_cat_bar -> WineBar
      ic_cat_alcohol -> Liquor
      ic_cat_bus -> DirectionsBus
      ic_cat_tram -> Tram
      ic_cat_flight -> Flight
      ic_cat_bike -> DirectionsBike
      ic_cat_fuel -> LocalGasStation
      ic_cat_parking -> LocalParking
      ic_cat_shoes -> CUSTOM (a shoe / sneaker outline — no faithful Material glyph)
      ic_cat_electronics -> Devices
      ic_cat_books -> MenuBook
      ic_cat_rent -> Apartment
      ic_cat_utilities -> Bolt
      ic_cat_water -> WaterDrop
      ic_cat_furniture -> Chair
      ic_cat_repair -> HomeRepairService
      ic_cat_pharmacy -> LocalPharmacy
      ic_cat_doctor -> MedicalServices
      ic_cat_dentist -> CUSTOM (a tooth outline — no faithful Material glyph)
      ic_cat_gym -> FitnessCenter
      ic_cat_beauty -> Spa
      ic_cat_education -> School
      ic_cat_kids -> ChildCare
      ic_cat_baby -> ChildFriendly
      ic_cat_travel -> Luggage
      ic_cat_hotel -> Hotel
      ic_cat_subscription -> Subscriptions
      ic_cat_streaming -> LiveTv
      ic_cat_internet -> Wifi
      ic_cat_charity -> VolunteerActivism
  - feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/common/IconCatalog.kt — append all 34 new keys to `EXPENSE_ICON_KEYS` (existing 15 stay first; add new ones after, grouped sensibly).
  - core/designsystem/src/test/java/com/kshavrin/mymoney/core/designsystem/icon/CategoryIconsTest.kt — MANDATORY updates (the test has hardcoded counts that WILL fail otherwise):
      • Append the 34 new keys to the `allKeys` list (L46-64).
      • Update the count test `covers exactly the seventeen documented category keys` (L71-75): `assertEquals(51, allKeys.size)` and `assertEquals(50, nonOtherKeys.size)` (17+34 keys; nonOther = all minus ic_cat_other). Rename the test + its KDoc/comment away from "seventeen/sixteen" to the new totals (or a generic name).
      • The existing loop tests then auto-cover the new keys: `each non-other key returns a vector that is not the fallback` (must stay green — every new expense key must resolve to NON-`Icons.Outlined.Category`) and `the sixteen non-other keys are pairwise distinct vectors` (rename; it now asserts ALL non-other keys map to pairwise-DISTINCT vectors — so no two keys, new or old, may share the same ImageVector instance). This distinctness guard is intentional: if a substituted Material icon collides with another key, pick a different glyph.
      • (Optional) add explicit per-key `assertSame` cases for a few representative new keys, mirroring the existing per-key tests.
TEST_TYPES: unit
CONSTRAINTS:
  - Style: Outlined line-art, 24dp; custom vectors use the existing categoryVector() helper (stroke 1.6, round cap/join, SolidColor(Color.Black)) — match Hygiene/Pets/Health/Clothing exactly. Avoid colliding with an existing key's vector where a distinct glyph exists (e.g. ic_cat_restaurant must NOT reuse cafe's Restaurant — use RestaurantMenu; ic_cat_bar must NOT reuse entertainment's LocalBar — use WineBar; ic_cat_rent must NOT reuse housing's Home — use Apartment).
  - material-icons-extended already in libs.bundles.compose — NO dependency change. NO DB migration / seeder / domain change.
  - categoryIcon() stays a non-@Composable pure function. English ids; no hardcoded strings; no comments unless WHY. Each new Material icon name needs its matching `import androidx.compose.material.icons.outlined.<Name>`.
  - This SPEC is EXPENSE only; income keys are SPEC 04. Together SPEC 03 (34) + SPEC 04 (16) = 50 new category icons.
=== END SPEC ===

## Gap / context
Only 15 expense category icons exist; users want a much larger palette when creating expense categories. Registry + catalog are additive (string keys, no DB change); the contract test guards that every cataloged key actually resolves so a typo can't silently fall back to the generic Category glyph.

## Implementation links
- commit: 5803e85, be4effe
- files:  core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/icon/CategoryIcons.kt; feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/common/IconCatalog.kt; core/designsystem/src/test/java/com/kshavrin/mymoney/core/designsystem/icon/CategoryIconsTest.kt; feature/dictionaries/src/test/kotlin/com/kshavrin/mymoney/feature/dictionaries/common/IconCatalogTest.kt
