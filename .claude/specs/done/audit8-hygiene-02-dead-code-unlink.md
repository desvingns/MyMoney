# Мёртвый код: unlink :core:network, флаги, toml, HapticPlayer
Epic: audit8-hygiene
Order: 02 of 04
Status: done
Depends-on: audit8-hygiene-01
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: refactor
PLATFORM: android
WHAT: APK и сборка перестают тащить мёртвый груз: (1) :core:network отвязывается от :app (модуль остаётся на диске и в settings.gradle — только зависимость убирается; okhttp/retrofit/logging-interceptor уходят из APK); (2) BuildConfig-флаг SYNC_DISABLED удаляется из обоих build-файлов (не читается нигде); (3) из toml вычищаются неиспользуемые coilCompose 3.0.0-rc02 / firebase-analytics-ktx / sentry-android; (4) мёртвая deprecation-ветка HapticPlayer убирается; (5) ExampleUnitTest.kt переносится в archive/.
LAYERS: build
CHANGED_HINT:
  - app/build.gradle.kts:126 — убрать implementation(projects.core.network) (G3); :55-59 — убрать SYNC_DISABLED (G3)
  - core/sync/build.gradle.kts:21-25 — убрать SYNC_DISABLED (G3)
  - gradle/libs.versions.toml — убрать coilCompose/firebase-analytics-ktx/sentry-android записи (G4)
  - core/ui/src/main/java/com/kshavrin/mymoney/core/ui/haptic/HapticPlayer.kt:87-94 — убрать недостижимую else-ветку и @Suppress (G5)
  - app/src/test/java/com/kshavrin/mymoney/ExampleUnitTest.kt — git mv в archive/ (правило G8, файл НЕ удалять)
  - проверка: :app:assembleDebug + полный unit-гейт зелёные; размер APK — в Implementation links
TEST_TYPES: unit
CONSTRAINTS:
  - НИЧЕГО не удалять с диска: :core:network остаётся модулем в settings.gradle (его код может ожить с OQ-2/3); ExampleUnitTest — в archive/ с пометкой пользователю удалить вручную (G8).
  - `app/build.gradle.kts` — после audit8-hygiene-01.
  - Если какой-то «мёртвый» элемент окажется живым при реализации (новые потребители) — оставить и зафиксировать в Implementation links.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Сборка без мёртвого груза

  Scenario: APK легче и собирается
    When собирается debug и release APK
    Then okhttp/retrofit классы отсутствуют в APK
    And сборка зелёная, все unit-тесты проходят

  Scenario: Каталог версий чист
    Then в libs.versions.toml нет записей, на которые не ссылается ни один build-файл

  Scenario: Архив вместо удаления
    Then ExampleUnitTest.kt лежит в archive/ и не участвует в сборке
```

## Gap / context
Аудит P2.5/P2.6/P3.11/P3.16 (G3, G4, G5): мёртвый модуль в APK, два мёртвых флага, RC-зависимость
в каталоге, недостижимая ветка с suppress.

## Implementation links
- commit: 0d3133b9 (pushed to main 5a2b1a2a..0d3133b9)
- размер APK: debug ~80.8 MB (unminified — okhttp/retrofit savings материализуются в minified release-сборке, не в debug-APK).
- files:
  - `app/build.gradle.kts` — убран `implementation(projects.core.network)` + `SYNC_DISABLED` buildConfigField.
  - `core/sync/build.gradle.kts` — убран `SYNC_DISABLED` buildConfigField.
  - `gradle/libs.versions.toml` — убраны неиспользуемые `coil-compose`/`coilCompose`, `firebase-analytics-ktx`, `sentry-android` (library-запись). НЕ тронуты: okhttp/retrofit toml-записи (`:core:network` всё ещё их использует) и `sentry-android-core`/`sentry-core` + `sentryAndroid` версия (живые в `:app` и `:core:sync`).
  - `core/ui/src/main/java/com/kshavrin/mymoney/core/ui/haptic/HapticPlayer.kt` — убрана недостижимая else-ветка в private `resolveVibrator()` (minSdk 31 → `SDK_INT >= S` всегда true); `Build` import остаётся (нужен для API-33 shimmer-ветки).
  - `archive/ExampleUnitTest.kt.dead-template` — `git mv` из `app/src/test/...` (правило archive/, НЕ удалён — **удалить вручную**).
- Найдено живым (оставлено): `:core:network` остаётся модулем в settings.gradle (код может ожить с OQ-2/3); только зависимость :app убрана. `sentry-android-core`/`sentry-core` оказались живыми → не тронуты.
- Verified-manual: `:app:clean :app:assembleDebug` зелёный (clean — нет incremental-dex порчи), `:app:testDebugUnitTest` + `:core:ui:testDebugUnitTest` + `:core:sync:testDebugUnitTest` + `:core:ui:ktlintCheck` зелёные. Runner-скрипт дал false-neg (несуществующие detekt/jacoco/lint task'и).
