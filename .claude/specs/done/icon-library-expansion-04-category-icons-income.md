# 16 new INCOME category icons
Epic: icon-library-expansion
Order: 04 of 04
Status: done
Depends-on: 02
Date: 2026-06-05

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Add 16 new INCOME category icons to the shared `categoryIcon()` registry and to `INCOME_ICON_KEYS`, in the same Outlined 24dp style as today's category icons. Hybrid Material-Extended + custom. Extend the contract unit test. This completes the 50-icon category expansion (34 expense in SPEC 03 + 16 income here).
LAYERS: presentation
CHANGED_HINT:
  - core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/icon/CategoryIcons.kt — add 16 new branches to the `categoryIcon()` when-expression (before the `else` fallback). Custom vectors (if any) via the existing private `categoryVector()` helper + `CategoryVectors` object.
  - 16 NEW income keys (suggested Outlined; resilience clause from the epic overview applies):
      ic_cat_freelance -> Work
      ic_cat_bonus -> Stars
      ic_cat_dividends -> TrendingUp
      ic_cat_interest -> Percent
      ic_cat_rent_income -> HomeWork
      ic_cat_business_income -> BusinessCenter
      ic_cat_sale -> Sell
      ic_cat_refund -> AssignmentReturn
      ic_cat_gift_received -> Redeem
      ic_cat_cashback -> Paid
      ic_cat_pension -> Elderly
      ic_cat_scholarship -> WorkspacePremium
      ic_cat_investment_return -> ShowChart
      ic_cat_royalties -> Copyright
      ic_cat_tips -> MonetizationOn
      ic_cat_deposit_income -> Savings
  - feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/common/IconCatalog.kt — append all 16 new keys to `INCOME_ICON_KEYS` (existing ic_cat_salary, ic_cat_other stay; add new ones).
  - core/designsystem/src/test/java/com/kshavrin/mymoney/core/designsystem/icon/CategoryIconsTest.kt — MANDATORY updates (hardcoded counts will fail otherwise). This SPEC runs AFTER 03, so the test already has 51 keys:
      • Append the 16 new income keys to the `allKeys` list.
      • Update the count assertion to `assertEquals(67, allKeys.size)` and `assertEquals(66, nonOtherKeys.size)` (17 original + 34 expense + 16 income; nonOther = all minus ic_cat_other). If SPEC 03 has not run yet for any reason, set the counts to the actual current `allKeys` size + 16 — i.e. counts must always equal the real list size.
      • Existing loop tests auto-cover the new keys: `each non-other key returns a vector that is not the fallback` and the pairwise-distinct loop must stay green (no income key may share an ImageVector instance with any other category key — including the expense keys from SPEC 03).
TEST_TYPES: unit
CONSTRAINTS:
  - Style: Outlined line-art 24dp; any custom vectors use the existing categoryVector() helper, matching Hygiene/Pets/Health/Clothing exactly. Prefer glyphs distinct from the matching expense keys where it reads better (e.g. ic_cat_rent_income -> HomeWork vs expense ic_cat_rent -> Apartment); reusing a Material vector across an income/expense pair is acceptable only if no clearer match exists (note it in the Deviations footer).
  - material-icons-extended already in libs.bundles.compose — NO dependency change. NO DB migration / seeder / domain change.
  - categoryIcon() stays a non-@Composable pure function. English ids; no hardcoded strings; no comments unless WHY. Each new Material icon name needs its matching `import androidx.compose.material.icons.outlined.<Name>`.
  - If SPEC 03 already added a key with the same semantic to the EXPENSE list, do NOT duplicate the registry branch — only the catalog list membership differs. (All 16 keys here are new; none overlap SPEC 03's keys.)
=== END SPEC ===

## Gap / context
Only 2 income category icons exist (salary, other); creating an income category offers almost no icon choice. This adds 16 income-relevant icons. Registry + catalog additions are pure-presentation string-key additions; the contract test guards resolution.

## Implementation links
- commit: 5a85c5f, 2b33a27
- files:  core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/icon/CategoryIcons.kt; feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/common/IconCatalog.kt; core/designsystem/src/test/java/com/kshavrin/mymoney/core/designsystem/icon/CategoryIconsTest.kt; feature/dictionaries/src/test/kotlin/com/kshavrin/mymoney/feature/dictionaries/common/IconCatalogTest.kt
