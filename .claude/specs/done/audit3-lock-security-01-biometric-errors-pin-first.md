# Обработка всех ошибок биометрии + PIN обязателен до включения лока
Epic: audit3-lock-security
Order: 01 of 04
Status: done
Depends-on: —
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: Из экрана блокировки всегда есть выход. (1) LockOverlay обрабатывает ВСЕ коды ошибок BiometricPrompt: NEGATIVE_BUTTON/USER_CANCELED → переход в PIN-режим (или кнопка «Повторить», если PIN отсутствует у легаси-установки); NO_BIOMETRICS/HW_UNAVAILABLE/HW_NOT_PRESENT → сразу PIN-режим; LOCKOUT-ветки — как сейчас. (2) Лок невозможно включить без сохранённого PIN: BiometricSetupViewModel включает biometricLockEnabled только ПОСЛЕ записи pinHash; отклонение PIN-диалога отменяет включение. (3) pinFallback переживает поворот (rememberSaveable).
LAYERS: presentation
CHANGED_HINT:
  - feature/lockscreen/.../overlay/LockOverlay.kt:171-172 — onAuthenticationError: полный when по кодам (G1); :67 — remember → rememberSaveable (G1); :145-149 — PIN-ветка с pinHash=null → показать «Повторить биометрию» вместо мёртвого ввода (G3)
  - feature/lockscreen/.../setup/BiometricSetupViewModel.kt:76 — перенести `update { biometricLockEnabled = true }` в обработчик успешного сохранения PIN; :58-59 — PinSetupDismissed отменяет включение лока (G2)
  - feature/lockscreen/src/main/res/values{,-ru}/strings.xml — новые строки: «Ввести PIN», «Повторить», пояснение (G9)
  - тесты: BiometricSetupViewModelTest — лок не включается без PIN; LockOverlay compose-тест PIN-fallback по Cancel
TEST_TYPES: unit, compose-ui
CONSTRAINTS:
  - `LockOverlay.kt` правится также в SPEC 02 (троттлинг) — этот SPEC первый.
  - BackHandler {} на локе сохраняется (лок не обходится back-ом) — выходы только PIN/биометрия.
  - Легаси-кейс «лок включён, pinHash=null»: при старте лока предлагать создать PIN после успешной биометрии (assumption).
  - AS-5: оверлей не является nav-destination — структуру не менять.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Из экрана блокировки всегда есть выход

  Scenario: Отмена биометрии ведёт в PIN
    Given лок включён и PIN сохранён
    When пользователь нажимает «Отмена» в системном промпте биометрии
    Then показывается клавиатура ввода PIN
    And верный PIN разблокирует приложение

  Scenario: Биометрия удалена в системе
    Given лок включён и PIN сохранён
    And в системе не осталось зарегистрированных отпечатков
    When открывается экран блокировки
    Then пользователю сразу доступен ввод PIN

  Scenario: Лок не включается без PIN
    Given пользователь включает блокировку в настройках
    When он закрывает диалог создания PIN, не сохранив его
    Then блокировка остаётся выключенной
```

## Gap / context
Баг C2 аудита: тап «Отмена», удаление отпечатков или недоступность сенсора оставляют статичный
«Locked» без выхода (G1), а лок можно включить с pinHash=null (G2) — данные недоступны навсегда.

## Implementation links
- commit: 1e375fc0, b569850b, cc1f9b3d, a9fa41a5, 04842e7e
- tests: 5469cb23, 12637049, 45989cea, 4a420789, f794a315, c4cbf703, 368ef047, 75b5ac3f
- files: feature/lockscreen/src/main/java/com/kshavrin/mymoney/feature/lockscreen/overlay/LockOverlay.kt; feature/lockscreen/src/main/java/com/kshavrin/mymoney/feature/lockscreen/setup/BiometricSetupViewModel.kt; feature/lockscreen/src/main/res/values/strings.xml; feature/lockscreen/src/main/res/values-ru/strings.xml; feature/lockscreen/build.gradle.kts; feature/lockscreen/src/test/java/com/kshavrin/mymoney/feature/lockscreen/fake/FakeSecureStorage.kt; feature/lockscreen/src/test/java/com/kshavrin/mymoney/feature/lockscreen/setup/BiometricSetupViewModelTest.kt; feature/lockscreen/src/androidTest/AndroidManifest.xml; feature/lockscreen/src/androidTest/java/com/kshavrin/mymoney/feature/lockscreen/HiltTestRunner.kt; feature/lockscreen/src/androidTest/java/com/kshavrin/mymoney/feature/lockscreen/overlay/LockOverlayUiTest.kt
