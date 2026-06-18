# Плитки категорий: компонент плитки + скроллируемый список
Epic: dashboard-neon-ring-redesign
Order: 05 of 06
Status: done
Depends-on: dashboard-neon-ring-redesign-01
Date: 2026-06-15

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Плитка категории `CategoryTile` — скруглённый чип-иконка (тонированный фон), название слева, сумма справа, тонкая цветная полоска-прогресс снизу (ширина = доля от расхода, цвет = цвет категории); и `CategoryTilesList` — скроллируемый список плиток под кольцом (D4). Тап по плитке эмитит событие drill-down в операции категории (D9).
LAYERS: presentation
CHANGED_HINT:
  - feature/dashboard/.../components/CategoryTile.kt (new) — Row: `categoryIcon(iconKey)` в скруглённом чипе с tint, название (textPrimary), сумма (`MoneyFormatter`, целое — под макет), нижняя полоска шириной `fraction*maxWidth` цветом `parseHexColor(colorHex)` (G13, G3)
  - feature/dashboard/.../components/CategoryTilesList.kt (new) — `LazyColumn` по `expenseTiles` (из 02) со скроллом при переполнении (D4); пустой список → подсказка «нет расходов за период» (string res)
  - feature/dashboard/src/main/res/values/strings.xml + values-ru/strings.xml — строка пустого состояния (assumption)
TEST_TYPES: unit compose-ui
CONSTRAINTS:
  - Тап → `onTileClick(categoryId)` проброс наверх; реальная навигация (drill-down с текущим периодом) подключается в 06, переиспользуя существующий drill-down (G14) — компонент только эмитит событие, без обращения к VM/nav.
  - Иконки берём из существующего реестра `categoryIcon` (G13); НЕ добавлять новые ключи — `CategoryIconsTest` хардкодит 67/66 (G16).
  - `fraction`/цвет/иконка/сумма приходят из модели плитки (02); компонент чистый.
  - Скролл: компонент — `LazyColumn`; финальная стыковка со скроллом дашборда — в 06 (следить, чтобы не было вложенного вертикального скролла-конфликта) (D4).
  - Без хардкод-строк, EN+RU; ktlintFormat; тесты модуля прогнать вручную (G16).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Список плиток категорий

  Scenario: Плитка показывает иконку, имя, сумму и полоску
    Given расходная категория "Продукты" 15200 с долей 0.32
    Then плитка показывает иконку в чипе, "Продукты", "15 200 ₽" и полоску шириной ~32% цветом категории

  Scenario: Скролл при переполнении
    Given расходных категорий больше, чем влезает на экран
    Then список вертикально скроллится

  Scenario: Тап открывает операции категории
    When тап по плитке "Продукты"
    Then эмитится onTileClick с categoryId этой категории

  Scenario: Пустой период
    Given за период нет расходов
    Then показана подсказка пустого состояния, а не пустой экран
```

## Gap / context
Сейчас разбивка категорий живёт иконками НА донате; списка плиток под кольцом нет. Новый макет переносит
разбивку в скроллируемый список плиток (иконка + имя + сумма + полоска-прогресс) — этот SPEC даёт компоненты.

## Implementation links
- commit: 401b7710
- files:  feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardColors.kt; feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardState.kt; feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModel.kt; feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/CategoryTile.kt; feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/CategoryTilesList.kt; feature/dashboard/src/main/res/values/strings.xml; feature/dashboard/src/main/res/values-ru/strings.xml; feature/dashboard/src/test/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModelTest.kt; app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/components/CategoryTilesListUiTest.kt
