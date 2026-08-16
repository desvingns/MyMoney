# Удалённый killswitch Shared и переворот PLAY_RELEASE_SYNC_ENABLED
Epic: plus-subscription-gating
Order: 09 of 10
Status: active
Depends-on: plus-subscription-gating-01 … 08 (весь эпик)
Date: 2026-08-12
Acceptance-matrix: build_flag=enabled,disabled; remote_config=absent,enabled,disabled
Risk-signals: entitlement, billing, build, sync, server-authoritative, cross-module

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Два неразрывно связанных изменения релизной конфигурации, выполняемых **последними** в эпике:
  (1) дописать `RemoteConfigRepositoryImpl.sharedSyncEnabled()` так, чтобы он читал уже объявленный,
  но никем не используемый ключ `KEY_SHARED_SYNC` — аварийный тормоз на случай, если расходы на
  Supabase выйдут из-под контроля; (2) перевернуть `sync.playReleaseEnabled` в релизных дефолтах,
  чтобы Shared-синхронизация включилась в продакшене — но только сейчас, когда гейтинг уже готов.
  Плюс ADR-0011, снимающий противоречие с ADR-0010 D1.
LAYERS: data, build
CHANGED_HINT:
  - `core/sync/src/main/java/com/kshavrin/mymoney/core/sync/remoteconfig/RemoteConfigRepositoryImpl.kt:55-56`
    (G1) — `sharedSyncEnabled()` становится
    `(BuildConfig.PLAY_INTERNAL_SYNC_ENABLED || BuildConfig.PLAY_RELEASE_SYNC_ENABLED || syncForced())
    && (config?.getBoolean(KEY_SHARED_SYNC) ?: DEFAULT_SHARED_SYNC_WHEN_BUILD_ENABLED)`.
    Константа `KEY_SHARED_SYNC = "shared_sync_enabled"` уже объявлена (:75), но не читается.
  - Тот же файл, :83 — `DEFAULT_SHARED_SYNC` сейчас `false`, что при чтении ключа мгновенно выключило
    бы фичу в сборках без Firebase (`config == null`, G3). Дефолт для **этого** множителя обязан быть
    `true`: killswitch — это «выключить включённое», а не «включить выключенное». Старую константу
    не переиспользовать вслепую.
  - `core/sync/src/main/res/xml/remote_config_defaults.xml:20` — привести значение
    `shared_sync_enabled` в соответствие с новым смыслом (`true`). Запись там уже есть и до сих пор
    никем не читалась (ADR-0010:50-54, G20).
  - `app/build.gradle.kts:71-72` **и** `core/sync/build.gradle.kts:19-20` (G2) — `sync.playReleaseEnabled`
    дефолтится в `true`. **Оба файла обязательны**: гейт читает `BuildConfig` именно из `:core:sync`,
    правка только в `:app` не даст эффекта.
  - `docs/DECISIONS/ADR-0011-shared-sync-remote-killswitch.md` (новый) — amend'ит ADR-0010 D1
    («There is no remote kill switch… separate work», :50-54 и :196-198, G20): фиксирует наличие
    killswitch, его семантику (полная отцепка в LocalOnly, D8) и то, что откат по-прежнему
    предпочтителен через новый релиз, а killswitch — аварийный инструмент по расходам.
  - `docs/DECISIONS/ADR-0010-monetization.md` — одна строка в шапке: `Amended by: ADR-0011`.
  - `feature/cloudsync/.../CloudSyncViewModel.kt` — при `sharedSyncEnabled() == false` у уже
    подключённого пользователя вызывается `detachToLocalOnly(RemoteKillswitch)` (SPEC 06). Ветка уже
    реализована — здесь только её включение.
TEST_TYPES: unit
CONSTRAINTS:
  - **Этот SPEC идёт последним в эпике по прямому требованию ADR-0010 D1 (:38-48, G19).** Переворот
    флага до готового entitlement-гейта открывает окно, в котором Supabase-воркспейс достаётся всем
    бесплатно — ровно тот расход, который эпик и закрывает. `/mp --feature --next` не должен взять
    этот SPEC раньше, чем 01-08 переехали в `done/`.
  - **Флип активирует `requireSyncRuntimeConfiguration()`** (`app/build.gradle.kts:89-101`, G4): билд
    падает, если не заданы непустые Dropbox app key, Supabase URL, anon key и Google web client ID.
    Проверить `local.properties` локально и секреты в CI **до** мержа, иначе красный CI на пустом месте.
  - Killswitch = **полная отцепка в LocalOnly** (D8), а не read-only. Read-only оставляет realtime и
    pull — то есть именно те расходы, ради обрезания которых killswitch и нужен. Переиспользовать
    путь из SPEC 06, не писать второй.
  - Killswitch применяется и к платящим пользователям. ADR-0011 обязан явно зафиксировать это как
    принятое следствие и упомянуть возврат средств как ручную процедуру. *(assumption)*
  - Debug-путь `syncForced()` (:59) не трогать: он уже игнорируется в release (`DEBUG == false`).
  - `RemoteConfigRepository.sharedSyncEnabled()` имеет default `= false` в интерфейсе
    (`core/domain/.../RemoteConfigRepository.kt:14`, G5) — фейки в тестах его не переопределяют.
    Проверить, что тесты Cloud sync (`FakeRemoteConfigRepository.kt`) обновлены под новую семантику.
