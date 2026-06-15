# Авто-sync планируется на старте, а не только из тумблера
Epic: audit9-sync-hardening
Order: 04 of 04
Status: done
Depends-on: —
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: Периодический фоновый sync существует не только после ручного передёргивания тумблера: при старте приложения, если autoSyncEnabled=true И есть подключённый облачный таргет, периодическая работа enqueue-ится (ExistingPeriodicWorkPolicy.KEEP — без дублей); при выключенном autoSync или отсутствии таргета — снимается.
LAYERS: data
CHANGED_HINT:
  - core/sync/.../WorkSchedulerImpl.kt:22-44 — scheduleDailyJobs дополняется условным enqueue периодического sync (KEEP) по autoSyncEnabled + connected-таргету из настроек/SecureStorage (G5)
  - feature/cloudsync/.../CloudSyncViewModel.kt:129 — тумблер продолжает управлять работой (выключение → cancel) — поведение не дублируется, источник правды один (G5)
  - тест: fake-настройки autoSync=true+таргет → планирование вызвано; false → нет; KEEP-политика (повторный старт не создаёт дубль)
TEST_TYPES: unit
CONSTRAINTS:
  - Сейчас «подключённый таргет» недостижим в проде (Connect — заглушка до OQ-2) — фикс готовит корректное поведение к моменту открытия гейта; live-проверка отложена (Verifier manual-чеклист).
  - `MyMoneyApp.kt`/`WorkSchedulerImpl` — после audit5-donut-perf-02 (jobs off-main).
  - 6h-интервал и constraints существующего SyncScheduler не менять.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Фоновый sync живёт без ритуалов

  Scenario: Планирование на старте
    Given autoSync включён и Dropbox подключён
    When приложение стартует
    Then периодическая sync-работа стоит в WorkManager

  Scenario: Без дублей
    Given sync-работа уже запланирована
    When приложение стартует повторно
    Then работа остаётся в единственном экземпляре (KEEP)

  Scenario: Выключенный autoSync
    Given autoSync выключен
    When приложение стартует
    Then периодическая sync-работа не планируется
```

## Gap / context
Баг M8 аудита (G5): default autoSyncEnabled=true, но scheduleDailyJobs ставит только
recurring+prune — подключивший облако пользователь не получает фоновый sync вовсе.

## Implementation links
- commit: c8e3d81d (prod) + 9524f70e (test)
- files:
  - core/sync/src/main/java/com/kshavrin/mymoney/core/sync/WorkScheduler.kt
  - core/sync/src/main/java/com/kshavrin/mymoney/core/sync/WorkSchedulerImpl.kt
  - core/sync/src/test/java/com/kshavrin/mymoney/core/sync/WorkSchedulerImplTest.kt
