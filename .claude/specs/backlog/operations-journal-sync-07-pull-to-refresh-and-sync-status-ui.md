# Pull-to-refresh + статус синка (UI)
Epic: operations-journal-sync
Order: 07 of 07
Status: backlog
Depends-on: 06
Date: 2026-06-25

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: UI-триггеры и видимость near-real-time синка. Pull-to-refresh на dashboard запускает `JournalSync.syncNow` через ViewModel. На экране `:feature:cloudsync` — статус: время последнего синка, состояние по соседям, баннеры ошибок, и поле конфигурации `folderId` общей папки (ручной ввод — временно, до picker'а из отложенного OQ). Устаревший диалог разрешения конфликта убирается (журнал авторазрешает по LWW).
LAYERS: presentation
CHANGED_HINT:
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardScreen.kt — обернуть контент в Material3 `PullToRefreshBox`; `onRefresh` → `DashboardEvent.RefreshRequested`. (assumption — dashboard wiring)
  - feature/dashboard/.../DashboardViewModel.kt — `RefreshRequested` → `JournalSync.syncNow`; поле состояния `isRefreshing`. (assumption)
  - feature/cloudsync/.../CloudSyncViewModel.kt:26-219 — состояние `lastSyncAt`, статус по соседям, поле `folderId` + сеттер (сохранение в DataStore-конфиг, D10); маппинг ошибок `JournalSync` → string-res (G11); убрать устаревший conflict-prompt (`resolveConflict`, D5). КЛЭШ с 06: 06 раньше. (G10, G11)
  - feature/cloudsync/.../CloudSyncScreen.kt — UI: время последнего синка, список соседей/статус, ввод/показ `folderId`, кнопка «синхронизировать сейчас»; убрать диалог конфликта. (G10)
  - feature/cloudsync/src/main/res/values/strings.xml + values-ru/strings.xml — новые строки статуса синка и общей папки; без хардкода (G11). (G11)
TEST_TYPES: compose-ui
CONSTRAINTS:
  - Compose UI-тесты — `:app` androidTest, НЕ Robolectric (G20/G29); один `setContent` на `@Test`; пустые/нулевого размера узлы → `.assertExists()`; off-screen → `.performScrollTo()` перед кликом (G20).
  - UI БЕЗ логики синка: pull-to-refresh и «синхронизировать сейчас» лишь дёргают `JournalSync.syncNow` через VM.
  - Все подписи — через `stringResource` (values + values-ru), без хардкода (G11).
  - `folderId` — РУЧНОЙ ввод временно (picker/создание/шаринг папки = отложенный OQ, D2/D10); при пустом — статус «общая папка не настроена».
  - Убрать conflict-prompt UI (D5) — у журнала нет конфликтов в UI (LWW авто); файлы АРХИВИРОВАТЬ, не удалять (правило проекта).
  - `CloudSyncViewModel` — общий файл с 06: строго последовательно (06 раньше).
  - ВНИМАНИЕ (cross-epic): `DashboardScreen.kt`/`DashboardViewModel.kt` параллельно эволюционируют в эпике `dashboard-operations-summary-drawer` — при реализации свериться/ребейзнуться (это не клэш внутри этого эпика, но общий файл между эпиками).
  - near-real-time свежесть = этот pull-to-refresh + open-app триггер из 06 (D7).
=== END SPEC ===

## Acceptance
```gherkin
Feature: UI-триггеры и статус журнального синка
  Покрывает pull-to-refresh на dashboard и статус на экране облачного синка.

  Scenario: Pull-to-refresh запускает синк
    Given пользователь на dashboard, autosync настроен
    When пользователь тянет список вниз для обновления
    Then запускается JournalSync.syncNow
    And во время синка показывается индикатор обновления

  Scenario: Показ времени последнего синка
    Given синхронизация недавно выполнялась
    When пользователь открывает экран облачного синка
    Then отображается время последнего успешного синка

  Scenario: Общая папка не настроена
    Given folderId не задан
    When пользователь открывает экран облачного синка
    Then показан статус «общая папка не настроена»
    And доступно поле ввода идентификатора папки

  Scenario: Ошибка синка показывается баннером
    Given синк завершился ошибкой
    When пользователь на экране облачного синка
    Then показан локализованный баннер ошибки (string-res, не хардкод)
```

## Gap / context
После 06 синк работает по периодике и при открытии, но у пользователя нет ни ручного «потянуть-обновить», ни видимости состояния, ни способа задать общую папку. Этот SPEC добавляет UI-триггер и статус, завершая near-real-time контур (в рамках отложенного live-OQ).

## Implementation links
- commit: (pending)
- files:  (pending)
