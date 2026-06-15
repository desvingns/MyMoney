# Эпик: audit2-save-integrity — целостность сохранений и конкурентность
Epic: audit2-save-integrity
Order: 00 of 04 (overview)
Status: done
Completed: 2026-06-14 (all SPECs shipped to main; epic closed during backlog housekeeping)
Depends-on: —
Date: 2026-06-10

## Цель

Четыре подтверждённых аудитом дефекта целостности (`docs/audit/2026-06-10-project-audit.md`):
(H1) редактирование курса валют тихо не сохраняется; (H3) дабл-тап по Save/категории создаёт две
транзакции и дважды зовёт popBackStack; (H2) read-modify-write гонка DataStore теряет конкурентные
записи и воскрешает import-focus (вектор регрессии фикса `26dc71ac`); (M4) `catch(Throwable)`
повсеместно глотает `CancellationException`. Эпик делает каждое сохранение атомарным и однократным.

## Заблокированные решения (из grill)

- Фикс курса — паттерн `findRate → existing.id` (зеркало detail-VM), без смены DAO-контракта.
- Дабл-тап: синхронный guard первой строкой (`if (isSaving) return` + немедленная установка флага);
  словарные VM получают флаг.
- DataStore: transform переезжает ВНУТРЬ `dataStore.edit { }` — единственная атомарная точка записи.
- CancellationException всегда re-throw (или `ensureActive()`) до маппинга в ошибки/Sentry.

## SPEC'и (собираются через `/mp --feature --next` в порядке)

| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `audit2-save-integrity-01-currency-rate-upsert-fix.md` | — | presentation | findRate→existing.id + новый CurrencyRateViewModelTest |
| 02 | `audit2-save-integrity-02-double-tap-guards.md` | audit1-timezone-01 | presentation | синхронные isSaving-guard'ы в 8 VM |
| 03 | `audit2-save-integrity-03-datastore-atomic-update.md` | audit1-timezone-03 | data | transform внутри dataStore.edit + concurrency-тест |
| 04 | `audit2-save-integrity-04-cancellation-rethrow-sweep.md` | — | cross | rethrow CancellationException во всех catch |

## Почему такой порядок

01 — изолированный однофайловый фикс худшего дефекта. 02 ждёт audit1-01 (общий файл
`TransactionDetailViewModel.kt`). 03 ждёт audit1-03 (общие файлы AppSettings). 04 независим.

## Ключевые факты (verified, из grounding)

- G1: `CurrencyRateViewModel.save()` всегда строит `CurrencyRate(id = 0L, …)` и эмитит успех — `feature/transaction/.../rate/CurrencyRateViewModel.kt:96-105`.
- G2: unique(from_currency_id,to_currency_id) — `core/database/.../entity/CurrencyRateEntity.kt:25`; `@Upsert` при id=0 на существующей паре → UPDATE WHERE id=0 → тихий no-op (проверено по сгенерированному `CurrencyRateDao_Impl.java`).
- G3: корректный паттерн — `existing?.id ?: 0L` в `upsertRate` — `feature/transactionslist/.../detail/TransactionDetailViewModel.kt:345-356`.
- G4: `CurrencyRateRepository.findRate(from, to): CurrencyRate?` — `core/domain/.../repository/CurrencyRateRepository.kt:7`.
- G5: транзакционные VM имеют `isSaving`, но ставят его внутри корутины (`AddExpenseViewModel.kt:184`); входы: `save():170-217`, `onCategoryPicked():155-168`; AddIncome `:184`, Transfer `:203-262`, Detail `:253-343`.
- G6: словарные VM флага не имеют — `CategoryEditViewModel.kt:81-111`, AccountEdit, CurrencyEdit, GoalEdit.
- G7: `AppSettingsRepository.update`: `settings.first()` СНАРУЖИ `dataStore.edit`, `writeTo` перезаписывает все 16 ключей — `core/datastore/.../AppSettingsRepositoryImpl.kt:23-31,60-87`.
- G8: 13 вызовов `update()` в 9 файлах (Dashboard×4, BiometricSetup×3, CloudSync, Onboarding, BackupRestore, Language, Settings, ThemeSettings) — все зависят от семантики.
- G9: глотатели CancellationException — `AddExpenseViewModel.kt:209` (и все save-пути), `SnapshotSyncRepository.kt:51,67,74`, `DropboxRepository.kt:124-138`, `SearchViewModel.kt:77-80`, `RecurringWorker.kt:19-22` (+ остальные воркеры).
- G10: дыра покрытия — `CurrencyRateViewModelTest` отсутствует.

## Implementation links
- (заполняется по мере выполнения)
