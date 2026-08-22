# Поиск из drawer закрывает боковую панель
Epic: drawer-search-redesign
Order: 01 of 02
Status: done
Depends-on: —
Date: 2026-08-20
Risk-signals: navigation
Acceptance-matrix: scenario=search-open,search-back,other-drawer-navigation

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: При нажатии пункта «Поиск» в правом drawer боковая панель закрывается ДО открытия поиска
— как у всех соседних пунктов (Settings/Categories/…). Поверх дашборда открывается search-overlay,
возврат из поиска не воскрешает drawer.
LAYERS: presentation
CHANGED_HINT:
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModel.kt:1108 — в ветке `DashboardEvent.SearchClicked` вызвать `closeDrawers()` перед `emit(DashboardAction.NavigateSearch)`, по паттерну соседних веток (G13, G14)
  - feature/dashboard/src/test/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModelTest.kt:2052-2064 — НОВЫЙ отдельный тест (не расширение navigation-теста: там drawer никогда не открывается и state-assert пройдёт тривиально — warn light-eval): сначала `RightDrawerToggled` (rightDrawerOpen=true), затем `SearchClicked`, ассерт `rightDrawerOpen == false` && `leftDrawerOpen == false` + эмиссия `NavigateSearch`; существующий actions-order тест не трогать (G20, G59)
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/DashboardContentUiTest.kt — НОВЫЙ тест: открыть правый drawer → тап по `RIGHT_DRAWER_SEARCH_TAG` → drawer-контент исчез; добавление теста безопасно — пинятся только существующие строки/теги (G55, G56)
TEST_TYPES: unit, compose-ui, instrumented
CONSTRAINTS:
  - Закрытие drawer — ТОЛЬКО через флаги UiState (`closeDrawers()`), не через DrawerState (G14).
  - Порядок: сначала `closeDrawers()`, затем эмиссия `DashboardAction.NavigateSearch` — actions-порядок запинен в `DashboardViewModelTest` (G59), новых actions не добавлять.
  - Overlay-механику показа поиска (`searchOverlayOpen`, `contextualOverlay=true`) не трогать — D4 (G16).
  - Пункт drawer, его строку `right_drawer_search`, иконку и тег `RIGHT_DRAWER_SEARCH_TAG` не переименовывать — теги запинены в UI-тестах (G55, G56).
  - Instrumented-тест требует подключённый Pixel 5 API 34 (visual device gate, G61).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Закрытие drawer при переходе в поиск

  Scenario: Тап «Поиск» закрывает drawer
    Given открыт правый drawer дашборда
    When пользователь нажимает пункт «Поиск»
    Then drawer закрывается
    And поверх дашборда открывается поиск

  Scenario: Возврат из поиска не воскрешает drawer
    Given пользователь открыл поиск из drawer
    When пользователь нажимает «назад» в поиске
    Then отображается дашборд
    And drawer остаётся закрытым

  Scenario: Прочие пункты drawer не затронуты
    Given открыт правый drawer
    When пользователь нажимает любой другой пункт меню
    Then поведение закрытия и навигации не изменилось
```

## Gap / context
Корневой баг: `SearchClicked` — единственный из 9 пунктов drawer без `closeDrawers()` (G13);
поиск-overlay открывался поверх оставшегося открытым drawer.

## Implementation links
- commit: 5795aa7d, 8de1f24f
- files:
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModel.kt
  - feature/dashboard/src/test/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModelTest.kt
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/DashboardContentUiTest.kt
