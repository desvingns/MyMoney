# Эпик: audit8-hygiene — онбординг-флаг, мёртвый код, манифест, a11y
Epic: audit8-hygiene
Order: 00 of 04 (overview)
Status: done
Completed: 2026-06-14 (all SPECs shipped to main; epic closed during backlog housekeeping)
Depends-on: —
Date: 2026-06-10

## Цель

Гигиенические находки аудита (`docs/audit/2026-06-10-project-audit.md`, P1.2/P2.5-6/P3):
SHOW_ONBOARDING=false зашит во ВСЕ сборки (онбординг не увидит ни один пользователь релиза,
app-shortcuts мертвы); мёртвые :core:network (тянет okhttp/retrofit в APK) и SYNC_DISABLED;
toml-мусор (coil RC!); необрабатываемые intent-фильтры и крашопасная Dropbox AuthActivity;
худшие a11y-пробелы.

## Заблокированные решения (из grill)

- **D6:** SHOW_ONBOARDING → buildTypes: release=true, debug=false; skip-путь проставляет
  onboardingCompletedAt (shortcuts и роутинг работают в обоих режимах).
- Правило проекта: файлы НЕ удаляются — unlink из сборки + перенос в `archive/` (git-ignored).

## SPEC'и (собираются через `/mp --feature --next` в порядке)

| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `audit8-hygiene-01-onboarding-flag-skip.md` | — | build+presentation | флаг по buildTypes + skip ставит completedAt |
| 02 | `audit8-hygiene-02-dead-code-unlink.md` | 01 | build | :core:network unlink, SYNC_DISABLED, toml, HapticPlayer |
| 03 | `audit8-hygiene-03-intent-filters-authactivity.md` | — | manifest | убрать/загейтить мёртвые фильтры и AuthActivity |
| 04 | `audit8-hygiene-04-a11y-pass.md` | audit5-donut-perf-01 | presentation | balance bar, клавиатура, секторы доната |

## Почему такой порядок

01 и 02 делят `app/build.gradle.kts` — последовательно. 04 ждёт audit5-01 (общий
`MonefyDonutChart.kt`). Попутно (НЕ SPEC): закоммитить незакоммиченный
`core/designsystem/src/androidTest/.../appbar/MoneyHeroAppBarUiTest.kt` (13 тестов, висит в git ??).

## Ключевые факты (verified, из grounding)

- G1: `SHOW_ONBOARDING=false` в defaultConfig (все билды, комментарий «Temporary») — `app/build.gradle.kts:61`; гейт — `MyMoneyNavHost.kt:39`; `onboardingCompletedAt` ставится только в `OnboardingViewModel.kt:24`; роутер — `DecisionRouterViewModel.kt:25`.
- G2: shortcuts обрабатываются только в Dashboard-ветке — `MyMoneyNavHost.kt:313-320`; `MainActivity.kt:64-70`.
- G3: мёртвый `:core:network` (потребителей 0, okhttp/retrofit в APK) — `app/build.gradle.kts:126`; мёртвый `SYNC_DISABLED` — `app/build.gradle.kts:55-59` + `core/sync/build.gradle.kts:21-25`.
- G4: toml-мусор: `coilCompose 3.0.0-rc02`, `firebase-analytics-ktx`, `sentry-android` — не referenced ни одним build-файлом.
- G5: мёртвая deprecation-ветка — `core/ui/.../haptic/HapticPlayer.kt:87-94` (minSdk 31 → SDK_INT>=S всегда true); `app/src/test/.../ExampleUnitTest.kt` — шаблонная заглушка.
- G6: intent-фильтры `monefy://` + `DRIVE_OPEN` принимаются, но не обрабатываются — `app/AndroidManifest.xml:37-47`, `MainActivity.kt:64-70`; экспортированная `com.dropbox.core.android.AuthActivity` с отсутствующим классом + схема `db-PLACEHOLDER_DROPBOX_APP_KEY` — `feature/cloudsync/src/main/AndroidManifest.xml`.
- G7: a11y: кликабельный Row с contentDescription=null — `core/designsystem/.../balancebar/MonefyBalanceBar.kt:36-42`; операторные клавиши `MonefyKeypad.kt` без явных semantics; секторы доната не фокусируемы по отдельности.
- G8: правило archive/ — файлы не удалять, переносить в `archive/` для ручного удаления.

## Implementation links
- 01 onboarding-flag-skip — SHIPPED (e1346bab prod + 3a19046f test, pushed 5a2b1a2a).
- 02 dead-code-unlink — SHIPPED (0d3133b9 + b4110baa).
- 03 intent-filters-authactivity — SHIPPED (dd434188): removed dead monefy://+DRIVE_OPEN
  filters from app manifest and the exported unbacked AuthActivity from core/sync (real location,
  not feature/cloudsync as G6 guessed). **Намеренный откат функциональности-заглушки** — вернуть
  при реализации deep-links (monefy://, DRIVE_OPEN) и при закрытии OQ-2 (настоящая Dropbox
  AuthActivity с реальным app key).
- 04 a11y-pass — backlog (depends-on audit5-donut-perf-01, which is shipped).
