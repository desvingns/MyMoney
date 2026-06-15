# Манифест: мёртвые intent-фильтры и крашопасная AuthActivity
Epic: audit8-hygiene
Order: 03 of 04
Status: done
Depends-on: —
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: Приложение перестаёт ловить интенты, которые не умеет обрабатывать: (1) intent-фильтры monefy:// (VIEW+BROWSABLE) и DRIVE_OPEN удаляются из манифеста до реализации обработки (MainActivity читает только shortcut-extras — переход по ссылке сейчас просто открывает приложение, вводя в заблуждение); (2) узел com.dropbox.core.android.AuthActivity (exported, схема db-PLACEHOLDER_DROPBOX_APP_KEY, класс отсутствует в зависимостях) удаляется/гейтится до закрытия OQ-2 — переход по db-схеме из браузера сейчас крашит.
LAYERS: manifest
CHANGED_HINT:
  - app/src/main/AndroidManifest.xml:37-47 — убрать intent-фильтры monefy:// и DRIVE_OPEN; останутся MAIN/LAUNCHER + shortcuts (G6)
  - feature/cloudsync/src/main/AndroidManifest.xml — убрать узел AuthActivity вместе с tools:ignore="MissingClass" (вернётся настоящим при OQ-2 с реальным app key) (G6)
  - manual-проверка: `adb shell am start -a android.intent.action.VIEW -d "monefy://x"` больше не резолвится в приложение; deep-link тесты в проекте отсутствуют — регрессий нет
TEST_TYPES: unit
CONSTRAINTS:
  - App-shortcuts (G2 эпика) НЕ трогать — они через shortcut-extras, не через эти фильтры.
  - Удаление фиксируется в overview эпика как «вернуть при реализации deep-links» — намеренный откат функциональности-заглушки, не потеря.
  - :app:assembleDebug + lintDebug зелёные (lint перестанет ругаться на MissingClass).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Манифест честен

  Scenario: Незнакомая схема не открывает приложение
    When система резолвит VIEW-интент monefy://anything
    Then приложение не предлагается обработчиком

  Scenario: db-схема не крашит
    When браузер открывает ссылку db-PLACEHOLDER://auth
    Then приложение не крашится (активность не экспонируется)

  Scenario: Запуск и shortcuts живы
    Then обычный запуск и лонг-пресс shortcuts работают как раньше
```

## Gap / context
Аудит P3.17/P1.4 (G6): фильтры-заглушки вводят в заблуждение и создают крашопасную
экспонированную активность с отсутствующим классом.

## Implementation links
- commit: dd434188 (fix: drop stub deep-link filters and unbacked Dropbox AuthActivity)
- files:
  - app/src/main/AndroidManifest.xml — removed monefy:// VIEW+BROWSABLE and DRIVE_OPEN intent-filters; kept MAIN/LAUNCHER + @xml/shortcuts meta-data
  - core/sync/src/main/AndroidManifest.xml — removed exported com.dropbox.core.android.AuthActivity node (real location, not feature/cloudsync as the SPEC hint guessed); left empty <application/> with an OQ-2 restoration comment
- verification: :app:assembleDebug GREEN (--rerun-tasks, full manifest merge); no unit tests (manifest-only, no testable surface); runner script pass:false is the known false-neg (absent detekt/jacoco tasks + 3 pre-existing FullBackupContent lint errors in backup_rules.xml/data_extraction_rules.xml, out of scope). MissingClass lint complaint now gone.
- deferred (intentional): real Dropbox AuthActivity (com.dropbox.core.android.AuthActivity, scheme db-<appKey>) returns at OQ-2 once dropbox-android-sdk + real app key are supplied; monefy:// / DRIVE_OPEN deep-links return when deep-link handling is actually implemented.
