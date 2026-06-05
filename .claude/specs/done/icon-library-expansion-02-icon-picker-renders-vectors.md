# Icon picker & list circles render the real ImageVector (not a 3-letter abbreviation)
Epic: icon-library-expansion
Order: 02 of 04
Status: done
Depends-on: 01
Date: 2026-06-05

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: The icon picker bottom sheet (IconPickerSheet) currently renders each icon as the first 3 characters of its key (Text), not the glyph — so a large icon library is unusable. Make IconPickerSheet render the REAL ImageVector via an injected resolver, and make the account/category LIST circles render the real icon too. After this, the new account icons (SPEC 01) and category icons (SPEC 03/04) are visually selectable.
LAYERS: presentation
CHANGED_HINT:
  - feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/common/IconPickerSheet.kt — add parameter `iconFor: (String) -> ImageVector` to `IconPickerSheet(...)`. Replace the `Text(text = key.removePrefix("ic_cat_").removePrefix("ic_account_").take(3), ...)` block (L67-73) with `Icon(imageVector = iconFor(key), contentDescription = key, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))`. KEEP the outer Box's `.semantics { contentDescription = key }` (L55) — existing edit-screen UI tests select cells by key. Imports: androidx.compose.material3.Icon, androidx.compose.ui.graphics.vector.ImageVector.
  - feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/categories/CategoryEditScreen.kt — at the IconPickerSheet call site, pass `iconFor = { com.kshavrin.mymoney.core.designsystem.icon.categoryIcon(it) }`.
  - feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/accounts/AccountEditScreen.kt — at the IconPickerSheet call site, pass `iconFor = { com.kshavrin.mymoney.core.designsystem.icon.accountIcon(it) }` (accountIcon exists in :core:designsystem after SPEC 01).
  - feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/accounts/AccountsListScreen.kt:133-137 — replace the abbreviation `Text(text = row.account.iconKey.removePrefix("ic_account_").take(2), ...)` inside the coloured circle with `Icon(imageVector = accountIcon(row.account.iconKey), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))`. Import accountIcon from :core:designsystem.
  - feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/categories/CategoriesListScreen.kt:249-256 — replace the abbreviation `Text(text = category.iconKey.removePrefix("ic_cat_")…take(3), ...)` inside the coloured circle with `Icon(imageVector = categoryIcon(category.iconKey), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))`. Import categoryIcon from :core:designsystem.
  - app/src/androidTest/... — update any UI test that asserted the 3-letter/2-letter abbreviation text in the picker or list circles to instead assert the cell/row by its existing key contentDescription (picker cell semantics) or by the account/category name. The edit-screen icon-picker tests that select a cell by `contentDescription = key` keep working unchanged (semantics preserved). Add/extend a compose-ui test that IconPickerSheet renders Icon nodes (e.g. assert the picker shows N selectable cells with key contentDescriptions). Compile + run the affected androidTest classes in the same pass.
TEST_TYPES: compose-ui instrumented
CONSTRAINTS:
  - Depends on SPEC 01: `accountIcon()` must already exist in :core:designsystem. (:feature:dictionaries already depends on :core:designsystem; categoryIcon is already imported pattern there.)
  - PRESERVE the picker cell `semantics { contentDescription = key }` — it is the test/automation hook. Icon's own contentDescription is null (decorative) to avoid double-announcing; the cell semantics carry the key.
  - Pure presentation: no changes to ViewModels, domain, data, or the icon registries themselves.
  - Keep cell sizing/grid (Adaptive 64dp, 56dp circle) intact; only swap the inner Text for an Icon. English ids; no hardcoded strings; no comments unless WHY.
=== END SPEC ===

## Gap / context
IconPickerSheet.kt:67 and the two list screens render `iconKey.take(3)`/`take(2)` text, never the glyph — so even today's 17 category / 4 account icons show as "foo"/"cas". Adding 70 icons (SPECs 01/03/04) is pointless until the picker shows real icons. The render path uses the already-existing `categoryIcon()` and the SPEC-01 `accountIcon()` resolvers.

## Implementation links
- commit: 1c107b9 (fix: render dictionary icon vectors) + bcd2a75 (test: cover dictionary icon picker vectors)
- files:
  - feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/accounts/AccountEditScreen.kt
  - feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/accounts/AccountsListScreen.kt
  - feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/categories/CategoriesListScreen.kt
  - feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/categories/CategoryEditScreen.kt
  - feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/common/IconPickerSheet.kt
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/dictionaries/accounts/AccountEditContentUiTest.kt
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/dictionaries/common/IconPickerSheetUiTest.kt
- verification: device gate confirmed `Pixel_5_API_34`, SDK 34, boot complete. `mp-reviewer-android` recheck passed. `mp-runner-instrumented-android` passed `IconPickerSheetUiTest` 2/2 and `AccountEditContentUiTest` 3/3 on `Pixel_5_API_34` after AVD restart for a device-service crash. Local JDK 21 gate passed `:feature:dictionaries:assembleDebug :app:assembleDebug`. `mp-verifier-android` passed.
