# CI: батчи connected-тестов, честный артефакт, шаги качества
Epic: audit6-quality-gates
Order: 03 of 05
Status: done
Depends-on: audit6-quality-gates-02
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: refactor
PLATFORM: android
WHAT: CI перестаёт повторять известный локальный фейл-паттерн: connected-джоб разбивается на батчи по модулям (:app / :core:designsystem / :core:database / :core:datastore — отдельные gradle-вызовы с таймаутами вместо одного монолитного connectedDebugAndroidTest); release-артефакт переименовывается с явным «-unsigned»; в быстрый джоб добавляются шаги detekt/ktlintCheck/koverVerify из SPEC 01-02.
LAYERS: infra
CHANGED_HINT:
  - .github/workflows/ci.yml:44 — добавить шаги detekt, ktlintCheck, koverVerify после testDebugUnitTest (G2)
  - .github/workflows/ci.yml:98 — `./gradlew connectedDebugAndroidTest` → последовательность `:app:connectedDebugAndroidTest`, `:core:designsystem:connectedDebugAndroidTest`, `:core:database:connectedDebugAndroidTest`, `:core:datastore:connectedDebugAndroidTest`, каждый шаг с timeout-minutes (G2, дисциплина G4)
  - .github/workflows/ci.yml — артефакт `app-release-unsigned.apk` + комментарий о причине (keystore — внешний гейт OQ) (G2)
TEST_TYPES: unit
CONSTRAINTS:
  - Матрицу эмуляторов не раздувать: тот же API 34, один AVD; батчи — последовательные шаги одного джоба (дешевле, чем 4 джоба).
  - Падение одного батча не должно скрывать результаты остальных (continue-on-error: false, но отчёты загружать always).
  - `ci.yml` — после SPEC 02 (шаги ссылаются на его задачи).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: CI воспроизводим и честен

  Scenario: Connected-тесты идут батчами
    When запускается connected-джоб
    Then каждый модуль гоняется отдельным шагом со своим таймаутом
    And зависание одного модуля не маскирует остальные

  Scenario: Качество в быстрый джоб
    When пуш в main
    Then detekt, ktlintCheck и koverVerify выполняются и блокируют красную сборку

  Scenario: Артефакт не врёт
    Then выложенный APK называется app-release-unsigned.apk
```

## Gap / context
Аудит P2.8: монолитный прогон 122 тестов локально вешал AVD на 64 мин (G4) — CI повторяет тот же
паттерн (G2); неподписанный артефакт назван как релизный.

## Implementation links
- commit: 4bc55b63 (pushed to main 2026-06-13)
- files: `.github/workflows/ci.yml`
- notes: fast job gains detekt/ktlintCheck/koverVerify steps (all confirmed green); connected job splits monolithic connectedDebugAndroidTest into 4 per-module gradle calls (:app 30m, :core:designsystem/:core:database/:core:datastore 20m each) under one emulator-runner script hook with shell `timeout` + status accumulation + `exit $status` (no masking), reports uploaded `if: always()`; release artifact renamed `app-release-unsigned.apk` with OQ-keystore comment. Runner script false-failed (probes non-existent `:app:jacoco`; project uses Kover) → verified-manual pass.
