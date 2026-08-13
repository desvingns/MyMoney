# Supabase: журнал покупок поддержки и восстановление бейджа
Epic: support-hub-tip
Order: 05 of 08
Status: done
Depends-on: 04
Date: 2026-08-12

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Факт покупки авторизованного пользователя доезжает до Supabase и возвращается оттуда после переустановки. **Новой** миграцией добавляется таблица-журнал `public.supporter_purchases` (строка на покупку), INSERT-политика, разрешающая писать только собственные строки, и триггер, апсертящий бейдж-строку `public.supporters` — тем же приёмом, что уже использует `grant_admob_plus_from_reward()`. На клиенте появляется data source, который пишет покупку после успеха и при входе в аккаунт читает состояние, отдавая его в `SupporterRepository.mergeRemote`. Неавторизованный пользователь покупает и получает бейдж ровно так же — просто ничего не отправляет и ничего не восстанавливает.
LAYERS: data, backend
CHANGED_HINT:
  - `supabase/migrations/20260813090000_supporter_purchases.sql` (**новый файл** — обе монетизационные миграции уже применены, G25) — `create table public.supporter_purchases (id uuid pk, user_id uuid not null references auth.users on delete cascade, product_id text not null, purchase_token text not null unique, purchased_at timestamptz not null, created_at timestamptz default now())` (G22, D4)
  - тот же файл — `enable row level security` + политики `supporter_purchases_select_own` и `supporter_purchases_insert_own` с `with check ((select auth.uid()) = user_id)`; `grant select, insert on public.supporter_purchases to authenticated`. Форму прав копировать с `20260812130000_monetization_schema.sql:99-123`, но **не редактировать сам этот файл** (G23, D3)
  - тот же файл — триггер `after insert on public.supporter_purchases`, апсертящий `public.supporters(user_id, provider='google_play', provider_reference=purchase_token)` с `on conflict (user_id) do nothing`; образец — `supabase/migrations/20260812140000_admob_reward_grant.sql:10-85` (G22, G24, D3)
  - core/network/src/main/java/com/kshavrin/mymoney/core/network/shared/SupabaseSupporterApi.kt — `post`/`get` через существующий `SupabaseHttpTransport(path, payload, accessToken)`; образец — `SupabaseSharedWorkspaceRpc.kt:24-36` (G21)
  - core/sync/src/main/java/com/kshavrin/mymoney/core/sync/supporter/SupporterSyncImpl.kt — читает сессию через `SupabaseSharedAuth.currentSession()` / `SharedSyncCoordinator.isSignedIn()` (G20), пишет покупку, при входе тянет состояние и зовёт `SupporterRepository.mergeRemote` (SPEC-04)
  - core/testing/src/main/kotlin/com/kshavrin/mymoney/core/testing/fake/FakeSupporterSync.kt — фейк для тестов SPEC-07 по образцу `FakeCurrencyRepository.kt:9-45` (T1)
TEST_TYPES: unit
CONSTRAINTS:
  - **Существующие миграции `20260812130000` и `20260812140000` править запрещено — они уже применены к `shwzjlkhlpgbmzgnxhxi`** (G25, проверено `list_migrations` 2026-08-12: `monetization_schema_20260812130000`, `admob_reward_grant_20260812140000`). Правка применённого файла разведёт репозиторий и БД, и ни один тест этого не покажет — они не ходят в сеть. Всё новое — только отдельным файлом миграции.
  - Папка `supabase/` целиком untracked в git (G25) — этот SPEC обязан её закоммитить: сами Edge Functions задеплоены и активны, но их исходники и обе миграции в репозитории отсутствуют.
  - Новую миграцию нужно **применить** и подтвердить через `list_migrations`; версию файла выбрать строго больше `20260812133214`. Если `plus-subscription-gating` или `support-rewarded-ads` уже добавили свои миграции — развести по времени, версия файла = порядок применения.
  - Запись **не верифицируется у Google и технически подделываема** (D3). Это осознанно принятая цена: бейдж косметический и никаких серверных ресурсов не открывает. Не «чинить» это добавлением Edge Function в рамках этого эпика.
  - Идемпотентность держит `purchase_token unique`: повторная отправка той же покупки не должна ни падать наверх ошибкой, ни удваивать счётчик. Конфликт по уникальному ключу — это успех, а не сбой.
  - Отказ сети при отправке **не влияет** на локальный бейдж: покупка уже совершена, пользователь уже поддержал. Ошибка отправки не показывается как ошибка покупки.
  - `SupporterRepository` делится со SPEC-04 — этот идёт строго после.
  - Экран About не трогать (вне скоупа эпика).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Серверная запись поддержки

  Scenario: Покупка авторизованного пользователя доезжает на сервер
    Given авторизованного пользователя
    When успешная покупка записана локально
    Then в журнал покупок отправляется строка с идентификатором товара и токеном
    And бейдж-строка пользователя появляется на сервере

  Scenario: Неавторизованный покупает без отправки
    Given пользователя без входа в аккаунт
    When успешная покупка записана локально
    Then бейдж и счётчик обновлены
    And на сервер ничего не отправляется

  Scenario: Восстановление после переустановки
    Given чистую установку и вход в аккаунт с тремя покупками на сервере
    When состояние поддержки синхронизируется
    Then бейдж взведён
    And счётчик равен 3

  Scenario: Повторная отправка той же покупки безопасна
    Given покупку, уже записанную на сервере
    When та же покупка отправляется ещё раз
    Then операция завершается успехом
    And счётчик на сервере не меняется

  Scenario: Отказ сети не отменяет поддержку
    Given авторизованного пользователя и недоступную сеть
    When успешная покупка записана локально
    Then бейдж и счётчик обновлены
    And пользователю не показывается ошибка покупки
```

## Gap / context
`public.supporters` уже есть в миграции, но её `user_id` — PRIMARY KEY, поэтому журнала покупок
туда не положить (G22), а RLS даёт `authenticated` только SELECT (G23) — писать клиенту сейчас
физически нечем. Журнал + INSERT-политика + триггер закрывают обе дыры одним связным изменением
схемы и дают честный `count(*)` для восстановления счётчика.

## Implementation links
- commits: 2d525f41, 7110ac42, 353cbb56, f728fe59, 891699c0, a14fbe88, 67b98cdf, 07a0bb5a
- migration: supabase/migrations/20260813090000_supporter_purchases.sql
- tests: app/src/test/java/com/kshavrin/mymoney/SupporterPurchasesMigrationContractTest.kt; core/billing/src/test/java/com/kshavrin/mymoney/core/billing/PlayBillingGatewayTest.kt; core/datastore/src/test/kotlin/com/kshavrin/mymoney/core/datastore/supporter/SupporterPurchaseStoreTest.kt; core/network/src/test/java/com/kshavrin/mymoney/core/network/shared/SupabaseSupporterApiTest.kt; core/sync/src/test/java/com/kshavrin/mymoney/core/sync/supporter/SupporterSyncImplTest.kt
- verification: 2069 passed / 0 failed / 0 skipped; detekt ok; lint ok
