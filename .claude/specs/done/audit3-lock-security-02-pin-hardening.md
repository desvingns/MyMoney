# PIN: версионированный KDF (600k) + lazy re-hash + анти-brute-force
Epic: audit3-lock-security
Order: 02 of 04
Status: done
Depends-on: audit3-lock-security-01
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: (1) Новый формат хэша `v2:<iterations>:<saltB64>:<dkB64>` с PBKDF2-SHA256 600 000 итераций; legacy-строки `saltB64:dkB64` продолжают верифицироваться с 10 000 и при первом успешном вводе лениво перехэшируются в v2 (пользователь ничего не замечает). (2) Троттлинг: после 5 неверных попыток — задержка 30с, удваивается за каждые следующие 5 (60с, 120с…, потолок 30 мин); счётчик/deadline в SecureStorage, обратный отсчёт виден на экране, успех сбрасывает счётчик.
LAYERS: domain, data
CHANGED_HINT:
  - feature/lockscreen/.../setup/PinHasher.kt:11-13,44-47 — формат v2 + парсер-диспетчер по префиксу; verify(legacy) → 10k, verify(v2) → итерации из строки; hash() всегда пишет v2/600k (G4)
  - core/datastore/.../SecureStorageImpl.kt:30,50-55 — новые поля failedPinAttempts: Int, pinLockoutDeadlineEpochMs: Long? + аксессоры (G5)
  - feature/lockscreen/.../overlay/LockOverlay.kt — после verify: успех → re-hash при legacy + сброс счётчика; неудача → инкремент, расчёт deadline; активный deadline → поле ввода заблокировано с отсчётом (G1-флоу из SPEC 01)
  - feature/lockscreen/src/main/res/values{,-ru}/strings.xml — строка «Повторите через N сек» (G9)
  - тесты: PinHasherTest — v2 round-trip, legacy verify, lazy re-hash; ThrottleTest — пороги 5/10/15, сброс, потолок
TEST_TYPES: unit
CONSTRAINTS:
  - Хэширование 600k — НЕ на main: через инжектированный @Named-диспетчер (конвенция проекта).
  - `LockOverlay.kt` — общий файл со SPEC 01: выполняется ПОСЛЕ него.
  - Троттлинг применяется только к PIN-вводу; биометрия живёт своим системным LOCKOUT.
  - Потолок задержки 30 мин (assumption); счётчик переживает рестарт процесса (хранение в SecureStorage, не в памяти).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Усиленный PIN

  Scenario: Прозрачная миграция legacy-хэша
    Given PIN сохранён в старом формате (10k итераций)
    When пользователь вводит верный PIN
    Then приложение разблокируется
    And хэш перезаписан в формате v2 с 600000 итераций

  Scenario: Троттлинг перебора
    Given пользователь ввёл 5 неверных PIN подряд
    Then ввод PIN заблокирован на 30 секунд с видимым отсчётом
    When он вводит ещё 5 неверных после разблокировки
    Then задержка удваивается до 60 секунд

  Scenario: Успех сбрасывает счётчик
    Given было 4 неверных попытки
    When пользователь вводит верный PIN
    Then счётчик попыток обнулён
```

## Gap / context
Баг M10 аудита: неограниченный перебор 4-значного пространства + 10k итераций (G4). Решения D3/D3b
из grill; версионирование обязательно — счётчик не зашит в legacy-строку.

## Implementation links
- commit: b1eefb8e, 9654b480
- files: core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/SecureStorage.kt; core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/SecureStorageImpl.kt; core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/model/SecureSettings.kt; core/datastore/src/androidTest/java/com/kshavrin/mymoney/core/datastore/SecureStorageTest.kt; feature/lockscreen/src/main/java/com/kshavrin/mymoney/feature/lockscreen/overlay/LockOverlay.kt; feature/lockscreen/src/main/java/com/kshavrin/mymoney/feature/lockscreen/overlay/PinKeypad.kt; feature/lockscreen/src/main/java/com/kshavrin/mymoney/feature/lockscreen/setup/PinHasher.kt; feature/lockscreen/src/main/res/values-ru/strings.xml; feature/lockscreen/src/main/res/values/strings.xml; feature/lockscreen/src/test/java/com/kshavrin/mymoney/feature/lockscreen/fake/FakeSecureStorage.kt; feature/lockscreen/src/test/java/com/kshavrin/mymoney/feature/lockscreen/setup/PinHasherTest.kt; feature/lockscreen/src/test/java/com/kshavrin/mymoney/feature/lockscreen/overlay/LockOverlayTest.kt
