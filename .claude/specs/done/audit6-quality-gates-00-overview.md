# Эпик: audit6-quality-gates — Kover, detekt/ktlint, CI, недостающие VM-тесты
Epic: audit6-quality-gates
Order: 00 of 05 (overview)
Status: done
Completed: 2026-06-14 (all SPECs shipped to main; epic closed during backlog housekeeping)
Depends-on: —
Date: 2026-06-10

## Цель

В проекте нет ни одного инструмента качества (аудит §2 P2.7, §3): ни coverage (Kover/JaCoCo),
ни статанализа (detekt/ktlint), ни lint-конфига; CI гоняет монолитный connectedDebugAndroidTest
(локально известен как зависающий на 122 тестах) и публикует неподписанный release-APK; модули
:feature:onboarding (0 тестов), Transfer/CurrencyRate VM и 4 словарных VM не покрыты. Эпик ставит
инструменты с baseline (без шторма правок), батчит CI и закрывает тестовые дыры.

## Заблокированные решения (из grill)

- **D5:** полный набор — Kover + detekt + ktlint. Побочный бонус: задача `:app:detekt` чинит
  известный false-fail скрипта mp-runner-android.sh.
- **O1 (assumption):** пороги Kover фиксируются ПОСЛЕ первого baseline-отчёта; старт — report-only
  + verify на :core:domain/:core:database/:core:datastore (рекомендация line ≥ 60%).
- detekt/ktlint входят с baseline-файлами; массовый ktlint-формат — отдельным коммитом.

## SPEC'и (собираются через `/mp --feature --next` в порядке)

| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `audit6-quality-gates-01-kover.md` | — | build | Kover: report + verify на core-модулях |
| 02 | `audit6-quality-gates-02-detekt-ktlint.md` | 01 | build | detekt+ktlint с конфигами и baseline |
| 03 | `audit6-quality-gates-03-ci-hardening.md` | 02 | infra | connected-джоб батчами; -unsigned; шаги качества |
| 04 | `audit6-quality-gates-04-vm-tests-core-flows.md` | — | test | Onboarding/Splash/Transfer VM-тесты |
| 05 | `audit6-quality-gates-05-vm-tests-dictionaries.md` | — | test | 4 словарных VM-теста |

## Почему такой порядок

01→02 делят корневой `build.gradle.kts`/toml; 03 правит ci.yml после появления задач 01–02.
04/05 независимы (чистые test-only). Клэш: 02 и 03 оба трогают ci.yml — последовательность.

## Ключевые факты (verified, из grounding)

- G1: `gradle/libs.versions.toml` [plugins] :146-158 — detekt/ktlint/kover/jacoco ОТСУТСТВУЮТ; корневой `build.gradle.kts:17-21` — subprojects-блок JvmTarget — точка подключения.
- G2: CI `.github/workflows/ci.yml` — 2 джоба: lintDebug+testDebugUnitTest+:app:assembleRelease (:44) и монолитный `connectedDebugAndroidTest` через reactivecircus/android-emulator-runner (:98); release-артефакт неподписан.
- G3: mp-runner-android.sh ожидает задачи :app:detekt/:app:jacoco — их отсутствие даёт false pass:false (project memory `mymoney-mp-runner-script-mismatch`).
- G4: локальная дисциплина девайс-прогонов: NEVER монолитный полный прогон; батчи с fresh-JVM + watchdog; `scripts/preflight_device_health.ps1` перед сюитами (memory `mymoney-device-run-discipline`).
- G5: тестовые дыры (verified): `:feature:onboarding` — 0 тест-файлов (OnboardingViewModel, SplashViewModel); нет TransferViewModelTest (есть только TransferScreenContractTest); нет AccountEditViewModelTest / AccountsListViewModelTest / CurrenciesListViewModelTest / CategoryEditViewModelTest (частично закрыт CategoryEditFromPickerTest).
- G6: конвенция тестов: fakes-only на границе репозитория, без мок-фреймворков; module-local fakes (до java-test-fixtures).
- G7: незакоммичен `core/designsystem/src/androidTest/.../appbar/MoneyHeroAppBarUiTest.kt` (13 тестов, git ??) — закоммитить при ближайшем прогоне (не отдельный SPEC).

## Implementation links
- (заполняется по мере выполнения)
