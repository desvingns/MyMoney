# Переход в LocalOnly: отцепка без потери данных и обратное подключение
Epic: plus-subscription-gating
Order: 06 of 10
Status: done
Depends-on: plus-subscription-gating-01, plus-subscription-gating-03
Date: 2026-08-12
Risk-signals: session/auth lifecycle, entitlement, persistence, concurrency, cross-module data flow

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Реализовать последний переход машины состояний — `Expired → LocalOnly`. При отцепке делается
  полный локальный снапшот; **ни одна транзакция, категория, аккаунт или цель не удаляется, включая
  пришедшие от других участников**; отключается только обмен — realtime, outbox-push и кнопка
  синхронизации. Повторная покупка переподключает воркспейс и доставляет накопленные локальные
  изменения. Тем же путём отрабатывает killswitch (D8, SPEC 09) и истечение 24-часового окна ad-Plus
  (диалог «оплатите подписку, чтобы продолжить» → при отказе немедленная отцепка).
LAYERS: data, domain
CHANGED_HINT:
  - `core/sync/src/main/java/com/kshavrin/mymoney/core/sync/shared/SharedSyncCoordinator.kt` (G6,
    :28-92) — новый метод `suspend fun detachToLocalOnly(reason: LocalOnlyReason): Result<Unit>` и
    `suspend fun reattachAfterEntitlementRestored(): Result<Unit>`; `LocalOnlyReason { EntitlementExpired,
    RemoteKillswitch, AdRewardWindowEnded }`. Дефолтные реализации — по образцу уже существующих
    default-методов интерфейса (`disconnectFromDevice`, :69-70).
  - `core/sync/src/main/java/.../shared/SharedSyncCoordinatorImpl.kt`:
    - `detachToLocalOnly` — сначала `backupRepository.createInternalBackup()` (G7 — уже используется
      в пяти местах: :149,170,192,319,351), затем `stopForegroundRealtime()`, снятие подписки
      realtime и запрет `syncNow()`. **Никаких `clearSharedOutbox()`** — см. CONSTRAINTS.
    - `reattachAfterEntitlementRestored` — восстановление realtime и полный push накопленного
      outbox по существующему пути `syncNow()`.
    - гейт на входе в `syncNow()` / `startForegroundRealtime()` / `createInvite()`: при LocalOnly —
      немедленный отказ без сетевого вызова.
  - `core/datastore/.../` — персистентный флаг `sharedLocalOnly(reason, since)`, чтобы состояние
    переживало перезапуск процесса и не зависело от того, дошёл ли клиент до сервера.
  - `feature/cloudsync/.../CloudSyncViewModel.kt` — реакция на `EntitlementState.EXPIRED`: вызвать
    `detachToLocalOnly`, показать итоговое состояние карточки; на возврат Plus — вызвать
    `reattachAfterEntitlementRestored`. **Клэш с SPEC 05** (тот же файл) — идёт после него.
  - `feature/cloudsync/.../CloudSyncState.kt` / `CloudSyncScreen.kt` — состояние «локальный режим»:
    что данные целы, что накопленные изменения отправятся после возобновления подписки, и путь
    к внутренним снапшотам (`SharedDialog.InternalBackups`, `CloudSyncState.kt:72`).
  - `feature/cloudsync/.../CloudSyncEvent.kt` (G9, :45-111) + строки — диалог ad-Plus «оплатите подписку, чтобы
    продолжить пользоваться синхронизацией» с двумя исходами: на paywall / отказ → немедленная
    отцепка. Реализация самой награды — следующий эпик; здесь только ветка состояний (D6).
TEST_TYPES: unit, dao, instrumented
CONSTRAINTS:
  - **Outbox не чистится.** `clearSharedOutbox()` вызывается в пяти местах координатора
    (`SharedSyncCoordinatorImpl.kt:374,441,470,496`, G8) — ни один из них не должен сработать на
    пути отцепки, иначе требование «повторная покупка доставляет накопленные локальные изменения»
    невыполнимо. Это самая вероятная ошибка реализации — вынести в отдельный тест.
  - **Ни одной удалённой строки.** Данные, пришедшие от других участников, остаются в локальной базе
    как обычные записи. Отцепка отключает обмен, а не владение данными.
  - Снапшот делается **до** любых деструктивных действий и его провал отменяет отцепку (паттерн уже
    применён в координаторе: снапшот → операция).
  - У ad-Plus **grace-периода нет вообще** (D6/SPEC 01): по истечении 24 часов сразу диалог, при
    отказе — немедленная отцепка. Никаких 7 дней.
  - Переход обязан быть идемпотентным: повторный вызов `detachToLocalOnly` в уже локальном режиме —
    no-op без нового снапшота (иначе воркер размножит снапшоты).
  - LocalOnly переживает перезапуск процесса: состояние в DataStore, а не только в памяти.
  - Восстановление после возврата Plus не должно потерять порядок операций — использовать
    существующий путь `syncNow()`, а не самописный push.
