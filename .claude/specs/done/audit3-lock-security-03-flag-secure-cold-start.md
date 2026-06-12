# FLAG_SECURE и закрытие окна флеша контента до лока
Epic: audit3-lock-security
Order: 03 of 04
Status: done
Depends-on: —
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: (1) На холодном старте с включённым локом реальные балансы не должны мелькать до появления оверлея: splash удерживается `setKeepOnScreenCondition`, пока состояние лока неизвестно. (2) При включённом локе окно получает FLAG_SECURE — превью в task switcher и скриншоты не показывают финансовые данные; при выключении лока флаг снимается.
LAYERS: presentation
CHANGED_HINT:
  - feature/lockscreen/.../overlay/LockController.kt:33-34,42 — добавить `isResolved: StateFlow<Boolean>` (false до первой эмиссии настроек) (G7)
  - app/src/main/java/com/kshavrin/mymoney/MainActivity.kt:45-61 — `installSplashScreen().setKeepOnScreenCondition { !lockController.isResolved.value }`; collect biometricLockEnabled → `window.addFlags/clearFlags(WindowManager.LayoutParams.FLAG_SECURE)` (G7, G8)
  - тест: LockController unit (isResolved до/после эмиссии); manual-чеклист для FLAG_SECURE (превью свитчера)
TEST_TYPES: unit, instrumented
CONSTRAINTS:
  - Удержание splash ограничено первой эмиссией DataStore (миллисекунды на здоровом I/O) — никаких таймеров.
  - FLAG_SECURE строго при biometricLockEnabled=true; в debug-сборках поведение то же (скриншоты тестов на разблокированном состоянии не страдают: лок в тестах выключен).
  - Существующие 36/36 DashboardContentUiTest не должны деградировать (лок в них не включается).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Контент не утекает мимо лока

  Scenario: Холодный старт без флеша
    Given лок включён
    When приложение запускается с холодного старта
    Then первый видимый кадр после splash — экран блокировки
    And балансы не появляются до успешной разблокировки

  Scenario: Превью в недавних приложениях скрыто
    Given лок включён и приложение свёрнуто
    When пользователь открывает системный переключатель задач
    Then превью приложения не показывает суммы и список операций

  Scenario: Без лока поведение прежнее
    Given лок выключен
    Then splash снимается по первому кадру и скриншоты разрешены
```

## Gap / context
Баг H5 аудита: `shouldShowLock` стартует false и выставляется асинхронно (G7) — несколько кадров
балансы видимы и кликабельны; FLAG_SECURE отсутствует вовсе (G8).

## Implementation links
- commit: db4e45d8207a6bdc8d687468e024c085f91822a2, 7591b502
- files: app/src/main/java/com/kshavrin/mymoney/MainActivity.kt; feature/lockscreen/src/main/java/com/kshavrin/mymoney/feature/lockscreen/overlay/LockController.kt; feature/lockscreen/src/test/java/com/kshavrin/mymoney/feature/lockscreen/overlay/LockControllerTest.kt; app/src/androidTest/java/com/kshavrin/mymoney/MainActivityWindowSecurityTest.kt
