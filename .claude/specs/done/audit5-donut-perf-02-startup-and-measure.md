# Холодный старт: ленивые SoundPool/SecureStorage, jobs off-main + перемер
Epic: audit5-donut-perf
Order: 02 of 02
Status: done
Depends-on: audit3-lock-security-04
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: refactor
PLATFORM: android
WHAT: Снять тяжёлую работу с горячего пути старта без изменения поведения: (1) SoundPoolImpl грузит 6 звуков лениво/асинхронно (первое воспроизведение или фоновая прогрузка), а не в конструкторе; (2) SecureStorageImpl создаёт EncryptedSharedPreferences лениво (by lazy)/на фоновом диспетчере — Keystore-работа уходит с пути инжекта MainActivity; (3) scheduleDailyJobs уходит в @ApplicationScope на IO. Затем — перемер :macrobenchmark на release и фиксация чисел против бюджетов TDD.
LAYERS: presentation
CHANGED_HINT:
  - core/ui/.../sound/SoundPlayer.kt:44-54 — загрузка из конструктора → lazy/фоновая инициализация; первый play при незагруженном пуле деградирует в тишину (G5)
  - core/datastore/.../SecureStorageImpl.kt — prefs → by lazy поверх recovery-фабрики из audit3-04 (G7)
  - app/src/main/java/com/kshavrin/mymoney/MyMoneyApp.kt:27 — scheduleDailyJobs() в applicationScope.launch(io) (G5)
  - прогон :macrobenchmark (StartupBenchmark + frame-бенчи) на release; числа — в Implementation links и PROGRESS
TEST_TYPES: unit, instrumented
CONSTRAINTS:
  - Поведение неизменно: звуки играют (после прогрузки), лок работает, jobs ставятся — меняется только МОМЕНТ инициализации.
  - `SecureStorageImpl.kt` — после audit3-04 (recovery там); `MyMoneyApp.kt` — ПОСЛЕ audit1-timezone-03 (запуск нормализатора там), затем его правит audit9-sync-hardening-04.
  - WorkManager on-demand init (manifest remove) не трогать.
  - Бенчмарки гонять по дисциплине устройства: preflight health-check, никаких монолитных полных сюит.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Лёгкий старт

  Scenario: Старт не грузит звуки и Keystore синхронно
    When приложение стартует с холодного старта
    Then создание Application/MainActivity не выполняет загрузку SoundPool и EncryptedSharedPreferences на main

  Scenario: Звук работает после старта
    Given приложение запущено
    When пользователь совершает действие со звуком
    Then звук воспроизводится (после фоновой прогрузки — без краша)

  Scenario: Ежедневные jobs поставлены
    When приложение стартовало
    Then recurring/prune jobs стоят в WorkManager как раньше
```

## Gap / context
P2.9 аудита: MyMoneyApp.onCreate + конструкторы синглтонов — видимые вкладчики в cold start ~5.5s
(G4, G5). Перемер обязателен на release — debug-эмулятор не показателен.

## Implementation links
- commit: c1c3b81d — `perf: defer SoundPool/SecureStorage init and offload daily-job scheduling` (pushed to main 2026-06-13)
- files:
  - core/ui/src/main/java/com/kshavrin/mymoney/core/ui/sound/SoundPlayer.kt — SoundPool load moved out of the constructor (lazy/background prefetch; play() before load = silence, no crash)
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/SecureStorageImpl.kt — EncryptedSharedPreferences now `by lazy` over the audit3-04 recovery factory (Keystore work off the MainActivity inject path)
  - app/src/main/java/com/kshavrin/mymoney/MyMoneyApp.kt — scheduleDailyJobs() moved into applicationScope.launch(ioDispatcher); audit1-03 normalizer launch preserved
  - tests: SoundPoolImplTest.kt (JVM, timing contract via RecordingSoundPlayer), SecureStorageTest.kt (androidTest — lazy-init-not-on-construction + by-lazy-single-init, 11/11 device), SecureStorageImplTest.kt (JVM)
- verification: :core:ui + :core:datastore + :app unit tests green; SecureStorageTest 11/11 on Pixel_5_API_34; clean :app:assembleDebug + install + launch smoke OK (no crash, RecurringWorker + PruneDeletedWorker ran SUCCESS — jobs still scheduled off-main); reviewer pass, verifier pass.
- macrobenchmark до/после: NOT RE-MEASURED in this environment — release-build numbers require (a) release signing keys (absent here, `hasReleaseSigningConfig=false`) and (b) a physical ARM device with the baseline profile applied; the SPEC itself notes x86_64/debug-emulator numbers are non-representative. **Deferred to a physical-device signed-release run** (DevOps prerequisite, OQ-class). The code-level hot-path reductions (1)(2)(3) are shipped; the quantified before/after against the TDD budgets is the pending measurement.