=== END SPEC ===

## Acceptance

```gherkin
Feature: Локальный режим после потери подписки
  Отключается обмен, а не данные.

  Scenario: Отцепка сохраняет все данные
    Given пользователь состоял в общем воркспейсе и получил записи от других участников
    When его подписка окончательно истекает и приложение переводит его в локальный режим
    Then ни одна транзакция, категория, счёт или цель не удаляется
    And записи других участников остаются на месте

  Scenario: Снапшот делается до отцепки
    Given пользователь переходит в локальный режим
    Then перед отключением обмена создаётся полный локальный снапшот
    And он доступен в списке внутренних резервных копий

  Scenario: Обмен выключается полностью
    Given пользователь в локальном режиме
    Then обновления в реальном времени не приходят
    And кнопка синхронизации недоступна
    And исходящие изменения не отправляются на сервер

  Scenario: Локальные изменения копятся
    Given пользователь в локальном режиме
    When он добавляет несколько транзакций
    Then они сохраняются локально
    And помечаются как ожидающие отправки

  Scenario: Повторная покупка доставляет накопленное
    Given пользователь в локальном режиме и накопил ожидающие изменения
    When он снова оформляет подписку
    Then воркспейс переподключается
    And все накопленные изменения доставляются на сервер в исходном порядке

  Scenario: Владелец не продлил — участники уходят в локальный режим с целыми данными
    Given льготный период владельца воркспейса закончился
    When приложение участника обрабатывает это состояние
    Then участник переходит в локальный режим
    And все его данные, включая пришедшие от других, сохраняются полностью

  Scenario: Истечение окна за рекламу без льготного периода
    Given Plus получен за просмотр рекламы и его 24 часа истекли
    When приложение обрабатывает это состояние
    Then показывается диалог с предложением оформить подписку
    And при отказе отцепка происходит немедленно, без льготного периода

  Scenario: Повторный переход в локальный режим ничего не ломает
    Given пользователь уже находится в локальном режиме
    When приложение снова обрабатывает истёкшее право
    Then новый снапшот не создаётся
    And состояние не меняется
```

## Gap / context

Это то место, где гейтинг может уничтожить данные пользователя. Координатор уже умеет делать
внутренний снапшот (G7), но все существующие «отключающие» пути параллельно чистят outbox (G8) —
на пути отцепки это ровно то, чего делать нельзя. Плюс сам путь «Expired → LocalOnly» пока не
существует ни в каком виде: `disconnectFromDevice()` — это ручное действие пользователя, а не
автоматический переход по потере права.

## Handoff / точка продолжения

Дата handoff: 2026-08-15. Этот SPEC остаётся единственным обрабатываемым SPEC и намеренно остаётся
в `active`: до `done` его не доводить, пока не выполнены все пункты ниже.

### Что уже реализовано

Последовательность коммитов текущей реализации:

- `2d93364f` — базовый переход в LocalOnly, DataStore-флаг, coordinator gates, UI-состояние и ad-Plus ветка.
- `3a9161e6` — сохранение LocalOnly при неуспешном reattach, owner/participant semantics и cancellation safety.
- `300490d6` — сохранение контекста восстановления при cold start и после продления владельцем.
- `8f34d503` — устранён рекурсивный refresh/reattach loop после неудачного восстановления.
- `062b5fa0` — раздельный owner/participant/killswitch/unknown copy для LocalOnly UI.
- `c8291603` — дополнительное укрепление recovery flow и regression tests.

Уже покрыты: персистентный LocalOnly через DataStore, сохранение outbox и локальных данных,
гейты `syncNow`/realtime/invite, идемпотентность перехода, возврат Plus, отказ от ad-Plus,
перезапуск процесса, owner/participant UI copy и защита от refresh-loop.

### Где остановилась текущая сессия

Третий проход semantic reviewer снова завершился `pass=false` с двумя блокерами. По контракту
MP Dev после третьего blocker-pass дальнейший patch в этой сессии остановлен; был выполнен только
read-only architect preflight. Его verdict: `PATCH ALLOWED`, отдельное пользовательское/design-
решение не требуется.

Блокер 1 — destructive auth/role recovery:

- В `core/sync/.../SharedSyncCoordinatorImpl.kt` путь `clearSharedStateOnAuthFailure()` может
  вызвать полное revoke/очистку.
- При persisted LocalOnly это способно удалить binding, SharedStore/cursor и shared outbox при
  auth failure или неизвестной роли.
- Исправление должно быть fail-closed и неразрушающим: при LocalOnly запрещены
  `clearBinding()`, `sharedStore.clear()` и `clearSharedOutbox()`; разрешены только остановка
  transport/jobs и обработка недействительного auth token.

Блокер 2 — неверный порядок detach:

- В `feature/cloudsync/.../CloudSyncViewModel.kt` `detachToLocalOnly()` и путь RemoteKillswitch
  выставляют read-only/останавливают realtime до завершения `createInternalBackup()`.
- При ошибке или cancellation snapshot приложение остаётся отключённым, но durable LocalOnly не
  записан.
- Исправление должно сделать coordinator единственным владельцем порядка:
  `cancellable snapshot → durable LocalOnly commit → teardown`; до успешного snapshot VM не меняет
  read-only/realtime, а failure/cancellation сохраняет исходное состояние.

Дополнительные замечания reviewer:

- Отсутствующее active self-membership сейчас нельзя трактовать как participant; нужен явный
  tri-state `VerifiedOwner | VerifiedParticipant | Unknown`.
- Нужен regression test: auth failure в LocalOnly сохраняет reason/since, binding, cursor и все
  outbox rows.

### Что продолжить в Claude

1. Прочитать этот handoff и текущую реализацию после `c8291603`; не выбирать второй SPEC и не
   переводить этот файл в `done` до завершения всех проверок.
2. Перенести ownership перехода в `SharedSyncCoordinator`: VM оставляет только pending UI,
   coordinator выполняет snapshot/commit/teardown под mutex и возвращает результат.
3. Ввести/сохранить sync-level tri-state роли. `Unknown` должен блокировать reattach без push/pull
   и без очистки LocalOnly/outbox; recovery разрешён только после свежей authenticated active
   membership и Active billing.
4. Добавить/обновить тесты для auth-failure retention, `Unknown`, snapshot failure и
   `CancellationException`; не использовать wall-clock timeout или `advanceUntilIdle()` для
   бесконечных realtime collectors.
5. Повторить полный MP Dev цикл: reviewer → tests/runner → semantic reviewer → verifier. Исправить
   все найденные проблемы и только после зелёного результата заполнить `Implementation links`,
   переместить SPEC в `done` и записать commit/changed files.

### Acceptance-инварианты для финальной передачи

- Auth failure или `Unknown` в LocalOnly не очищает LocalOnly, binding, cursor или ни одну outbox row.
- Ни один LocalOnly-путь не вызывает `clearSharedOutbox()`.
- Ошибка/cancellation snapshot не делает durable detach и не останавливает realtime/UI transport.
- После durable LocalOnly никакой sync, realtime, invite, conflict mutation или outbox publish не
  достигает сети; локальные изменения продолжают накапливаться.
- Reattach снимает LocalOnly только после verified role + Active billing и успешного ordered sync.
- `Unknown` никогда не отображается и не ведёт себя как participant.

## Deferred hardening

- **Teardown-failure return/commit mismatch (non-blocking, semantic-reviewer cycle 5, `SharedSyncCoordinatorImpl.kt` `detachToLocalOnly`).**
  `sharedStore.setLocalOnly()` commits inside the same `NonCancellable` block as the later
  `stopForegroundRealtimeAndJoin()` / `syncScheduler.cancelAllSync()` teardown steps. If a teardown
  step throws after the commit, `detachToLocalOnly` still returns `Result.failure()` even though
  LocalOnly is durably set — the VM then shows a stale non-LocalOnly UI with an error banner. All
  network gates (`ensureNotInLocalOnlyMode()`, realtime start, invite create) already block on the
  committed state, so no data/network leak occurs, and the next realtime-supervisor `syncNow()` hit
  self-heals by discovering the committed state and refreshing the UI. No regression test exists for
  this sub-path (`FakeSyncScheduler`/`FakeSharedRealtime` are infallible in the coordinator suite).
  Follow-up: either return `Result.success` when `localOnlyState()` is already committed on teardown
  failure, or move `setLocalOnly()` after teardown; add a `cancelAllSync()`-failure regression test.
- A related uncertainty was raised but not confirmed as a live path: whether any non-VM caller can
  invoke `sharedCoordinator.resolveConflict()` directly while LocalOnly is committed, bypassing the
  VM-level `canWrite()`/`isWorkspaceReadOnly` gate. **Resolved by the independent critic**: grep of
  all production callers found a single call site (`CloudSyncViewModel.resolveSharedConflict()`),
  already gated by `canWrite()`. No action needed.
