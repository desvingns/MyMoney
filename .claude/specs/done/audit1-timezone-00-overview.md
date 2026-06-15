# Эпик: audit1-timezone — единая таймзонная конвенция записи/чтения дат
Epic: audit1-timezone
Order: 00 of 04 (overview)
Status: done
Completed: 2026-06-14 (all SPECs shipped to main; epic closed during backlog housekeeping)
Depends-on: —
Date: 2026-06-10

## Цель

Баг C1 аудита (`docs/audit/2026-06-10-project-audit.md`): транзакции пишутся UTC-полночью, а все
границы периодов и отображение считаются в локальной зоне. В любой UTC−зоне запись «10 июня»
агрегируется под 9 июня; «открыл-сохранил» Monefy-импортированную запись тихо сдвигает дату на день.
Решение (grill D2): единая конвенция — **локальная зона на записи**; Material3-пикеры конвертируются
через `ZoneOffset.UTC` строго на границе пикера (их контракт — UTC-millis). Существующие строки
выравнивает одноразовый нормализатор (D2b). Эпик закрывается регрессионной TZ-сюитой.

## Заблокированные решения (из grill)

- **D2:** запись `occurredAt = date.atStartOfDay(ZoneId.systemDefault()).toInstant()` во всех 4 VM;
  detail-чтение тоже локальное. Monefy-импорт (уже локальный) не трогаем.
- **D2b:** одноразовый нормализатор: строки с occurredAt ровно на UTC-полночь → локальная полночь
  того же UTC-календарного дня; guard-флаг в AppSettings; идемпотентно.
- Контракт пикеров: UTC-millis ↔ LocalDate только через `ZoneOffset.UTC` (эталон G6).

## SPEC'и (собираются через `/mp --feature --next` в порядке)

| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `audit1-timezone-01-write-sites-local.md` | — | presentation | 4 VM пишут локальную полночь; detail-чтение локально |
| 02 | `audit1-timezone-02-dashboard-picker-utc-boundary.md` | — | presentation | PeriodStrip: UTC-millis пикера → ZoneOffset.UTC |
| 03 | `audit1-timezone-03-normalizer-migration.md` | 01 | domain+data | одноразовый нормализатор UTC-полночных строк + guard-флаг |
| 04 | `audit1-timezone-04-tz-regression-suite.md` | 01,02,03 | test | регрессионная сюита с TimeZone=America/New_York |

## Почему такой порядок

01 — фундамент (новые записи корректны), 03 чинит старые данные поверх, 04 пиннит всё. Клэши:
`TransactionDetailViewModel.kt` правится здесь (01) и в `audit2-save-integrity-02` — этот эпик первый;
файлы AppSettings (03) правятся также в `audit2-save-integrity-03` — секвенировать (03 первым).

## Ключевые факты (verified, из grounding)

- G1: запись UTC-полночью — `feature/transaction/.../expense/AddExpenseViewModel.kt:195`, `.../income/AddIncomeViewModel.kt:195`, `.../transfer/TransferViewModel.kt:223`, `feature/transactionslist/.../detail/TransactionDetailViewModel.kt:403-404`.
- G2: detail-чтение через UTC — `TransactionDetailViewModel.kt:108`.
- G3: `PeriodArithmetic.toEpochMillisRange(period, zone = ZoneId.systemDefault())` — `core/domain/.../time/PeriodArithmetic.kt:10,22-23`.
- G4: Monefy-импорт пишет локальную полночь — `core/database/.../repository/BackupRepositoryImpl.kt:322`.
- G5: PeriodStrip конвертирует UTC-millis пикера через systemDefault — `feature/dashboard/.../components/PeriodStrip.kt:82-89`.
- G6: эталон UTC-границы пикера — `feature/transaction/.../TransactionDateRangePickerDialog.kt:22,33`.
- G7: import-focus месяц через systemDefault — `feature/dashboard/.../DashboardViewModel.kt:176-181` (после унификации корректно — не трогать).
- G8: AppSettings update/writeTo (для guard-флага) — `core/datastore/.../AppSettingsRepositoryImpl.kt:23-31,60-87`.
- G9: отображение даты строки списка — `feature/transactionslist/.../list/TransactionsListScreen.kt:504` (systemDefault — остаётся).

## Implementation links
- (заполняется по мере выполнения)
