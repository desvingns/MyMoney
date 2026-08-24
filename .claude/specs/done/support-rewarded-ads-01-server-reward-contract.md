# Серверный контракт награды: заморозка при активном Plus + RPC прогресса
Epic: support-rewarded-ads
Order: 01 of 06
Status: done
Depends-on: —
Date: 2026-08-12

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: Сервер начинает соблюдать два правила, которые сейчас нарушает. (1) Любой активный неотозванный Plus — платный, whitelist, activation-code или ad-Plus — замораживает прогресс: награда записывается (поддержка зафиксирована, дедуп по transaction_id сохранён), но помечается как не считающаяся и не участвует в выдаче; окно 24 ч никогда не продлевается. (2) Появляется единственный read-путь для клиента — RPC get_ad_reward_state(), возвращающая прогресс, порог, статус Plus и причину заморозки; правило «что считается» существует только на сервере.
LAYERS: data
CHANGED_HINT:
  - supabase/migrations/<новая версия>_ad_reward_freeze_and_state.sql — НОВЫЙ файл; править `20260812140000_admob_reward_grant.sql` на месте ЗАПРЕЩЕНО (G40: обе миграции уже применены на `shwzjlkhlpgbmzgnxhxi`)
  - в новой миграции: `alter table public.ad_rewards add column counts_toward_reward boolean not null default true`, `add column exclusion_reason text` (G11 — форма таблицы)
  - в новой миграции: `create or replace function private.grant_admob_plus_from_reward()` — предикат активного Plus расширяется на ЛЮБОЙ provider (сейчас только `provider = 'admob_reward'`, G13: `20260812140000_admob_reward_grant.sql:24-34`); вместо раннего `return new` строка обновляется в `counts_toward_reward = false` + `exclusion_reason`; подсчёт до порога учитывает только `counts_toward_reward = true` (G12: `…140000.sql:36-43`)
  - в новой миграции: `advisory_xact_lock` по `user_id` и привязка 5 старейших наград сохраняются как есть (G12: `…140000.sql:20-22,63-74`)
  - в новой миграции: `create or replace function public.get_ad_reward_state()` — `security definer`, `set search_path = ''`, читает `auth.uid()`, `revoke ... from public, anon` + `grant execute ... to authenticated` (зеркалит `redeem_activation_code`, G18: `20260812130000_monetization_schema.sql:126-198`)
  - supabase/tests/ad_reward_grant.test.sql — НОВЫЙ pgTAP-набор (каталога `supabase/tests/` в проекте ещё нет) (assumption: pgTAP как харнесс подтверждён на гейте разбивки)
  - supabase/README.md — раздел про новую RPC и правило заморозки (assumption: место документации)
TEST_TYPES: sql
CONSTRAINTS:
  - Миграция обязана быть **аддитивной и идемпотентной по смыслу**: она применяется поверх уже
    задеплоенной схемы (G40), существующие строки `ad_rewards` получают `counts_toward_reward = true`
    по default — это верно, они были заработаны до появления правила.
  - Клиент НИКОГДА не выдаёт entitlement; RPC — только чтение, никаких побочных эффектов (ADR-0010 D5, G22).
  - Окно не стекается: грант всегда ставит `now() + interval '24 hours'`, никогда не добавляет к
    существующему (ADR-0010, G27).
  - `public.entitlements` параллельно правит эпик `plus-subscription-gating` — миграции обязаны быть
    разведены по версии-времени, одновременная работа над обеими запрещена.
  - RPC не должна раскрывать чужие строки: `auth.uid()` внутри, никаких параметров-идентификаторов.
  - Деплой миграции и функций делает человек (см. чеклист в overview) — SPEC считается закрытым по
    коду и тестам, а не по факту применения на проде.

### Calculation (domain_math)

**Формула**

```
plus_active(u, t)      = ∃ e ∈ entitlements :
                           e.user_id = u ∧ e.entitlement = 'plus' ∧ e.revoked_at IS NULL
                           ∧ e.starts_at ≤ t ∧ (e.expires_at IS NULL ∨ e.expires_at > t)
                         -- ЛЮБОЙ provider, без исключений

counts_toward_reward(r) = ¬ plus_active(r.user_id, r.verified_at)   -- вычисляется один раз, при вставке

progress(u)            = |{ r ∈ ad_rewards : r.user_id = u
                                           ∧ r.entitlement_id IS NULL
                                           ∧ r.counts_toward_reward = true }|

grant(u) выполняется ⟺ counts_toward_reward(new) ∧ progress(u) ≥ REQUIRED
  → entitlements += { provider: 'admob_reward', starts_at: now(), expires_at: now() + 24h }
  → 5 старейших считающихся непривязанных наград (order by rewarded_at, created_at, id) получают entitlement_id
```

**Таблица символов**

| Символ | Смысл | Тип / единица |
|---|---|---|
| `u` | пользователь (`auth.users.id`) | uuid |
| `t` | момент оценки | timestamptz |
| `REQUIRED` | порог выдачи | 5 (целое, константа) |
| `WINDOW` | длительность окна Plus | `interval '24 hours'` |
| `progress(u)` | «просмотрено N из 5» | целое ≥ 0 |
| `exclusion_reason` | почему не засчитано | text, напр. `plus_active:google_play` |

