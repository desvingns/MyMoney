# Supabase: чтение entitlement и серверный гейтинг shared-RPC
Epic: plus-subscription-gating
Order: 02 of 10
Status: done
Depends-on: plus-subscription-gating-01
Date: 2026-08-12

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android (серверная часть — Supabase/Postgres + Deno Edge Functions)
WHAT: Сделать сервер единственной точкой контроля доступа к shared-воркспейсу. Закоммитить уже
  написанную, но незакоммиченную схему монетизации и Edge Functions как фундамент (D1), добавить
  `security definer` RPC, которым клиент читает свой entitlement, привязать воркспейс к плательщику
  (`payer_user_id` + денормализованное `billing_state`, D5) и **загейтить все shared-RPC на состояние
  плательщика**, а не вызывающего. После этого истёкший пользователь перестаёт обслуживаться сервером
  даже если приложение было закрыто и клиентская проверка не выполнялась.
LAYERS: server, data
CHANGED_HINT:
  - `supabase/migrations/20260812130000_monetization_schema.sql` — закоммитить как есть (G11):
    таблицы `entitlements` / `supporters` / `ad_rewards` / `provider_events` / `activation_codes`,
    RLS «select own» + `revoke all ... from public, anon, authenticated`, `redeem_activation_code(p_code)`.
  - `supabase/migrations/20260812140000_admob_reward_grant.sql` — закоммитить как есть (G11): триггер
    `private.grant_admob_plus_from_reward()`, 5 наград → `now() + 24h`, окно не стакается.
  - `supabase/functions/{google-play-rtdn,admob-ssv,create-ad-reward-token,redeem-activation-code}/`
    + `_shared/` — закоммитить как есть (G12).
  - `supabase/migrations/<новая>_workspace_payer_and_entitlement_gating.sql` (новый) —
    (а) `alter table public.workspaces add column payer_user_id uuid references auth.users(id)`,
    `billing_state text not null default 'active' check (billing_state in ('active','grace','expired'))`,
    `billing_state_until timestamptz`, backfill `payer_user_id = <владелец>` для существующих строк;
    (б) `create function private.effective_entitlement(p_user uuid) returns table(source text, starts_at
    timestamptz, expires_at timestamptz, in_trial boolean)` — победитель по максимальному
    `expires_at + grace`, приоритет `google_play` над `admob_reward` при равенстве (зеркало правила
    из SPEC 01);
    (в) `create function public.get_my_entitlement() returns json security definer` — закрывает
    пробел G14 (прямой `select` по `entitlements` запрещён `revoke all ... from authenticated`);
    (г) `create function private.workspace_write_allowed(p_workspace uuid) returns boolean` —
    `billing_state = 'active'`; `grace` → только чтение; `expired` → ничего;
    (д) `create function private.recompute_workspace_billing_state()` + расписание (pg_cron/
    Scheduled Function), пересчитывающее `billing_state`/`billing_state_until` из
    `private.effective_entitlement(payer_user_id)`.
  - Та же миграция — `create or replace` для гейтинга (G13, сейчас гейт только `is_active_member`):
    `public.push_operation` (`0002_shared_operations.sql:74`) и `public.resolve_conflict` (:254) —
    требуют `workspace_write_allowed`; `public.pull_operations` (:149) и
    `public.list_pending_conflicts` (:199) — разрешены при `active`/`grace`, запрещены при `expired`;
    `public.create_workspace` (`0001_shared_workspaces.sql:84`) — требует активного entitlement
    вызывающего и проставляет `payer_user_id = auth.uid()`;
    `public.create_invite` (:110) и `public.join_workspace` (:162) — требуют активного entitlement
    у плательщика воркспейса **любого источника**, включая `admob_reward` (D6: объём прав от
    источника не зависит; ветвление по `provider` в гейтинге запрещено).
  - `supabase/functions/google-play-rtdn/index.ts` — после записи/отзыва entitlement вызывать
    `private.recompute_workspace_billing_state()` для воркспейсов этого плательщика, чтобы
    `billing_state` не ждал следующего прогона планировщика. Маппинг состояний Play уже есть (G12):
    `SUBSCRIPTION_STATE_IN_GRACE_PERIOD` → `grace`, `REVOKED/EXPIRED/ON_HOLD/PAUSED` → `expired`.
  - `supabase/README.md` — runbook: выдача вечного Plus по whitelist
    (`insert into public.entitlements(user_id, provider) values (:user_id, 'whitelist')`), отзыв
    (`revoked_at = now()`), проверка `get_my_entitlement()`. *(assumption — UI администрирования не
    делается, O1)*
TEST_TYPES: unit (pgTAP или SQL-ассерты в миграционном тесте), instrumented (E2E двух аккаунтов —
  переиспользовать сценарий `review-2026-07-25-two-device-merge-e2e`)
