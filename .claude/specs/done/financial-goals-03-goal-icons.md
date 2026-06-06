# Goal icon registry in :core:designsystem + 12 new goal icons + contract test
Epic: financial-goals
Order: 03 of 06
Status: done
Depends-on: —
Date: 2026-06-05

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Add a goal-icon registry so users can pick an icon when creating a financial goal, in the SAME
style as account/category icons. Create `core/designsystem/.../icon/GoalIcons.kt` with
`fun goalIcon(iconKey: String?): ImageVector` (when-expression; hybrid Material-Extended-Outlined +
custom vectors only where no faithful match; fallback `Icons.Outlined.Flag`). Add `GOAL_ICON_KEYS`
(12 themed + `ic_goal_other`) to the shared catalog, and a contract unit test. Registry only — wiring
into the picker/list happens in SPEC 04/05.
LAYERS: presentation
CHANGED_HINT:
  - NEW core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/icon/GoalIcons.kt —
    `fun goalIcon(iconKey: String?): ImageVector = when (iconKey) { ... else -> Icons.Outlined.Flag }`.
    Mirror `AccountIcons.kt` exactly (plain non-@Composable pure fun, nullable key). 12 themed keys
    (suggested Outlined; resilience clause — substitute the closest Outlined or author a custom 24dp
    stroke-only vector via the `categoryVector()`-style helper if a name does not resolve):
      ic_goal_home        -> Home
      ic_goal_car         -> DirectionsCar
      ic_goal_travel      -> Flight
      ic_goal_education   -> School
      ic_goal_emergency   -> HealthAndSafety
      ic_goal_wedding     -> Diamond
      ic_goal_gadget      -> Devices
      ic_goal_gift        -> Redeem
      ic_goal_health      -> MedicalServices
      ic_goal_retirement  -> BeachAccess
      ic_goal_renovation  -> Construction
      ic_goal_family      -> FamilyRestroom
    plus `ic_goal_other -> Icons.Outlined.Flag` (INTENTIONALLY == the fallback, the generic goal glyph —
    exactly like `ic_account_wallet`==fallback in AccountIcons / `ic_cat_other`==fallback in CategoryIcons).
  - feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/common/IconCatalog.kt —
    add `val GOAL_ICON_KEYS = listOf( ... )` listing all 13 keys (12 themed + `ic_goal_other`) in display order.
  - NEW core/designsystem/src/test/java/com/kshavrin/mymoney/core/designsystem/icon/GoalIconsTest.kt —
    mirror `AccountIconsTest.kt`. Declare a LOCAL `allKeys` list of the 13 keys (do NOT import from
    :feature:dictionaries — :core:designsystem must not depend on a feature module). Assertions:
      • unknown/empty/garbage key -> fallback (Icons.Outlined.Flag);
      • repeated calls return a stable instance (Material singletons / lazy custom vectors);
      • every key EXCEPT `ic_goal_other` resolves to a NON-fallback vector; `ic_goal_other` INTENTIONALLY
        maps to the fallback instance (document in the test KDoc);
      • the keys other than `ic_goal_other` are pairwise-distinct vectors.
TEST_TYPES: unit
CONSTRAINTS:
  - HYBRID: Material Icons Extended Outlined where faithful; custom 24dp stroke-only ImageVector ONLY where
    no faithful Material match (copy the private `categoryVector(name){ }` helper style from CategoryIcons.kt;
    keep it private). `material-icons-extended` is ALREADY in `libs.bundles.compose` — NO dependency change.
  - `goalIcon()` must be a plain (non-@Composable) pure function returning ImageVector, so the registry is
    pure-JVM unit-testable (same reason AccountIcons.kt / CategoryIcons.kt are non-@Composable).
  - NO DB migration, NO seeder change, NO domain change — `iconKey` is a String; these are picker choices only.
  - This SPEC does NOT render goal icons anywhere yet (picker/list wiring is SPEC 04/05). The icons become
    visible in the shared `IconPickerSheet` only after `icon-library-expansion-02` (the picker-renders-vectors
    SPEC, currently active) lands — that is a SPEC-05 dependency, not a code dependency of this registry.
  - Naming `ic_goal_<name>` (lowercase snake_case), matching the `ic_account_*`/`ic_cat_*` convention.
    English ids; no hardcoded strings; no comments unless WHY.
=== END SPEC ===

## Gap / context
There is no goal-icon registry. The create/edit form (SPEC 05) needs a set of selectable, on-theme goal
icons resolved through a pure `goalIcon()` (mirroring `accountIcon()`), plus a hardcoded-count contract
test (mirroring `AccountIconsTest`) so the catalog and registry can't silently drift.

## Implementation links
- commit: 6d2ec1a (`feat: add goal-icon registry and catalog keys`), pushed to origin/main
- files:
  - core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/icon/GoalIcons.kt (new — `goalIcon(iconKey): ImageVector`, fallback `Icons.Outlined.Flag`)
  - core/designsystem/src/test/java/com/kshavrin/mymoney/core/designsystem/icon/GoalIconsTest.kt (new — contract test, 21 tests, 0 failures)
  - feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/common/IconCatalog.kt (added `GOAL_ICON_KEYS`, 13 keys)
- verification: `:core:designsystem:testDebugUnitTest` green (GoalIconsTest 21/21); `:feature:dictionaries` compiles (main + unit test); Reviewer pass; Verifier pass (wiring N/A by design — picker/list deferred to SPEC 04/05).
