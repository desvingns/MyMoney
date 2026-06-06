# Account icon registry in :core:designsystem + 20 new account icons
Epic: icon-library-expansion
Order: 01 of 04
Status: done
Depends-on: —
Date: 2026-06-05

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Centralise account-icon resolution into :core:designsystem and expand the selectable set from 4 to 24. Create a NEW `core/designsystem/.../icon/AccountIcons.kt` with `fun accountIcon(iconKey: String?): ImageVector` (when-expression; hybrid Material-Extended-Outlined + custom vectors; fallback `Icons.Outlined.AccountBalanceWallet`). Migrate the 4 existing keys out of TransferScreen's private fun (and fix `ic_account_savings`, which currently falls through to the fallback). Add 20 new `ic_account_*` keys. List all 24 keys in `ACCOUNT_ICON_KEYS`. Repoint TransferScreen to the designsystem function. Add a contract unit test. NO new icons are rendered in the picker yet — that is SPEC 02.
LAYERS: presentation
CHANGED_HINT:
  - NEW core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/icon/AccountIcons.kt — `fun accountIcon(iconKey: String?): ImageVector = when (iconKey) { ... else -> Icons.Outlined.AccountBalanceWallet }`. Custom vectors (if any) authored in this file using the SAME private helper style as CategoryIcons.kt's `categoryVector(name) { pathBuilder }` (24dp viewport, stroke 1.6, round cap/join, SolidColor(Color.Black)) — copy that helper here (keep it private; do NOT make categoryVector public).
  - EXISTING 4 keys (migrated, with savings fixed): ic_account_cash -> Icons.Outlined.Payments; ic_account_card -> Icons.Outlined.CreditCard; ic_account_bank -> Icons.Outlined.AccountBalance; ic_account_savings -> Icons.Outlined.Savings.
  - 20 NEW keys (suggested Outlined; resilience clause applies — substitute closest existing Outlined or author a custom vector if a name does not resolve):
      ic_account_wallet -> AccountBalanceWallet
      ic_account_debit_card -> Payment
      ic_account_credit_score -> CreditScore
      ic_account_cash_bills -> AttachMoney
      ic_account_coins -> Toll
      ic_account_ewallet -> Wallet
      ic_account_crypto -> CurrencyBitcoin
      ic_account_safe -> Lock
      ic_account_investment -> TrendingUp
      ic_account_loan -> RequestQuote
      ic_account_business -> BusinessCenter
      ic_account_currency_exchange -> CurrencyExchange
      ic_account_atm -> LocalAtm
      ic_account_cheque -> ReceiptLong
      ic_account_rewards -> Stars
      ic_account_insurance -> Shield
      ic_account_pension -> Elderly
      ic_account_gift_card -> CardGiftcard
      ic_account_transit_card -> Commute
      ic_account_family -> Group
  - feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/common/IconCatalog.kt — replace `ACCOUNT_ICON_KEYS` (currently 4) with all 24 keys (4 existing + 20 new) in a sensible display order.
  - feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/transfer/TransferScreen.kt — DELETE the private `accountIcon(iconKey: String?)` (L449-453) and its now-unused icon imports (AccountBalance, CreditCard, Payments — keep AccountBalanceWallet only if still used elsewhere in the file); import `com.kshavrin.mymoney.core.designsystem.icon.accountIcon`. The call site at L404 stays `imageVector = accountIcon(account?.iconKey)`. (:feature:transaction already depends on :core:designsystem.)
  - NEW core/designsystem/src/test/java/com/kshavrin/mymoney/core/designsystem/icon/AccountIconsTest.kt — mirror the structure of the existing CategoryIconsTest.kt. NB: ACCOUNT_ICON_KEYS lives in :feature:dictionaries, but :core:designsystem MUST NOT depend on a feature module — so declare an explicit local `allKeys` list of the 24 keys inside the test. Assertions:
      • unknown/empty/garbage key -> fallback (Icons.Outlined.AccountBalanceWallet);
      • repeated calls return a stable instance (lazy singletons / Material singletons);
      • every key EXCEPT `ic_account_wallet` resolves to a NON-fallback vector; `ic_account_wallet` INTENTIONALLY maps to the fallback instance (Icons.Outlined.AccountBalanceWallet — it IS the generic wallet glyph), exactly analogous to how CategoryIconsTest treats `ic_cat_other`. Document this in the test KDoc.
      • the keys other than `ic_account_wallet` are pairwise-distinct vectors.
TEST_TYPES: unit
CONSTRAINTS:
  - HYBRID: Material Icons Extended Outlined where faithful; custom 24dp stroke-only ImageVector ONLY where no faithful Material match. material-icons-extended already in libs.bundles.compose — NO dependency change.
  - accountIcon() must be a plain (non-@Composable) pure function returning ImageVector, so the registry stays pure-JVM unit-testable (same reason CategoryIcons.kt is non-@Composable).
  - NO DB migration, NO seeder change, NO domain change — iconKey is a String column; new keys are picker choices only.
  - Do NOT change rendering in the picker or list screens in this SPEC (that is SPEC 02). Only TransferScreen is repointed (its existing rendering is preserved).
  - English ids; no hardcoded strings; no comments unless WHY. Keep accountIcon's fallback = Icons.Outlined.AccountBalanceWallet (same as the old private fun). `ic_account_wallet` intentionally resolves to that SAME instance (it is the generic wallet glyph) — this mirrors `ic_cat_other` == fallback in CategoryIcons; it is NOT a bug, do not "fix" it.
=== END SPEC ===

## Gap / context
Account icons have no central registry — a private `accountIcon()` in TransferScreen maps only 4 keys and `ic_account_savings` (already in ACCOUNT_ICON_KEYS) silently falls through to the wallet fallback. To add 20 icons usably AND let the generic IconPickerSheet (in :feature:dictionaries) render them in SPEC 02, the resolver must live in :core:designsystem alongside `categoryIcon()`.

## Implementation links
- commit: f1aed7f (feat: centralise account icon registry with 24 keys) + 7ddf911 (test: cover account icon registry) — pushed to origin/main
- files:
  - core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/icon/AccountIcons.kt (NEW — `fun accountIcon(iconKey): ImageVector`, 24 keys, fallback AccountBalanceWallet)
  - feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/common/IconCatalog.kt (ACCOUNT_ICON_KEYS grown 4 → 24)
  - feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/transfer/TransferScreen.kt (private accountIcon() deleted; repointed to designsystem import)
  - core/designsystem/src/test/java/com/kshavrin/mymoney/core/designsystem/icon/AccountIconsTest.kt (NEW — 32 tests, all pass)
- verification: `:core:designsystem:testDebugUnitTest` 32/0/0 + `:app:testDebugUnitTest` SUCCESSFUL + `:app:lintDebug` SUCCESSFUL (detekt/jacoco n/a in this project). Reviewer + Verifier pass.
- deviations: developer to record any Material-icon-name substitutions in commit f1aed7f if the suggested Outlined names did not resolve (resilience clause).