=== END SPEC ===

## Acceptance

```gherkin
Feature: Релизное включение общего воркспейса и аварийный выключатель

  Scenario: В релизной сборке общий воркспейс доступен
    Given собрана релизная сборка с настроенными ключами
    And удалённый выключатель не активирован
    Then раздел общего воркспейса доступен пользователям

  Scenario: Аварийный выключатель отключает общий воркспейс
    Given удалённая конфигурация выставляет общий воркспейс в «выключено»
    When приложение обновляет удалённую конфигурацию
    Then подключённые пользователи переводятся в локальный режим
    And их данные сохраняются полностью
    And новые подключения к общему воркспейсу невозможны

  Scenario: Сборка без удалённой конфигурации остаётся работоспособной
    Given приложение собрано без конфигурации Firebase
    Then общий воркспейс доступен, как определяет флаг сборки
    And отсутствие удалённой конфигурации не выключает его

  Scenario: Отключение выключателя возвращает доступ
    Given пользователь был переведён в локальный режим аварийным выключателем
    And у него по-прежнему активная подписка
    When выключатель снимается и приложение обновляет конфигурацию
    Then воркспейс переподключается
    And накопленные локальные изменения доставляются

  Scenario: Флаг сборки остаётся главнее
    Given флаг сборки для релиза выключен
    And удалённая конфигурация разрешает общий воркспейс
    Then общий воркспейс недоступен
```

## Gap / context

`KEY_SHARED_SYNC` объявлен в коде, но не читается ни разу (G1) — ровно то же верно для ключей
Dropbox и GDrive; ADR-0010 честно это зафиксировал и объявил killswitch отдельной работой (G20).
Эта работа делается здесь, потому что без неё единственный способ остановить растущий счёт
Supabase — выпуск новой версии и ожидание, пока пользователи обновятся. Переворот
`PLAY_RELEASE_SYNC_ENABLED` привязан к тому же SPEC-у намеренно: включение платной фичи и её
аварийный выключатель обязаны появиться одновременно.

## Implementation links
- commit: `29913477` (feat), `1628b512` (docs — ADR-0010 supersession pointer), `54df5cf2` (fix — missed
  `core/network` playReleaseEnabled site, caught by the pre-existing CI contract test), `6332402e` (test —
  stale-test reconciliation)
- files:
  - app/build.gradle.kts
  - core/sync/build.gradle.kts
  - core/network/build.gradle.kts
  - core/sync/src/main/java/com/kshavrin/mymoney/core/sync/remoteconfig/RemoteConfigRepositoryImpl.kt
  - core/sync/src/main/res/xml/remote_config_defaults.xml
  - docs/DECISIONS/ADR-0011-shared-sync-remote-killswitch.md (new)
  - docs/DECISIONS/ADR-0010-monetization.md
  - app/src/test/java/com/kshavrin/mymoney/PlayInternalSyncCiContractTest.kt
  - core/sync/src/test/java/com/kshavrin/mymoney/core/sync/remoteconfig/RemoteConfigRepositoryImplTest.kt

## Deferred hardening
- `RemoteConfigRepositoryImplTest.kt:107` structural guard uses a location-agnostic `.contains("&&")`
  check (independent-critic warning, non-blocking): a future refactor that relocates the killswitch
  conjunction while leaving other `&&` operators in the file would silently stop being caught. Fix:
  narrow the check to `.contains("&& (config?.getBoolean(KEY_SHARED_SYNC)")` or equivalent.
- Verify `local.properties` locally and CI secrets (Dropbox app key, Supabase URL, anon key, Google web
  client ID) BEFORE the next release build/CI run — `requireSyncRuntimeConfiguration()` is now active
  in the release/internal config paths per this SPEC's own stated constraint, and a missing secret will
  fail the build at config time, not at runtime.