- **AUTH-RACE-001 test-quality gap (non-blocking, confirmation-pass finding, commit `ef2e10e8`).**
  The fix wraps `superviseForegroundRealtime`'s terminal-failure `clearSharedStateOnAuthFailure` call
  in `operationMutex.withLock`, verified correct by code-trace (no deadlock: the mutex holder cancels
  the realtime job during its own teardown). The new regression test
  (`realtime auth failure racing a local only commit preserves binding cursor and outbox`) runs on
  `StandardTestDispatcher` (cooperative, single-threaded), so it proves the functional branch
  (`localOnly != null` → non-destructive path) but cannot actually exercise the real
  multi-threaded TOCTOU window the mutex closes — it would pass even if the `withLock` wrapper were
  removed, as long as the if/else check remained. Follow-up: either rename the test to be explicit
  that it covers only the functional branch, or add a dispatcher/hook-based test that proves the
  mutex is load-bearing (e.g. a controlled delay between the read and the branch in a fake store).
- **Uncertainty, not yet triaged**: for a `SyncError.Conflict` terminal failure while LocalOnly is
  committed, `isAuthFailure()` is false so `clearSharedStateOnAuthFailure` is a no-op (correct — not
  an auth path) and the realtime generation is not invalidated; unclear whether the resulting status
  should be `Error` or `Inactive` while already LocalOnly, and no test covers this sub-path. Worth a
  quick look in a follow-up SPEC; not part of this SPEC's acceptance criteria.

## Implementation links
- implementation commits: `2d93364f`, `3a9161e6`, `300490d6`, `8f34d503`, `062b5fa0`, `c8291603`,
  `b4335959` (repair: AUTH-RECOVERY-001, DETACH-ORDER-001 partial), `2a708a9a` (repair: DETACH-ORDER-001
  RemoteKillswitch path + ROLE-DEFAULT-001), `ef2e10e8` (repair: AUTH-RACE-001 mutex fix)
- final commit: `ef2e10e8` — pushed to `main` (`8f330a96..ef2e10e8`)
- final runner/verifier: scoped (`:core:sync`, `:feature:cloudsync`) `289 passed / 0 failed / 0 skipped`;
  full project runner `2248 passed / 0 failed / 0 skipped`, detekt ok, lint ok; deterministic reviewer
  0 violations at every cycle; 2 semantic-review repair cycles + 1 independent-critic pass + 1
  confirmation semantic-review pass all resolved `pass=true`; full Verifier `pass=true`
  (`hilt_graph: ok`, `tests_exist: ok`, `stale_tests: ok`; `nav_wired`/`room_schema`: `n/a`, no screen
  or schema surface touched)
- files: `core/sync/src/main/java/com/kshavrin/mymoney/core/sync/shared/SharedSyncCoordinator.kt`,
  `core/sync/src/main/java/com/kshavrin/mymoney/core/sync/shared/SharedSyncCoordinatorImpl.kt`,
  `core/sync/src/test/java/com/kshavrin/mymoney/core/sync/shared/SharedSyncCoordinatorImplTest.kt`,
  `core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/SharedSyncStore.kt`,
  `core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/SharedSyncStoreImpl.kt`,
  `core/datastore/src/test/java/com/kshavrin/mymoney/core/datastore/SharedSyncStoreImplTest.kt`,
  `feature/cloudsync/src/main/java/com/kshavrin/mymoney/feature/cloudsync/CloudSyncViewModel.kt`,
  `feature/cloudsync/src/test/java/com/kshavrin/mymoney/feature/cloudsync/CloudSyncViewModelTest.kt`,
  `feature/cloudsync/src/main/java/com/kshavrin/mymoney/feature/cloudsync/CloudSyncEvent.kt`,
  `feature/cloudsync/src/main/java/com/kshavrin/mymoney/feature/cloudsync/CloudSyncScreen.kt`,
  `feature/cloudsync/src/main/java/com/kshavrin/mymoney/feature/cloudsync/CloudSyncState.kt`,
  `feature/cloudsync/src/main/res/values/strings.xml`,
  `feature/cloudsync/src/main/res/values-ru/strings.xml`,
  `feature/cloudsync/src/test/java/com/kshavrin/mymoney/feature/cloudsync/CloudSyncScreenContentTest.kt`,
  `feature/cloudsync/src/test/java/com/kshavrin/mymoney/feature/cloudsync/fake/FakeRemoteConfigRepository.kt`