CONSTRAINTS:
  - **Отказ по entitlement возвращается отдельным кодом `entitlement_required`** (`raise exception
    ... using errcode`), чтобы клиент отличал его от сетевой ошибки и от отказа по членству. Клиент
    мапит его в `SyncError` и в баннер (SPEC 05). *(assumption, O4)*
  - Гейт читается **у плательщика воркспейса**, а не у вызывающего: участники бесплатны (D5). Ни одна
    RPC не должна проверять entitlement `auth.uid()`, кроме `create_workspace`.
  - `billing_state` — денормализация ради стоимости чтения на горячем пути `push_operation`. Она
    обязана иметь ровно два писателя: RTDN-функция и планировщик. Никакого клиентского пути записи.
  - **Схема обязана допускать «перехват оплаты» участником в v2 без миграции** (требование
    заказчика): смена `payer_user_id` на другого активного участника — единственное, что для этого
    понадобится. Не заводить `owner_pays` как булев флаг и не выводить плательщика из `workspaces.owner_id`.
  - Все новые функции — `security definer` с `set search_path = ''` и явным `revoke all ... from
    public, anon` (паттерн уже применён в `20260812023710_finalize_shared_api_least_privilege.sql`).
  - **Миграции `20260812130000` и `20260812140000`, судя по всему, уже применены к проекту
    `shwzjlkhlpgbmzgnxhxi`** — так утверждает `support-rewarded-ads-01-server-reward-contract.md`
    (его факт G40) и на этом основании запрещает править их на месте. `support-hub-tip-05` при этом
    исходит из обратного и правит `20260812130000` in-place. **Перед началом работы проверить
    фактическое состояние** (`list_migrations` / `supabase migration list`) и действовать по нему;
    при сомнении — только новые файлы. Этот SPEC новых правок в применённые файлы не вносит: он их
    коммитит как есть и добавляет отдельную миграцию.
  - Пересечение по файлам: `support-hub-tip-05` добавляет в ту же схему `supporter_purchases`, а
    `support-rewarded-ads-01` — `ad_reward` заморозку. Три эпика пишут в `supabase/migrations/` —
    у каждого свой файл, порядок применения по имени; параллельно не редактировать.
=== END SPEC ===

## Acceptance

```gherkin
Feature: Серверный контроль доступа к shared-воркспейсу
  Сервер перестаёт обслуживать пользователя без действующего Plus, независимо от клиента.

  Scenario: Клиент читает своё право одним вызовом
    Given у пользователя есть активная годовая подписка
    When клиент запрашивает своё право у сервера
    Then сервер возвращает источник, дату начала, дату окончания и признак триала

  Scenario: Владелец с активной подпиской пишет операции
    Given владелец воркспейса имеет активное право
    When участник отправляет новую операцию
    Then операция принимается

  Scenario: Grace владельца переводит весь воркспейс в режим чтения
    Given подписка владельца истекла два дня назад и он находится в Grace
    When участник отправляет новую операцию
    Then сервер отказывает с кодом «требуется подписка»
    And тот же участник по-прежнему может вычитывать операции

  Scenario: После Grace сервер отказывает и в чтении
    Given срок Grace владельца истёк
    When участник пытается вычитать операции
    Then сервер отказывает с кодом «требуется подписка»

  Scenario: Истечение при закрытом приложении не остаётся незамеченным
    Given подписка владельца истекла, пока приложение было закрыто, и клиент ни разу не пересчитывал состояние
    When планировщик выполняет очередной пересчёт
    Then состояние оплаты воркспейса становится «истекло»
    And следующий запрос любого участника отклоняется

  Scenario: Награда за рекламу даёт те же серверные права, что подписка
    Given плательщик воркспейса получил Plus за просмотр рекламы и окно ещё не истекло
    When он выпускает приглашение
    Then сервер разрешает
    And после истечения 24 часов тот же запрос отклоняется без grace

  Scenario: Участник не платит
    Given у участника нет никакого права Plus, а у владельца подписка активна
    When участник отправляет операцию
    Then операция принимается
```

## Gap / context

Требование «если entitlement истёк, пока приложение было закрыто, сервер обязан сам перестать
обслуживать запросы» сейчас не закрыто: все shared-RPC гейтятся только членством
(`is_active_member`, G13), а прочитать своё право клиент вообще не может — прямой `select` по
`entitlements` закрыт `revoke all ... from authenticated` (G14). Плюс правило «платит владелец,
участники бесплатны» требует, чтобы гейт смотрел на **чужой** entitlement — для этого воркспейсу
нужен явный плательщик.

## Implementation links
- commit: c0fe820f, d84c8e55, d3cd11ac
- files: supabase/README.md; supabase/functions/_shared/admob.ts; supabase/functions/_shared/google-play.ts; supabase/functions/_shared/http.ts; supabase/functions/_shared/pubsub-oidc.ts; supabase/functions/_shared/reward-token.ts; supabase/functions/_shared/supabase.ts; supabase/functions/admob-ssv/index.ts; supabase/functions/create-ad-reward-token/index.ts; supabase/functions/google-play-rtdn/index.ts; supabase/functions/redeem-activation-code/index.ts; supabase/migrations/20260812130000_monetization_schema.sql; supabase/migrations/20260812140000_admob_reward_grant.sql; supabase/migrations/20260812150000_workspace_payer_and_entitlement_gating.sql; app/src/test/java/com/kshavrin/mymoney/SupabaseEntitlementGatingMigrationContractTest.kt; app/src/test/java/com/kshavrin/mymoney/SupabaseReadmeContractTest.kt
