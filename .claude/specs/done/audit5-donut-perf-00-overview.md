# Эпик: audit5-donut-perf — производительность доната и холодного старта
Epic: audit5-donut-perf
Order: 00 of 02 (overview)
Status: done
Completed: 2026-06-14 (all SPECs shipped to main; epic closed during backlog housekeeping)
Depends-on: —
Date: 2026-06-10

## Цель

Закрыть перф-находки аудита (`docs/audit/2026-06-10-project-audit.md`, H6 + P2.9) и сдвинуть
проваленные бюджеты PHASE_15 (dashboard frame CPU p50 ~399 ms, cold start ~5.5 s на эмуляторе):
донат аллоцирует Paint+BlurMaskFilter и рисует до ~200 арок на КАЖДЫЙ кадр анимации, а анимация
рестартует при любой записи в БД; старт грузит SoundPool/SecureStorage/WorkManager на горячем пути.

## Заблокированные решения (из grill)

- Чисто оптимизационный эпик: видимый рендер доната не меняется (29/29 MonefyDonutChartUiTest —
  контракт), поведение звука/локов не меняется — только момент инициализации.
- Перемер строго через :macrobenchmark на release-сборке (5.5s намерено на debug/эмуляторе —
  baseline-профиль там не действует).

## SPEC'и (собираются через `/mp --feature --next` в порядке)

| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `audit5-donut-perf-01-draw-allocations.md` | — | presentation | кеш draw-объектов, одно-проходная стенка, стабильный animationKey |
| 02 | `audit5-donut-perf-02-startup-and-measure.md` | audit3-lock-security-04 | presentation | lazy SoundPool/SecureStorage, jobs off-main; перемер macrobenchmark |

## Почему такой порядок

01 независим. 02 ждёт audit3-04 (общий файл `SecureStorageImpl.kt` — recovery там, lazy здесь);
`MyMoneyApp.kt` также правится в `audit9-sync-hardening-04` — выполнять в порядке эпиков.
`MonefyDonutChart.kt` затем трогает `audit8-hygiene-04` (a11y) — после 01.

## Ключевые факты (verified, из grounding)

- G1: `drawExtrudedRing` строит `Paint` + `BlurMaskFilter(7dp)` внутри drawIntoCanvas на каждый кадр — `core/designsystem/.../donut/MonefyDonutChart.kt:791-800`; remember{} вокруг draw-объектов нет.
- G2: depth-цикл `for (k in depth downTo 1)`, depth = (th*0.62f).coerceIn(7,22) — :802-808 — до 22 арок × N слайсов за кадр.
- G3: `LaunchedEffect(animationKey) { snapTo(0f); animateTo(1f) }` — :107,114-116 — анимация рестартует при каждом пересчёте баланса (любая запись в таблицу).
- G4: измеренные провалы бюджета (PHASE_15, 2026-06-01): cold-start median ~5.5 s, dashboard frame CPU p50 ~399 ms, list-scroll p50 ~141 ms.
- G5: startup-работа на main: `workScheduler.scheduleDailyJobs()` — `app/.../MyMoneyApp.kt:27`; SoundPool + 6×getIdentifier в конструкторе — `core/ui/.../sound/SoundPlayer.kt:44-54`.
- G6: контракт: `MonefyDonutChartUiTest` 29/29 на Pixel_5_API_34 — не ломать.
- G7: `SecureStorageImpl` создаёт EncryptedSharedPreferences в конструкторе — `core/datastore/.../SecureStorageImpl.kt:17-28` (recovery-фабрика приходит из audit3-lock-security-04).

## Implementation links
- 01 draw-allocations — DONE 2026-06-13, commit b6bb1c4d, pushed to main (Paint/BlurMaskFilter hoisted into remembered ExtrudedRingPaints; value-equal DonutAnimationKey from (categoryId, fraction); DonutAnimationKeyTest JVM + MonefyDonutChartUiTest 36/36 device green — visual parity held)
- 02 startup-and-measure — DONE 2026-06-13, commit c1c3b81d, pushed to main (SoundPool load + EncryptedSharedPreferences `by lazy` deferred off the cold-start hot path; scheduleDailyJobs → applicationScope.launch(io); unit green + SecureStorageTest 11/11 device + clean-assemble smoke OK, jobs still scheduled). ⚠ macrobenchmark re-measure DEFERRED — needs signed release on a physical ARM device (no release keys + x86_64 emulator here; emulator numbers non-representative per overview).

**EPIC COMPLETE 2026-06-13 — both SPECs shipped to main.** Remaining open item: the quantified macrobenchmark before/after vs TDD budgets (G4) is a DevOps-prerequisite measurement deferred to a physical-device signed-release run.
