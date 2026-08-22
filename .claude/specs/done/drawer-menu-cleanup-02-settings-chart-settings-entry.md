# Пункт «Настройки графиков» в Settings → Appearance с авто-открытием шторки на дашборде
Epic: drawer-menu-cleanup
Order: 02 of 02
Status: done
Depends-on: 01
Date: 2026-08-20
Acceptance-matrix: ui_state=row_visible,sheet_auto_open,consume_once,back_to_settings
Risk-signals: navigation, nav-arg, ui-tests-pinned

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: В экране настроек, секция Appearance, появляется пункт «Настройки графиков». Тап переводит пользователя на дашборд, где поверх графика один раз открывается существующий ChartSettingsSheet — пользователь сразу видит, как меняется график (D1). Закрытие шторки не вызывает повторного авто-открытия; «назад» из дашборда возвращает в Settings. Сама шторка, ChartConfig и хранение CHART_* в DataStore не меняются (D2).
LAYERS: presentation
CHANGED_HINT:
  - feature/settings/src/main/java/com/kshavrin/mymoney/feature/settings/root/SettingsRootScreen.kt:121-135 — новый ListItem «Настройки графиков» в секции Appearance, зеркалит пункт Theme: ListItem + clickable(onOpenChartSettings) + semantics contentDescription = label (G5)
  - feature/settings/src/main/java/com/kshavrin/mymoney/feature/settings/root/SettingsRootScreen.kt:51-58 — колбэк `onOpenChartSettings` параметром в SettingsRootRoute/SettingsRootContent (G5)
  - feature/settings/src/main/res/values/strings.xml + feature/settings/src/main/res/values-ru/strings.xml — новый ключ (предложение: `settings_chart_settings`) ПАРОЙ в обеих локалях (G11)
  - core/ui/src/main/java/com/kshavrin/mymoney/core/ui/navigation/Destinations.kt:69-71 — `Destinations.Dashboard` получает optional arg `openChartSettings: Boolean = false` (G7; assumption O1)
  - app/src/main/java/com/kshavrin/mymoney/navigation/MyMoneyNavHost.kt:238-244 — провести `onOpenChartSettings` в SettingsRootRoute: `navController.navigate(Destinations.Dashboard(openChartSettings = true))`; чтение arg в composable<Destinations.Dashboard> через toRoute (G7; assumption O1)
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardScreen.kt:271-298 — при openChartSettings=true один раз эмитнуть открытие шторки (LaunchedEffect + сохранённый флаг потребления), переиспользуя существующий путь ChartSettingsClicked → chartSettingsSheetOpen (G1, G3; assumption O1)
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModel.kt:1149-1154 — переиспользовать обработчик ChartSettingsClicked; при необходимости передать стартовое значение arg в VM через SavedStateHandle (G3; assumption O1)
  - feature/settings/src/test/java/com/kshavrin/mymoney/feature/settings/root/SettingsRootScreenContentTest.kt — тест пункта по contentDescription (конвенция G12)
TEST_TYPES: unit, compose-ui
CONSTRAINTS:
  - Depends-on 01: оба SPEC-а правят `MyMoneyNavHost.kt` и `DashboardViewModel.kt` — стартовать только после мержа 01, параллельные правки запрещены.
  - ChartSettingsSheet и ChartConfig/ChartConfigMapping НЕ переносятся и НЕ дублируются; feature→feature зависимость запрещена (D2, G8) — шторка открывается только через навигацию на дашборд.
  - Arg потребляется ровно один раз: после закрытия шторки повторного авто-открытия быть не должно; при rotate/process-death поведение не хуже, чем у обычного открытия через бывший пункт drawer (assumption O1).
  - Back из дашборда после такого перехода возвращает в Settings (стандартный back stack; assumption O1).
  - Строки EN/RU добавляются парой — lint MissingTranslation = error, `L10nParityTest`, `SettingsStringsTest` (G11).
  - ViewModel-тесты с `savedStateHandle.toRoute<…>()` — только под `@RunWith(RobolectricTestRunner)` (гайдлайн проекта, SPEC-19).
  - Visual-change device gate: верификация на подключённом Pixel 5 API 34 обязательна (G16) — ручной сценарий: Settings → Appearance → «Настройки графиков» → шторка поверх графика.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Вход в настройки графиков из экрана настроек

  Scenario: Пункт виден в секции Appearance
    Given пользователь на экране настроек
    When отображается секция Appearance
    Then присутствует пункт «Настройки графиков»

  Scenario: Тап открывает шторку поверх графика
    Given пользователь на экране настроек
    When он нажимает «Настройки графиков»
    Then открывается дашборд
    And поверх графика открыта шторка настроек графика

  Scenario: Шторка авто-открывается один раз
    Given шторка открылась переходом из настроек
    When пользователь закрывает шторку
    Then повторного авто-открытия не происходит

  Scenario: Возврат ведёт обратно в настройки
    Given пользователь перешёл к шторке из настроек
    When он нажимает «назад»
    Then он возвращается на экран настроек
```

## Gap / context
После SPEC-01 у настроек графика нет точки входа (drawer-пункт удалён). Новый вход живёт в
Settings → Appearance и открывает ту же шторку поверх графика — механика «настроил и сразу
увидел результат» сохраняется (D1), новый экран не строится (D2).

## Implementation links
- commit: 282fdbee (feat), 546ddc6f (fix: stale test repair), ad55a215 (test: consume-once + row wiring), 39583d6f (test: back-to-settings navigation)
- files: app/src/main/java/com/kshavrin/mymoney/navigation/MyMoneyNavHost.kt, core/ui/src/main/java/com/kshavrin/mymoney/core/ui/navigation/Destinations.kt, feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardScreen.kt, feature/settings/src/main/java/com/kshavrin/mymoney/feature/settings/root/SettingsRootScreen.kt, feature/settings/src/main/res/values/strings.xml, feature/settings/src/main/res/values-ru/strings.xml, app/src/androidTest/java/com/kshavrin/mymoney/feature/settings/root/SettingsRootContentUiTest.kt, app/src/androidTest/java/com/kshavrin/mymoney/navigation/TypedNavigationDeviceTest.kt, app/src/test/java/com/kshavrin/mymoney/navigation/DestinationsTest.kt, feature/settings/src/test/java/com/kshavrin/mymoney/feature/settings/root/FactoryResetDialogContentTest.kt, feature/settings/src/test/java/com/kshavrin/mymoney/feature/settings/root/SettingsRootContentTest.kt, feature/settings/src/test/java/com/kshavrin/mymoney/feature/settings/root/SettingsRootScreenContentTest.kt, feature/dashboard/src/test/java/com/kshavrin/mymoney/feature/dashboard/DashboardRouteOpenOnceTest.kt
