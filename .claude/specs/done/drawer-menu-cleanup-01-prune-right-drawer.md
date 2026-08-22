# Правый drawer дашборда: убрать ChartSettings и About, оставить 7 пунктов
Epic: drawer-menu-cleanup
Order: 01 of 02
Status: done
Depends-on: —
Date: 2026-08-20
Acceptance-matrix: ui_state=seven_items,about_path_via_settings,fits_without_scroll
Risk-signals: navigation, ui-tests-pinned, dead-code-cleanup

## SPEC
=== SPEC ===
TASK: refactor
PLATFORM: android
WHAT: Из правого drawer дашборда удалены пункты «Настройки графиков» и About. Остаются 7 пунктов в прежнем порядке: Search, Categories, Accounts, Financial Goals, Currencies, Settings, Support — все помещаются на экран (проверка на Pixel 5 API 34), verticalScroll сохраняется как страховка. Вычищена dead-цепочка About: событие AboutClicked, действие NavigateAbout и его ветка в NavHost, строки `right_drawer_about` / `right_drawer_chart_settings` (EN+RU), теги `RIGHT_DRAWER_ABOUT_TAG` / `RIGHT_DRAWER_CHART_SETTINGS_TAG`. Событие ChartSettingsClicked, флаг chartSettingsSheetOpen и ChartSettingsSheet НЕ трогаются — их использует SPEC-02 (D2). Путь к информации о приложении сохраняется через Settings → «О приложении» (G13).
LAYERS: presentation
CHANGED_HINT:
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/RightDrawerContent.kt:85-102 — удалить RightDrawerItem ChartSettings (:85-90) и About (:97-102); порядок остальных 7 не менять; `verticalScroll` (:49) и размеры рядов (Box 56dp :130, иконка 44dp :137) НЕ менять (G2, G9, D4)
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/RightDrawerContent.kt:148-156 — удалить константы `RIGHT_DRAWER_CHART_SETTINGS_TAG` и `RIGHT_DRAWER_ABOUT_TAG` (G2, G10)
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardState.kt:272 — удалить `DashboardEvent.AboutClicked`; `ChartSettingsClicked` (:299) оставить (G2, G3, D2)
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModel.kt:1129-1132 — удалить обработчик AboutClicked → `DashboardAction.NavigateAbout`; логику chartSettingsSheetOpen (:1149-1154) не трогать (G3, G5)
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardAction.kt:57 — удалить `NavigateAbout` (G5)
  - app/src/main/java/com/kshavrin/mymoney/navigation/MyMoneyNavHost.kt:89-90 — удалить ветку `NavigateAbout → Destinations.Settings` (G5)
  - feature/dashboard/src/main/res/values/strings.xml:83-84 + feature/dashboard/src/main/res/values-ru/strings.xml:83-84 — удалить `right_drawer_about` и `right_drawer_chart_settings` ПАРОЙ (G11)
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/components/DashboardDrawerContentUiTest.kt:40-71 — убрать пары label→event для chart_settings/about, поправить assertEquals по списку кликов (G10)
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/DashboardContentUiTest.kt:682-739,821-825,1448,1702-1712 — убрать 2 тега из destinationDrawerRowTags(), поправить bounds-порядок и клик-тесты (G10)
  - app/src/test/java/com/kshavrin/mymoney/DestinationsTest.kt:145-146 — проверить source-scan веток `DashboardAction.*`; поправить контракт, если он пинит NavigateAbout (G10, H4)
TEST_TYPES: unit, compose-ui
CONSTRAINTS:
  - Файлы не удаляем — правим in-place (политика проекта: archive/ вместо удаления).
  - verticalScroll остаётся; размеры рядов/зазоров не меняются (D4). Пункт Support не трогаем — единственный путь к Destinations.Support (D5, G13).
  - ChartSettingsClicked / chartSettingsSheetOpen / ChartSettingsSheet.kt остаются нетронутыми — SPEC-02 переиспользует их для авто-открытия шторки (D2, D1).
  - Same-file clash со SPEC-02: `MyMoneyNavHost.kt` и `DashboardViewModel.kt` — SPEC-02 стартует только после мержа 01 (порядок Order обязателен).
  - Строки EN/RU удаляются парой, иначе lint MissingTranslation = error и `L10nParityTest` упадёт (G11).
  - Unit-гейт НЕ компилирует src/androidTest — прибитые Compose-тесты drawer проверять compile/connected-задачами (G15); Roborazzi-голдены дашборда сняты с закрытыми drawer'ами и не ломаются (G15).
  - Visual-change device gate: верификация на подключённом Pixel 5 API 34 (sys.boot_completed=1) обязательна — работа визуальная (G16).
  - Комментарий :46-48 в RightDrawerContent про причину скролла обновить: скролл теперь страховка для низких экранов/fontScale при 7 пунктах (D4).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Правый drawer дашборда без дублей

  Scenario: Drawer показывает семь пунктов
    Given пользователь на дашборде
    When открывается боковое меню
    Then видны пункты Search, Categories, Accounts, Financial Goals, Currencies, Settings, Support
    And пункты «Настройки графиков» и About отсутствуют

  Scenario: Все пункты помещаются на экран
    Given устройство со стандартным экраном (Pixel 5)
    When открывается боковое меню
    Then все семь пунктов видны без прокрутки

  Scenario: Путь к информации о приложении сохраняется
    Given пользователь в настройках
    When открывается секция «О приложении»
    Then доступны «About & Help» и «Open-source licences»
```

## Gap / context
Drawer-пункт About сегодня ведёт на тот же `Destinations.Settings`, что и пункт Settings
(`MyMoneyNavHost.kt:89-90`) — чистый дубль; «Настройки графиков» переезжают в Settings (SPEC-02).
После удаления 2 пунктов меню впервые помещается на экран без прокрутки.

## Implementation links
- commit: 28d76d6c (feature), e1fd7537 (DrawerRowDefaults tokens), c823dcae (token wiring + reviewer fix)
- files: app/src/main/java/com/kshavrin/mymoney/navigation/MyMoneyNavHost.kt,
  feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardAction.kt,
  feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardState.kt,
  feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModel.kt,
  feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/RightDrawerContent.kt,
  feature/dashboard/src/main/res/values-ru/strings.xml,
  feature/dashboard/src/main/res/values/strings.xml,
  core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/drawer/DrawerRowDefaults.kt,
  app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/components/DashboardDrawerContentUiTest.kt,
  app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/DashboardContentUiTest.kt