**Округление и границы**

- Счёт целочисленный, округления нет.
- Время — `timestamptz`, окно задаётся `interval '24 hours'` (ровно 24 часа, не «календарные сутки»),
  поэтому переходы на летнее время и часовые пояса клиента на длину окна не влияют.
- Граница активности строгая: `expires_at > t`. В момент `t = expires_at` Plus уже НЕ активен, и
  просмотр снова считается.
- `progress` не сбрасывается по времени: заработанные считающиеся награды живут до привязки к гранту.

**Worked examples**

1. **Обычная выдача.** Free-пользователь, 4 считающиеся непривязанные награды. Приходит пятый SSV →
   `counts_toward_reward = true`, `progress = 5 ≥ 5` → создаётся entitlement `admob_reward`,
   `expires_at = now() + 24 h`; 5 наград привязываются; `progress` становится 0.
2. **Платный Plus активен.** Активна подписка `google_play` до `+20 дней`. Пользователь смотрит ролик →
   строка вставляется с `counts_toward_reward = false`, `exclusion_reason = 'plus_active:google_play'`;
   `progress` остаётся 0; новый entitlement не создаётся; срок подписки не меняется.
3. **Ad-Plus активен (антистекование).** Ad-Plus выдан 3 ч назад, истекает через 21 ч. Просмотр →
   `counts_toward_reward = false`, `exclusion_reason = 'plus_active:admob_reward'`; окно по-прежнему
   истекает в исходное время, а не через 24 ч от сейчас.
4. **Накопленное переживает платный период.** Было 4 считающиеся награды → пользователь купил Plus →
   3 просмотра во время подписки не засчитаны → подписка истекла → следующий просмотр засчитан,
   `progress = 5` → выдача. Ранее заработанные 4 не сгорели, «замороженные» 3 не воскресли.
5. **Whitelist-тестер.** `provider = 'whitelist'`, `expires_at IS NULL` → `plus_active` истинно всегда →
   каждый просмотр пишется с `counts_toward_reward = false`; прогресс вечно 0 — и UI обязан объяснить
   это, а не показывать «0 из 5» без причины.
6. **Дубль SSV.** Повторный колбэк с тем же `transaction_id` → нарушение UNIQUE (SQLSTATE 23505) →
   новой строки нет, `progress` не меняется, эндпоинт отвечает `{ok:true, duplicate:true}` (G14).
7. **Гонка двух колбэков.** Два SSV одновременно на 4-й и 5-й награде — `pg_advisory_xact_lock` по
   `user_id` (G12) сериализует их: ровно один грант, ровно 5 привязанных наград.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Серверный контракт награды за рекламу

  Scenario: Пятая награда выдаёт 24 часа Plus
    Given у пользователя 4 засчитанные непривязанные награды и Plus не активен
    When AdMob SSV подтверждает пятую награду
    Then создаётся entitlement plus с provider admob_reward и сроком ровно 24 часа от текущего момента
    And пять наград привязаны к этому entitlement
    And get_ad_reward_state возвращает progress 0 и plus_active true

  Scenario: Активный платный Plus замораживает счётчик
    Given у пользователя активна подписка plus с provider google_play
    When AdMob SSV подтверждает награду
    Then награда записана с counts_toward_reward false и указанной причиной
    And progress не изменился
    And новый entitlement не создан

  Scenario: Окно не стекается
    Given у пользователя активен ad-Plus, истекающий через 21 час
    When AdMob SSV подтверждает ещё одну награду
    Then срок истечения ad-Plus остался прежним

  Scenario: Заработанное до подписки не сгорает
    Given у пользователя 4 засчитанные награды, затем активная подписка, затем она истекла
    When AdMob SSV подтверждает следующую награду
    Then progress достигает 5 и выдаётся новый ad-Plus

  Scenario: Повторный колбэк ничего не меняет
    Given награда с данным transaction_id уже записана
    When приходит колбэк с тем же transaction_id
    Then новая строка не создаётся и progress не меняется

  Scenario: Чужое состояние недоступно
    Given два разных авторизованных пользователя
    When каждый вызывает get_ad_reward_state
    Then каждый видит только свой прогресс и свой статус Plus
```

## Gap / context
Серверная ad-часть уже написана и **уже применена** на удалённом проекте (G40), но нарушает два
собственных требования: заморозка проверяется только для `provider = 'admob_reward'` (G13), из-за чего
при платном/whitelist Plus счётчик растёт и выдаётся стекающийся ad-Plus вопреки ADR-0010 (G27); и
read-пути прогресса не существует вовсе (G18), поэтому клиент физически не может показать «3 из 5».

## Implementation links
- commit: 23421742, 18d3b6c0, d6511e44, 4ce164ac, 94048a93, c315f7c4
- files: supabase/migrations/20260812160000_ad_reward_freeze_and_state.sql; supabase/migrations/20260813000000_harden_ad_reward_state_access.sql; supabase/README.md; supabase/tests/ad_reward_grant.test.sql; app/src/test/java/com/kshavrin/mymoney/SupabaseReadmeContractTest.kt
