# detekt + ktlint с baseline (без шторма правок)
Epic: audit6-quality-gates
Order: 02 of 05
Status: done
Depends-on: audit6-quality-gates-01
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Статанализ входит в проект безопасно: detekt с дефолтным конфигом + detekt-baseline.xml (существующие находки заморожены, новые — ошибка сборки); ktlint через ktlint-gradle с .editorconfig; первый массовый формат — ОТДЕЛЬНЫМ коммитом (история правок не смешивается с конфигами). Появление задачи :app:detekt попутно чинит false-fail mp-runner-android.sh.
LAYERS: build
CHANGED_HINT:
  - gradle/libs.versions.toml — [plugins]: detekt, ktlint-gradle (G1)
  - build.gradle.kts (root) — применение на subprojects; config/detekt/detekt.yml (дефолт + правки под zero-comments стиль проекта); baseline на модуль (G1)
  - .editorconfig — ktlint-правила (официальный kotlin style)
  - один отдельный коммит `style: ktlint format` по всему репо
TEST_TYPES: unit
CONSTRAINTS:
  - Baseline-подход обязателен: сборка зелёная сразу после внедрения, существующий код не «чинится» под правила в этом SPEC (кроме автоформата ktlint отдельным коммитом).
  - Конвенция комментариев проекта (zero-comments) — отключить detekt-правила, требующие KDoc.
  - `build.gradle.kts` (root) — после SPEC 01; `.github/workflows/ci.yml` НЕ трогать (это SPEC 03).
  - Массовый формат-коммит прогнать через полный unit-гейт (:testDebugUnitTest все модули) до пуша (G4-дисциплина).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Статанализ на страже новых правок

  Scenario: Существующий код не блокирует сборку
    When detekt запускается сразу после внедрения
    Then сборка зелёная (находки в baseline)

  Scenario: Новое нарушение ловится
    Given разработчик добавляет функцию с явным запахом (пустой catch)
    When запускается detekt
    Then сборка падает с указанием файла и правила

  Scenario: Формат стабилен
    When ktlintCheck запускается после формат-коммита
    Then нарушений нет
```

## Gap / context
Аудит P2.7: ни detekt, ни ktlint, ни lint-блока (G1). Бонус — закрытие давнего несоответствия
mp-runner (G3): задача :app:detekt начинает существовать.

## Implementation links
- commit: 3e63c687 (config+baselines) + fb4a6f22 (style: ktlint format)
- files: gradle/libs.versions.toml, build.gradle.kts (root), config/detekt/detekt.yml, .editorconfig, */detekt-baseline.xml (per module), app/build.gradle.kts, core/sync/build.gradle.kts, feature/cloudsync FakeSnapshotSync.kt + 428 ktlint-formatted files
