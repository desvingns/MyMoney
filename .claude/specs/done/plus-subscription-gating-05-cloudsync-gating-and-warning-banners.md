# Гейтинг Shared на экране Cloud sync и баннеры предупреждений
Epic: plus-subscription-gating
Order: 05 of 10
Status: done
Depends-on: plus-subscription-gating-04
Risk-signals: entitlement, DI graph, navigation, server-authoritative state, cross-module data flow
Date: 2026-08-12

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Экран Cloud sync перестаёт пускать в Shared-режим без Plus: попытка включить его ведёт на
  paywall (SPEC 04). Плюс — баннеры предупреждений платного пути (за 3 дня до конца триала, при входе
  в Grace, за 1 день до Expired), read-only состояние воркспейса на время Grace владельца и явное
  объяснение правила «платит владелец, участники бесплатны» **и при создании, и при вступлении**.
LAYERS: presentation
CHANGED_HINT:
  - `feature/cloudsync/src/main/java/com/kshavrin/mymoney/feature/cloudsync/CloudSyncState.kt` —
    расширить `SharedCardState` (:46-55, G9): `entitlementState: EntitlementState`,
    `isWorkspaceReadOnly: Boolean`, `warning: EntitlementWarning?`. Существующий
    `errorBannerRes: Int?` (:23, G10) не трогать — предупреждение это **отдельное** поле, у него
    другой тон и другая кнопка.
  - `.../CloudSyncViewModel.kt` — подписка на `EntitlementRepository.entitlement` (SPEC 01/03);
    перехват `SharedSetupClicked` / `SharedCreateWorkspace` / `SharedJoinWorkspace`
    (`CloudSyncEvent.kt:54-66`, G9): при `Free`/`EXPIRED` вместо запуска — `CloudSyncAction`
    навигации на paywall. **Клэш с SPEC 06** (тот же файл) — этот SPEC идёт первым.
  - `.../CloudSyncScreen.kt` (рендерит `SharedCardState`, G9) — баннер предупреждения над карточкой Shared с CTA «Продлить» →
    paywall; в read-only режиме кнопка синхронизации и создание инвайта неактивны с пояснением.
  - `.../CloudSyncEvent.kt` (G9, :45-111) — `PaywallRequested(entryPoint)`, `WarningDismissed`,
    `WarningActionClicked`.
  - `app/src/main/java/com/kshavrin/mymoney/navigation/` — второй вход на маршрут `PaywallRoute`
    (первый добавлен SPEC 04) с `entryPoint = SharedSyncGate`.
  - `feature/cloudsync/src/main/res/values/strings.xml` + `values-ru/strings.xml` — тексты трёх
    предупреждений, read-only пояснения и правила «платит владелец» для диалогов создания и
    вступления (`SharedDialog.Setup`, `CloudSyncState.kt:57-81`).
TEST_TYPES: unit, compose-ui
CONSTRAINTS:
  - **Клиентский гейт — косметика.** Он существует, чтобы пользователь не упирался в невнятную
    серверную ошибку. Реальный отказ приходит от сервера (SPEC 02); UI обязан корректно отрисовать и
    его — код `entitlement_required` мапится в тот же баннер, даже если локальное состояние ещё
    показывает Plus. *(assumption, O4)*
  - Правило «платит владелец, участники бесплатны» показывается **дважды**: в диалоге создания
    воркспейса («подписку оплачиваешь ты») и в диалоге вступления по инвайту («тебе платить не
    нужно, платит владелец»). Одного места недостаточно — требование заказчика.
  - Grace владельца = **весь воркспейс read-only для всех участников**, а не только для владельца.
    Участник в read-only видит объяснение, что владелец не продлил подписку, и **не** видит
    предложения купить — «перехват оплаты» это v2 (см. SPEC 02, `payer_user_id`).
  - Предупреждения не блокируют работу: баннер закрывается, но появляется снова при следующем
    пересчёте, пока состояние не изменилось.
  - Уже подключённый пользователь, у которого Plus **пропал**, не выкидывается из режима этим
    SPEC-ом — переход в LocalOnly выполняет SPEC 06. Здесь только отображение.
  - Ни одна user-facing строка не хардкодится; RU-перевод обязателен (gate
    `review-2026-07-05-missing-translation-gate`).
  - `:feature:cloudsync` не должен получить зависимость на `:core:billing` — только на интерфейс в
    `:core:domain` (ADR-0010 :124-126, G19).
