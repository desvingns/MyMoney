# Эпик: audit4-records — полнота записей: переводы, период, живой список
Epic: audit4-records
Order: 00 of 05 (overview)
Status: done
Completed: 2026-06-14 (all SPECs shipped to main; epic closed during backlog housekeeping)
Depends-on: —
Date: 2026-06-10

## Цель

Закрыть пользовательски-видимые дыры экрана записей из аудита (`docs/audit/2026-06-10-project-audit.md`):
(M1) переводы невидимы во всех списках (их нельзя отредактировать/удалить из UI) и валят весь
CSV-экспорт; (M2) drill-down с дашборда теряет выбранный период; (M3) список показывает устаревшие
данные после правок в деталке; (M6/M7) undo-снэкбар блокирует тапы, recomputeBalance гоняется сам
с собой; (M5-события) one-shot действия теряются при повороте.

## Заблокированные решения (из grill)

- **D4:** переводы показываются на отдельном табе «Операции | Переводы» экрана записей (S12);
  строки «Счёт A → Счёт B», тап → существующая деталка.
- **D4a:** дашборд переводы НЕ учитывает в донате/расходных агрегатах (уже так — закрепить
  контракт-тестом); баланс КОНКРЕТНОГО счёта переводы учитывает (иначе цифры счёта врут).
- **D4b:** CSV-экспорт включает переводы (kind=transfer + оба счёта/суммы, формат аддитивный),
  импорт распознаёт их round-trip; IOException-отказ удаляется.

## SPEC'и (собираются через `/mp --feature --next` в порядке)

| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `audit4-records-01-transfers-tab.md` | — | data+domain+presentation | таб «Операции|Переводы» + контракт-тест доната |
| 02 | `audit4-records-02-csv-export-transfers.md` | — | data | экспорт/импорт переводов round-trip, убрать IOException |
| 03 | `audit4-records-03-drilldown-period-pass.md` | — | presentation | from/to в navigate из периода дашборда |
| 04 | `audit4-records-04-live-records-list.md` | 01 | presentation | список наблюдает таблицу (фикс staleness) |
| 05 | `audit4-records-05-ui-event-delivery.md` | 03 | presentation | снэкбар/гонка баланса/repeatOnLifecycle |

## Почему такой порядок

01 — ядро эпика (новая видимость переводов), 04 правит тот же `TransactionsListViewModel` после.
03 и 05 делят `DashboardViewModel` — 03 первым. 02 независим, но `BackupRepositoryImpl.kt` дальше
правят `audit7-forms-hardening-01` и `audit9-sync-hardening-03` — выполнять в порядке эпиков.

## Ключевые факты (verified, из grounding)

- G1: `getCategoryGroups` фильтрует `kind IN ('expense','income')` — `core/database/.../dao/TransactionDao.kt:62-75` (:70); у переводов categoryId NULL — они не попадают ни в одну группу.
- G2: `exportTransactionsCsv` бросает IOException при ЛЮБОМ Transfer — `BackupRepositoryImpl.kt:100-102`.
- G3: navigate-вызовы дашборда НЕ передают from/to — `app/.../navigation/MyMoneyNavHost.kt:84-97`; маршрут объявляет их с default −1 — :116-123 (verified-main).
- G4: `PeriodArithmetic.toEpochMillisRange(period, zone)` — `core/domain/.../time/PeriodArithmetic.kt:10`; `resolvePeriod` уже умеет читать from/to — `TransactionsListViewModel.kt:191-195`.
- G5: одноразовый `load()` в init, таблица не наблюдается — `TransactionsListViewModel.kt:66,80-88`.
- G6: реактивный паттерн для зеркалирования — `transactionRepository.observeRecent(limit = 1).collect { recompute… }` — `DashboardViewModel.kt:237-241`.
- G7: снэкбар ожидается в collect-цикле действий (buffer 4, DROP_OLDEST) — `TransactionsListScreen.kt:97-115`.
- G8: `recomputeBalance` — независимый launch без отмены предыдущего — `DashboardViewModel.kt:245-271`.
- G9: one-shot действия собираются `LaunchedEffect(viewModel)` без repeatOnLifecycle — `DashboardScreen.kt:87-89`, `BackupRestoreScreen.kt:96-117`.
- G10: строки — `feature/transactionslist/src/main/res/values{,-ru}/strings.xml`.
- G11: донат уже expenses-only (`DashboardViewModel.snapshotToSlices` считает доли от expense) — закрепить контракт-тестом (D4a).
- G12: MyMoney-путь импорта `importTransactionsCsv` + механизм резолва счетов — `BackupRepositoryImpl.kt:138,276`.
- G13: колбэк открытия деталки `onOpenDetail(id)` уже существует — `TransactionsListScreen.kt:85,100`.

## Implementation links
- **EPIC COMPLETE 2026-06-13** — all 5 SPECs shipped to main:
  - 01 transfers-tab: 5f0b0a4d + 8eb28745 + fea12fff
  - 02 csv-export-transfers: 29ed27c8 + 992a9356
  - 03 drilldown-period-pass: 5dac6fdf + 72246319
  - 04 live-records-list: e7a49f63 + 7803e553
  - 05 ui-event-delivery: dd654997 + d8ba8a79
