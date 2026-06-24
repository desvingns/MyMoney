# Аккордеон-список операций под плиткой категории
Epic: dashboard-category-inline-records
Order: 02 of 03
Status: done
Depends-on: 01
Date: 2026-06-22

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Под нажатой плиткой категории раскрывается inline-аккордеон со списком её операций (из `state.expandedRecords`, заполняемого SPEC 01). Новый composable `CategoryRecordsInlineList` рендерит строки операций; строка показывает заметку/комментарий (`note`, при пустом — нейтральный плейсхолдер), сумму и дату. Раскрыт блок только у плитки, чей `categoryId == state.expandedCategoryId` (одна категория за раз). Блок встроен в общий скролл дашборда сразу под соответствующей плиткой в `CategoryTilesList`. Пока `expandedRecordsLoading` — допускается лёгкое состояние загрузки; пустой результат рендерится пусто-безопасно.
LAYERS: presentation
CHANGED_HINT:
  - feature/dashboard/.../components/CategoryRecordsInlineList.kt (new) — новый composable: принимает `records: List<Transaction>` (+ `loading: Boolean`), рендерит строку-операцию (`note` + сумма + дата); собственный row-composable внутри (нельзя тянуть `TransactionLeaf`/row из `:feature:transactionslist` — G10). Форматирование суммы/даты — через те же утилиты, что использует дашборд (`formatMoney`/locale, см. `DashboardScreen.kt:252-256`); дата из `Transaction.occurredAt: Instant` (G6)
  - feature/dashboard/.../components/CategoryTilesList.kt — пробросить `expandedCategoryId` + `records`(+loading) и под плиткой с совпавшим id отрисовать `CategoryRecordsInlineList`; ширина блока высчитывается дополнительно (учесть, что `DashboardScreen.kt:287-295` сейчас фиксирует высоту списка плиток по числу плиток — высота должна включать раскрытый блок или перестать быть фиксированной)
  - feature/dashboard/.../DashboardScreen.kt:278-296 — передать `state.expandedCategoryId`, `state.expandedRecords`, `state.expandedRecordsLoading` в `CategoryTilesList`; снять/пересчитать жёсткую `Modifier.height(...)` так, чтобы аккордеон не обрезался (G1)
  - тест: app/src/androidTest/.../feature/dashboard — тап по плитке показывает строки операций под ней; повторный тап скрывает; у нераскрытых плиток списка нет
TEST_TYPES: compose-ui
CONSTRAINTS:
  - НЕ импортировать row/leaf из `:feature:transactionslist` — `:feature→:feature` запрещён (G10); собственный row в `:feature:dashboard`.
  - `DashboardScreen.kt` также правится в SPEC 03 — этот раньше (same-file clash).
  - Существующая фиксированная высота списка плиток (`DashboardScreen.kt:287-295`) обрежет раскрытый блок — обязательно пересчитать/убрать (G1, иначе аккордеон не виден).
  - UI-тесты дашборда — `:app` androidTest, не Robolectric; после генерации тестов прогнать `:app:ktlintFormat`; `assertExists`/`onAllNodes` — методы-члены; для off-screen строк — `.performScrollTo()` перед взаимодействием.
  - Не дублировать загрузку данных в UI — список берётся только из state (источник — SPEC 01).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Inline-аккордеон операций под категорией

  Scenario: Раскрытый список виден под плиткой
    Given на дашборде есть плитка «Еда» с операциями за период
    When пользователь тапает плитку «Еда»
    Then под плиткой «Еда» отображается список операций
    And каждая строка показывает заметку, сумму и дату

  Scenario: Только одна категория раскрыта
    Given плитка «Еда» раскрыта
    When пользователь тапает плитку «Транспорт»
    Then список появляется под «Транспортом», а под «Едой» исчезает

  Scenario: Повторный тап скрывает список
    Given плитка «Еда» раскрыта
    When пользователь тапает «Еда» снова
    Then список под «Едой» больше не отображается

  Scenario: Операция без заметки
    Given у операции категории «Еда» пустой комментарий
    When список «Еды» раскрыт
    Then строка показывает нейтральный плейсхолдер вместо заметки, сумму и дату
```

## Gap / context
SPEC 01 готовит данные раскрытия в состоянии, но ничего не рисует. Этот SPEC добавляет сам аккордеон-список под плиткой и строку-операцию в `:feature:dashboard`, переиспользуя форматирование дашборда и обходя запрет `:feature→:feature`.

## Implementation links
- commit: 306c3927 (impl) + 62441922 (tokens) + c4622588 (androidTest compile fix) + dc589b2e (empty-container assertExists)
- files:
  - feature/dashboard/.../components/CategoryRecordsInlineList.kt (new)
  - feature/dashboard/.../components/CategoryTilesList.kt
  - feature/dashboard/.../DashboardScreen.kt
  - feature/dashboard/.../res/values{,-ru}/strings.xml
  - core/ui/.../theme/Spacing.kt, Color.kt
  - app/src/androidTest/.../feature/dashboard/components/CategoryRecordsInlineListUiTest.kt, CategoryTilesListUiTest.kt
  - app/src/androidTest/.../feature/dashboard/DashboardContentUiTest.kt (stale-updated)
  - app/src/androidTest/.../ImportFocusColdStartRegressionTest.kt (stale-updated: SPEC 01 ctor)
