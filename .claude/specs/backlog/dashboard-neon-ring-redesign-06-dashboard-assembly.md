# Сборка дашборда: кольцо + центр + плитки + скролл, рестайл топ-бара, FAB +15%
Epic: dashboard-neon-ring-redesign
Order: 06 of 06
Status: backlog
Depends-on: dashboard-neon-ring-redesign-01, dashboard-neon-ring-redesign-02, dashboard-neon-ring-redesign-03, dashboard-neon-ring-redesign-04, dashboard-neon-ring-redesign-05
Date: 2026-06-15

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Собрать новый `DashboardContent`: `NeonRingChart` (слот центра = `RingCenterContent`) + `CategoryTilesList` под ним в общем вертикальном скролле; убрать отдельную `DashboardBalancePanel` (переехала в центр); рестайл топ-бара под неон (события сохранить); FAB +15% (через токен из 01); обновить androidTest дашборда под новую структуру.
LAYERS: presentation
CHANGED_HINT:
  - feature/dashboard/.../DashboardScreen.kt:114-312 — заменить donut Box на `NeonRingChart(fraction=state.ringFraction){ RingCenterContent(periodNet, income, expense) }`; добавить `CategoryTilesList(state.expenseTiles, onTileClick=…)`; контент в общий скролл; убрать вызов `DashboardBalancePanel` (:369-426) (G1, G7)
  - feature/dashboard/.../DashboardScreen.kt — `onTileClick(categoryId)` → существующий drill-down в операции с текущим периодом (G14)
  - feature/dashboard/.../components/TwoFabLayout.kt:30-120 — размеры подтянутся из токена 01 (FAB 104dp); при необходимости поправить лейаут под крупные кнопки и нижний отступ (G8, D3)
  - app/src/androidTest/.../dashboard/DashboardContentUiTest.kt + DashboardDrawerOverlayUiTest + LeftDrawerPeriodSelectorUiTest + DashboardDrawerBackPressUiTest — обновить под новую структуру (нет панели баланса/доната; есть кольцо + плитки) (G15)
TEST_TYPES: compose-ui instrumented
CONSTRAINTS:
  - Зависит от 01–05; правит `DashboardScreen.kt` (единственное место — пересечений нет) — интеграционный SPEC, идёт последним.
  - НЕ удалять файлы `MonefyDonutChart.kt`/`DashboardBalancePanel` — просто перестать вызывать на S01; если станут полностью мёртвыми — в `archive/`, не `rm` (CLAUDE.md: archive-not-delete).
  - Топ-бар: сохранить ВСЕ события (☰ левый дровер, поиск, перевод, ⋮ правый дровер, период ‹›), только перекрас под неон (D5, G1).
  - Свечение кольца не должно обрезаться паддингами Scaffold/скроллом (D1) — проверить вертикальный запас.
  - androidTest ОБЯЗАТЕЛЬНО обновить в этом же проходе — реворк структуры ломает существующие (рабочий контракт) (G15, G16); Canvas-кольцо не семантизируется → ассертить контейнеры по testTag + captureToImage/`--fit` (G15).
  - Runner пропускает `:feature:*`/инстру-тесты по модулю → прогон на устройстве `connectedDebugAndroidTest` (G16); ktlintFormat перед коммитом (G16).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Сборка неон-дашборда

  Scenario: Кольцо, центр и плитки на месте
    Given открыт дашборд за период с операциями
    Then показано неоновое кольцо с «Остаток» в центре и список плиток категорий под ним

  Scenario: Панели баланса больше нет
    Then отдельная DashboardBalancePanel не отображается (баланс — в центре кольца)

  Scenario: Кнопки крупнее и работают
    Then кнопки −/+ имеют размер 104dp и эмитят добавление расхода/дохода как прежде

  Scenario: Тап по плитке ведёт в операции категории
    When тап по плитке категории
    Then открывается список операций этой категории за текущий период

  Scenario: Топ-бар сохранил функции
    Then ☰, поиск, перевод, ⋮ и переключение периода работают как раньше
```

## Gap / context
Текущий `DashboardContent` строит топ-бар + панель баланса + Monefy-донат + FAB. Новый макет: топ-бар (неон) +
кольцо с центром-балансом + скроллируемые плитки + крупные FAB. Этот SPEC соединяет компоненты 02–05 в экран и
обновляет сломанные UI-тесты.

## Implementation links
- commit: <hash>
- files:  <changed files>
