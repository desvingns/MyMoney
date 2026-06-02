# Transfer account selector stack (S03)
Epic: monefy-fidelity-audit
Order: 03 of 04
Status: done
Depends-on: -
Date: 2026-06-02

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Tighten the S03 transfer form so the account selector stack matches `03.jpg`: compact
Monefy-style source/target selector rows separated by the down arrow, with the existing green
amount/note area and dialpad FAB preserved. Affected surface: S03 transfer form.
LAYERS: presentation
CHANGED_HINT: feature/transaction/.../transfer/TransferScreen.kt (`AccountCard` call sites at lines
226 and 245 and `AccountCard` implementation at line 332 become compact selector rows instead of
elevated Material cards); reuse or add a small presentation-only selector component if needed;
preserve `ModalBottomSheet` keypad at line 289 and `FloatingActionButton` at line 178; update/add
Compose UI tests for source selector, target selector, swap/down-arrow layout, keypad FAB, and rate
panel preservation; screenshot evidence must use `03.jpg`.
TEST_TYPES: unit compose-ui screenshot-manual
CONSTRAINTS:
  - Do not change transfer calculation, source/target account events, swap behavior, validation, or
    AS-6/AS-7 rate handling.
  - Do not rework the add expense/income keypad-first flow or category grid; this SPEC is transfer
    selector chrome only.
  - Keep the dialpad FAB and keypad sheet behavior unless a later SPEC explicitly changes it.
  - No hardcoded strings/colors; EN/RU parity for any new labels or content descriptions.
  - Manual screenshot verification is required because this is primarily a density/alignment gap.
=== END SPEC ===

## Evidence
- Reference screenshot IDs: `03.jpg`.
- Affected surfaces: S03 transfer form source/target account selector stack.
- Current evidence source: `feature\transaction\src\main\java\com\kshavrin\mymoney\feature\transaction\transfer\TransferScreen.kt:226`
  and `:245` render `AccountCard`; line 332 implements it with Material `Card`.
- Prior shipped SPEC check: `redesign-monefy-fidelity-03-form-chrome` covered the broad transfer
  chrome and explicitly shipped amount/date/keypad work. The residual gap is the current Material-card
  account selector density and shape, not a replay of the whole form-chrome SPEC.

## Implementation links
- commit: 754c045, d1c565d, 9d7f4e3
- files:
  - feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/transfer/TransferScreen.kt
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/transaction/transfer/TransferScreenUiTest.kt
  - feature/transaction/src/test/java/com/kshavrin/mymoney/feature/transaction/transfer/TransferScreenContractTest.kt
  - build/visual-check/mymoney-transfer-selector-stack.png
