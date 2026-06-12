# Реальные backup-правила + восстановление SecureStorage без краш-лупа
Epic: audit3-lock-security
Order: 04 of 04
Status: done
Depends-on: —
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: Перенос на новое устройство не должен превращаться в краш-луп. (1) backup_rules.xml и data_extraction_rules.xml перестают быть шаблонами: secure-prefs (`com.kshavrin.mymoney_secure.xml`) исключаются из cloud-backup И device-transfer; monefy.db и DataStore включаются (O3). (2) SecureStorageImpl: создание EncryptedSharedPreferences оборачивается в recovery — при GeneralSecurityException/IOException повреждённый файл удаляется и создаётся заново (лок/токены сбрасываются, приложение живёт).
LAYERS: data
CHANGED_HINT:
  - app/src/main/res/xml/backup_rules.xml — include БД/DataStore, exclude sharedpref com.kshavrin.mymoney_secure.xml (G6)
  - app/src/main/res/xml/data_extraction_rules.xml — те же правила для cloud-backup и device-transfer секций, убрать literal TODO (G6)
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/SecureStorageImpl.kt:17-28 — фабричная функция createPrefs() с try/catch: при провале deleteSharedPreferences/файл + повторное создание; (assumption) выделить creator-лямбду для unit-тестируемости (G5)
  - core/datastore/src/test или androidTest — кейс: повреждённый prefs-файл → объект создаётся, секреты пусты, краша нет
TEST_TYPES: unit, instrumented
CONSTRAINTS:
  - Исключение secure-prefs — безусловное (Keystore-ключ не переносится, восстановленный файл нерасшифруем).
  - Recovery теряет PIN/токены осознанно: деградация вместо краш-лупа; лок при этом выключается (biometricLockEnabled остаётся в DataStore — (assumption) сбросить его при recovery, иначе LockOverlay без pinHash → ветка из audit3-01).
  - `SecureStorageImpl.kt` затем правится в audit5-donut-perf-02 (lazy) — этот SPEC первый.
  - Manual-гейт: эмулировать restore (adb backup/restore или повреждение файла) по чеклисту Verifier.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Перенос на новое устройство безопасен

  Scenario: Восстановление из бэкапа не приносит секретов
    Given бэкап создан на старом устройстве
    When система восстанавливает приложение на новом устройстве
    Then файл secure-prefs отсутствует в восстановленных данных
    And приложение запускается без краша

  Scenario: Повреждённый secure-файл самовосстанавливается
    Given файл secure-prefs повреждён или нерасшифруем
    When приложение обращается к SecureStorage
    Then файл пересоздаётся пустым
    And блокировка выключена, краш-лупа нет
```

## Gap / context
Баг C3 аудита: allowBackup=true с шаблонными правилами (G6) бэкапит всё; на новом устройстве
EncryptedSharedPreferences бросает в конструкторе синглтона (G5) — с локом это краш-луп на экране блокировки.

## Implementation links
- commit: bb487398, 281e56ed
- files: app/src/main/res/xml/backup_rules.xml; app/src/main/res/xml/data_extraction_rules.xml; core/datastore/build.gradle.kts; core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/SecureStorageImpl.kt; core/datastore/src/test/java/com/kshavrin/mymoney/core/datastore/SecureStorageImplTest.kt; core/datastore/src/androidTest/java/com/kshavrin/mymoney/core/datastore/SecureStorageTest.kt; feature/lockscreen/src/main/java/com/kshavrin/mymoney/feature/lockscreen/overlay/LockController.kt; feature/lockscreen/src/test/java/com/kshavrin/mymoney/feature/lockscreen/overlay/LockControllerTest.kt; app/src/test/java/com/kshavrin/mymoney/BackupRulesTest.kt; app/src/test/java/com/kshavrin/mymoney/DataExtractionRulesTest.kt