=== END SPEC ===

## Acceptance

```gherkin
Feature: Гейтинг общего воркспейса на экране облачной синхронизации
  Бесплатный пользователь видит предложение подписки, а не серверную ошибку.

  Scenario: Попытка включить Shared без подписки
    Given у пользователя нет Plus
    When он пытается включить общий воркспейс
    Then открывается экран подписки
    And общий воркспейс не подключается

  Scenario: Приватный синк остаётся бесплатным
    Given у пользователя нет Plus
    When он подключает Dropbox или Google Drive
    Then подключение проходит без каких-либо предложений подписки

  Scenario: Предупреждение за три дня до конца триала
    Given до конца пробного периода осталось три дня
    When пользователь открывает экран облачной синхронизации
    Then над карточкой общего воркспейса показан баннер о скором окончании пробного периода
    And кнопка баннера ведёт на экран подписки

  Scenario: Вход в Grace
    Given подписка владельца истекла и начался льготный период
    When пользователь открывает экран облачной синхронизации
    Then показан баннер о льготном периоде с датой его окончания

  Scenario: Read-only для участника при Grace владельца
    Given владелец воркспейса находится в льготном периоде
    When участник открывает экран облачной синхронизации
    Then ему объяснено, что владелец не продлил подписку и воркспейс доступен только для чтения
    And кнопка синхронизации и создание приглашения недоступны
    And предложения оплатить подписку участнику не показывается

  Scenario: Правило оплаты при создании воркспейса
    Given пользователь с Plus создаёт общий воркспейс
    Then перед созданием ему сказано, что подписку оплачивает он как владелец

  Scenario: Правило оплаты при вступлении по приглашению
    Given пользователь вступает в чужой воркспейс по приглашению
    Then ему сказано, что платить не нужно — подписку оплачивает владелец

  Scenario: Серверный отказ при устаревшем локальном состоянии
    Given локально пользователь ещё считается обладателем Plus
    And сервер уже отказывает по причине истёкшего права
    When приложение пытается синхронизироваться
    Then показывается тот же баннер об истёкшей подписке, а не техническая ошибка
```

## Gap / context

Сейчас карточка Shared гейтится только флагом сборки `sharedSyncEnabled()` (G1) — «фича есть в этой
сборке», что никак не связано с «этому пользователю она разрешена». Без этого SPEC-а бесплатный
пользователь после флипа флага (SPEC 09) упрётся в невнятный отказ сервера, а обязательные
предупреждения платного пути негде показывать.

## Implementation links
- commits: `08ec42be`, `fdb88364`, `070ca33e`, `83347850`, `090c4f26`, `e31728ce`, `a74ed6ea`, `afc52d65`, `b7ac3d4b`, `a9f2dee8`, `fb9adff5`, `a3a8311c`, `c5611a88`, `ac6f7534`, `8fc58ad2`, `bb7ac5f3`
- verification: deterministic reviewer passed; scoped runner `547 passed / 0 failed / 0 skipped`; full runner `2219 passed / 0 failed / 0 skipped`; detekt/lint passed; explicit `:core:domain:test :core:sync:test` passed; independent critic and full verifier passed.
- server contract: authenticated workspace SELECT grant for `billing_state` and `billing_state_until` plus pgTAP schema contract.
- files: navigation, domain entitlement/use cases, network HTTP/RPC/realtime, sync coordinator/use cases, CloudSync state/viewmodel/screen/resources/tests, app CloudSync UI tests, Supabase migration/schema test.
