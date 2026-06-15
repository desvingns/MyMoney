# Центр кольца: авто-ужатие «Остаток» + пилюля доход/расход
Epic: dashboard-neon-ring-redesign
Order: 04 of 06
Status: backlog
Depends-on: dashboard-neon-ring-redesign-01, dashboard-neon-ring-redesign-03
Date: 2026-06-15

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Содержимое центра кольца — метка «Остаток», крупная сумма `periodNet` (целое число, D7) с динамическим авто-ужатием шрифта, чтобы при любой длине не касаться внутреннего края кольца (D2), и пилюля под суммой с двумя строками: `↑ доход` (зелёный) и `↓ расход` (коралл). Вставляется в слот `centerContent` компонента 03.
LAYERS: presentation
CHANGED_HINT:
  - feature/dashboard/.../components/RingCenterContent.kt (new) — Column по центру: метка «Остаток» (string res), сумма через `MoneyFormatter.format(..., decimalDigits = 0, ...)` (D7, G12), затем пилюля (скруглённый контейнер) с `↑`+доход и `↓`+расход; авто-ужатие суммы через `BoxWithConstraints` + понижение `fontSize` пока текст не влезает во внутренний бокс (аналог scale-to-fit в `drawCenterTotals`, но обычным Compose Text) (G6, D2)
  - feature/dashboard/src/main/res/values/strings.xml + values-ru/strings.xml — «Остаток»/«Balance» и метки доход/расход, если их ещё нет (assumption — готового ресурса нет)
  - core/ui/theme/Typography.kt — стили из 01 (крупная сумма, метка, бейджи)
TEST_TYPES: unit compose-ui
CONSTRAINTS:
  - Авто-ужатие: при длинной сумме (напр. «1 234 567 ₽») шрифт уменьшается так, чтобы сумма И пилюля целиком были внутри внутреннего круга (D2); минимальный пол шрифта — читаемый, не 0.
  - Это обычный Compose Text (НЕ Canvas) → проверяемо compose-ui: `onNodeWithText` + ассерт уменьшения fontSize на узком боксе, либо captureToImage (G15).
  - Зависит от слота 03 (внутренний бокс); `periodNet`/`income`/`expense` приходят параметрами (из 02 через 06) — компонент без обращения к VM.
  - Округление до целого только на отображении (`decimalDigits = 0`), Money не менять (G12, D7); без хардкод-строк, EN+RU (G16).
  - ktlintFormat; тесты модуля прогнать вручную (G16).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Центр неонового кольца

  Scenario: Остаток показан целым
    Given periodNet = 37650.49
    Then в центре отображается "37 650 ₽" (без копеек) с меткой "Остаток"

  Scenario: Длинная сумма ужимается
    Given periodNet = 1234567 в узком внутреннем боксе кольца
    Then шрифт суммы уменьшен так, что текст не выходит за внутренний круг

  Scenario: Пилюля доход/расход
    Given доход 85000 и расход 47350
    Then под суммой видны "↑ 85 000 ₽" (зелёный) и "↓ 47 350 ₽" (коралл)
```

## Gap / context
В текущем донате центр показывает доход/расход двумя строками (Canvas drawText). Новый макет: метка «Остаток» +
крупный net + пилюля доход/расход, всё внутри кольца и с гарантией, что текст не упирается во внутренний край.

## Implementation links
- commit: <hash>
- files:  <changed files>
