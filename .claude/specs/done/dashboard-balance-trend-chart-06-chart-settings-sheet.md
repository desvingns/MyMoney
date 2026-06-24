# График тренда — шторка настроек + применение конфига
Epic: dashboard-balance-trend-chart
Order: 06 of 07
Status: done
Depends-on: 02, 04, 05
Date: 2026-06-21

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Bottom sheet «Настройки графика» с контролами: выбор стиля (≈20 из SPEC 04), тип периода графика («follow» = как дашборд, иначе day/week/month/year) + число точек, метрика (накопленный/остаток за период/доход+расход), тумблеры (вертикальные линии, подписи), правило цвета (всегда-зелёный/по-знаку/по-тренду), показать/скрыть график. Открытие по тапу на области графика; когда график скрыт — пункт «Настройки графика» в правом меню ⋮. Конфиг читается/пишется в `AppSettings` (SPEC 02) и применяется и к виду графика, и к расчёту тренда (тип периода/число точек/метрика → пересчёт точек).
LAYERS: presentation
CHANGED_HINT:
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/ChartSettingsSheet.kt (new) — `ModalBottomSheet` с контролами (D11/D12). (assumption)
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardState.kt — конфиг графика в state (из `AppSettings`) + флаг открытия sheet (G2, G15).
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModel.kt — события (открыть/закрыть sheet, изменить поле конфига); запись через `appSettingsRepository.update {}` (G15); добавить settings в существующий `combine(...)` потока (G7) и пересчитать тренд при смене конфига.
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardScreen.kt — хост sheet; тап по графику → `DashboardEvent` открытия (G1/G9); зоны тапа: график → настройки, сумма → операции (`BalanceCardClicked`, D10 assumption).
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/RightDrawerContent.kt:35 — пункт «Настройки графика» + `DashboardEvent.ChartSettingsClicked` + `DashboardAction`/обработчик (G16).
  - strings.xml + values-ru — ярлыки настроек, названия стилей/метрик/правил цвета.
TEST_TYPES: unit compose-ui
CONSTRAINTS:
  - Зависит от 02 (поля конфига), 04 (стили), 05 (интеграция). Same-file clash по `Dashboard*` с 05/07 — последовательно.
  - Конфиг persistent (DataStore) — менять ТОЛЬКО через `update {}` (G15); не задевать `firstPositiveSeen` guard (`AppSettingsRepositoryImpl.kt:28`).
  - `chartPeriodType="follow"` → тип периода графика повторяет текущий период дашборда; иначе независимый (O3).
  - actions — `replay=0` SharedFlow: НЕ пред-потреблять `awaitItem()` в тестах (memory: actions gotcha).
  - Скрытие графика (`chartVisible=false`): на дашборде вместо графика ничего/тонкая подсказка; настройки доступны из ⋮ (D13, H4).
  - Раннер компилирует androidTest (memory) — обновить `DashboardContentUiTest`/`RightDrawer*` тесты.
  - Без хардкод-строк; zero comments кроме неочевидного WHY.
=== END SPEC ===

## Acceptance
```gherkin
Scenario: Открыть настройки тапом по графику
  Given график виден на дашборде
  When пользователь тапает по области графика
  Then открывается шторка «Настройки графика»

Scenario: Смена стиля сохраняется между запусками
  Given в шторке выбран стиль "bars"
  When приложение перезапущено
  Then график рисуется стилем "bars"

Scenario: Скрытие графика и возврат через меню
  Given в шторке выключен тумблер «показывать график»
  Then график исчезает с дашборда
  And пункт «Настройки графика» доступен в правом меню ⋮

Scenario: Тумблеры линий и подписей
  Given выключены вертикальные линии и подписи
  Then график рисуется без 3 линий и без подписей периодов
```

## Gap / context
Реализует заявленную пользователем настраиваемость: тап по графику → шторка с видом/периодом/метрикой/скрытием,
с сохранением между запусками и входом из ⋮ когда график скрыт.

## Implementation links
- commit: 0130d65e (feat: sheet+wiring), ba9fd77b (ui tokens), 00bc9781/93b689fb (test import fixes), 4ba0194d (test scroll-to)
- files: feature/dashboard/components/ChartSettingsSheet.kt (new), feature/dashboard/ChartConfigMapping.kt (new), DashboardState.kt, DashboardViewModel.kt, DashboardScreen.kt, components/RightDrawerContent.kt, dashboard strings.xml (en+ru), core/ui theme Spacing.kt+Typography.kt; tests: ChartConfigMappingTest.kt, DashboardViewModelTest.kt, ChartSettingsSheetUiTest.kt (25/25 device), DashboardContentUiTest.kt (46/46 device)
