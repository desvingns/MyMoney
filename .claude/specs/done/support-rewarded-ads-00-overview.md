# Эпик: support-rewarded-ads — награждаемая реклама AdMob → 24 ч Plus
Epic: support-rewarded-ads
Order: 00 of 06 (overview)
Status: done
Depends-on: plus-subscription-gating (весь эпик; см. «Почему такой порядок»)
Date: 2026-08-12
Completed: 2026-08-17 — все 6 SPEC-ов в done/ с commit+files; UI-блок SPEC 05 закрыл последнюю
зависимость. Ручные пункты (AdMob-консоль, Supabase-секреты, Play Console флаг, app-ads.txt hosting)
остаются в «Чеклисте для человека» ниже — не блокируют закрытие эпика, это внешние DevOps-шаги.

## Цель

Блок «Посмотреть рекламу» в разделе «Поддержать проект»: 5 досмотренных rewarded-роликов дают
24 часа Plus. Награда выдаётся **только** через AdMob Server-Side Verification — клиент не выдаёт
entitlement ни при каких условиях (ADR-0010 D5). Своей модели доступа эпик не вводит: переиспользует
`UserEntitlement` из `plus-subscription-gating`. Ad-Plus даёт полный доступ, включая shared-sync, и
по истечении окна ведёт себя ровно как истёкший платный Plus.

**В скоуп входит:** модуль `:core:ads` с `AdGateway` (AdMob `play-services-ads`, без медиации),
UMP-согласие, ad-часть серверного контракта (правило заморозки + RPC прогресса), UI-блок со всеми
состояниями, приватность и `app-ads.txt`.
**Не входит:** биллинг, подписка, покупка «кофе», баннеры/интерстишелы/app-open где бы то ни было,
медиация, месячный кап на просмотры, RuStore.

## Заблокированные решения (из grill)

- **D1:** эпик жёстко зависит от `plus-subscription-gating`: тот доставляет `:feature:support`
  (модуля сейчас НЕТ — G1), `UserEntitlement` + репозиторий entitlement в `:core:domain`
  (в Kotlin сейчас НЕТ — G3) и правила перехода в LocalOnly.
- **D2:** ad-часть сервера — в скоупе (правило гранта, read-путь прогресса, деплой, тесты). Общая
  схема `entitlements` / RLS / RTDN / activation codes остаётся за `plus-subscription-gating`.
- **D3:** «реклама недоступна в регионе» определяется **только по факту** — серия подряд идущих
  `NO_FILL`. Ни списка стран, ни определения страны по SIM/локали, ни запроса к серверу.
- **D4:** подтверждение награды — ограниченный поллинг с backoff (~30 с) после закрытия ролика.
  Не подтвердилось — честный текст «засчитается, когда сервер подтвердит» + pull-to-refresh.
  UI не имеет права сказать «начислено» до подтверждения сервером.
- **D5:** вердикт «реклама здесь не работает» живёт только в памяти сессии. Следствие принято
  осознанно: одна попытка загрузки на холодный старт. Бесконечный спиннер запрещён — у загрузки
  есть таймаут, попытка стартует лениво по открытию блока.
- **D6:** заморозка при активном Plus (любой provider) фиксируется новой колонкой
  `ad_rewards.counts_toward_reward`. Строка награды пишется всегда (поддержка зафиксирована, дедуп
  по `transaction_id` сохранён), но не идёт в прогресс и не продлевает окно.
- **D7:** клиент читает состояние через одну RPC `public.get_ad_reward_state()`. Правило заморозки
  живёт только на сервере; клиент ничего не пересчитывает.
- **D8:** работы, недоступные агенту, собраны в секции «Чеклист для человека» ниже, а не отдельным
  SPEC-ом.
- **(assumption) O1:** диалог «оплатите подписку» при истечении 24 ч и немедленная отцепка от
  Supabase — правила `plus-subscription-gating`; этот эпик своего пути истечения не добавляет.
- **(assumption) O2:** аналитики рекламной воронки нет; в Sentry уходят только ошибки.
- **(assumption) O3:** `com.google.android.gms.ads.APPLICATION_ID` — `meta-data` в манифесте через
  `manifestPlaceholders` (BuildConfig-полем быть не может); тестовые ad unit id — через BuildConfig.

## SPEC'и (собираются через `/mp --feature --next` в порядке)

| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `support-rewarded-ads-01-server-reward-contract.md` | — | data (SQL) | заморозка при активном Plus + `counts_toward_reward` + RPC `get_ad_reward_state()` |
| 02 | `support-rewarded-ads-02-core-ads-module.md` | — | build+data | каркас `:core:ads`, AdMob/UMP зависимости, AD_ID, BuildConfig-флаги |
| 03 | `support-rewarded-ads-03-ad-gateway-admob.md` | 02 | data | `AdGateway` + AdMob-реализация, ленивый UMP, маппинг ошибок, счётчик NO_FILL |
| 04 | `support-rewarded-ads-04-ad-reward-state.md` | 01, 02 | domain+data | `AdRewardRepository`/`AdRewardState`, чтение RPC, поллинг подтверждения |
| 05 | `support-rewarded-ads-05-support-ads-block-ui.md` | 03, 04, **plus-subscription-gating** | presentation | блок со всеми состояниями + строки EN/RU |
| 06 | `support-rewarded-ads-06-ads-privacy-and-app-ads-txt.md` | 02 | docs+release | блок Advertising в обе политики, дата, `app-ads.txt`, проверка Pages |

## Почему такой порядок

01 и 02 — единственные SPEC-и, которые можно взять **до** `plus-subscription-gating`: сервер и
gradle-каркас не зависят от экрана. 03 идёт после 02 — оба правят `core/ads/build.gradle.kts`,
параллельно нельзя. 04 ждёт 01 (контракт RPC) и 02 (модуль, куда ложится реализация). 05 —
последний: файлы `:feature:support` создаёт соседний эпик, до этого SPEC физически некуда писать.
06 не должен мержиться раньше 02 и обязан войти **в тот же релиз**, что и SDK (ADR-0010: политика
не публикуется раньше интеграции, но и не позже — G28).

Отдельный риск сериализации: 01 трогает `public.entitlements`, которую параллельно может править
`plus-subscription-gating`. Миграции надо развести по времени (версия файла = порядок применения).

## Ключевые факты (verified, из grounding)

- **G1/G2/G3:** нет ни `:feature:support`, ни `:core:ads`, ни `:core:billing` — `settings.gradle.kts:34-52`;
  нет `UserEntitlement` в Kotlin (поиск по `app/`, `core/`, `feature/` пуст); ни admob-, ни
  billing-зависимостей в `gradle/libs.versions.toml:60-154`.
- **G10/G11:** `public.entitlements` (`provider ∈ google_play|admob_reward|whitelist|activation_code`,
  `expires_at`, `revoked_at`, RLS select-own) — `supabase/migrations/20260812130000_monetization_schema.sql:3-30,104,122`;
  `public.ad_rewards` (`transaction_id` UNIQUE, `entitlement_id` nullable FK) — там же `:46-62,112,124`.
- **G12:** триггер `ad_rewards_grant_plus` → `private.grant_admob_plus_from_reward()`, advisory-lock
  по `user_id`, при ≥5 непривязанных наград создаёт entitlement `now() + interval '24 hours'` —
  `supabase/migrations/20260812140000_admob_reward_grant.sql:10-86`.
- **G13 (баг):** early-return «Plus уже активен» смотрит только `provider = 'admob_reward'` —
  `20260812140000_admob_reward_grant.sql:24-34`. При платном / whitelist / activation-code Plus
  счётчик растёт и выдаётся дополнительный стекающийся ad-Plus. Противоречит требованию и ADR-0010
  «Rewarded window does not stack». Чинит SPEC 01.
- **G14/G15:** Edge Function `admob-ssv` проверяет подпись AdMob до записи, дубль `transaction_id`
  (SQLSTATE 23505) → `{ok:true, duplicate:true}` — `supabase/functions/admob-ssv/index.ts:12-95`;
  `verifyAdMobSignature(...)` ECDSA P-256/SHA-256 с кэшем ключей 23 ч — `supabase/functions/_shared/admob.ts:89-103`.
- **G16/G17:** `create-ad-reward-token` (POST, требует `authenticatedUser`) → `{custom_data, expires_at}` —
  `supabase/functions/create-ad-reward-token/index.ts:5-18`; токен `v1.{userId}.{expiresAt}.{nonce}.{hmac}`,
  TTL 600 с по умолчанию — `supabase/functions/_shared/reward-token.ts:47-75`.
- **G18:** read-пути прогресса НЕТ — ни RPC, ни view; единственная RPC схемы — `redeem_activation_code(text)`
  (`20260812130000_monetization_schema.sql:126-198`). Заводит SPEC 01.
- **G19:** сессия/токен — `SupabaseSharedAuth.accessToken()` — `core/network/src/main/java/com/kshavrin/mymoney/core/network/shared/SupabaseSharedAuth.kt:18-48`;
  транспорт — OkHttp, не Retrofit — `core/network/.../shared/SupabaseHttpTransport.kt:20-61`.
- **G20:** отцепка в LocalOnly — `SharedSyncCoordinatorImpl.disconnectFromDeviceLocked()` —
  `core/sync/src/main/java/com/kshavrin/mymoney/core/sync/shared/SharedSyncCoordinatorImpl.kt:489-500`.
- **G25/G27:** ADR-0010 D7 (состав `:core:ads` / `:feature:support`, entitlement через интерфейс в
  `:core:domain`) — `docs/DECISIONS/ADR-0010-monetization.md:131-146`; «Rewarded window does not stack» — `:214-217`.
- **G26:** РФ — AdMob не отдаёт рекламу, временный Plus заработать нельзя; поверхность обязана
  деградировать честно, а не мёртвой кнопкой или тостом-ошибкой — `ADR-0010:148-176`.
- **G29:** Play Services Ads мержит `com.google.android.gms.permission.AD_ID`. Таблица permissions
  TDD §8.2 **уже приведена в соответствие**: `AD_ID` стоит `KEEP` с пометкой ADR-0010
  (`MyMoney_TDD.md:2035`), счётчик §8.2 — «Final count: **6**» (`:2040`). Этому эпику TDD править
  **не нужно**; остаточное расхождение на строке `:2113` закрывает `support-hub-tip-01`, а строку
  `POST_NOTIFICATIONS` — `plus-subscription-gating-07`. Первоисточник — `ADR-0010:196-200`.
- **G31/G32/G33:** черновик блока Advertising, статус «Draft, not published» —
  `docs/legal/privacy-policy-monetization-draft.md:3,176-211`; «Last updated» в обеих политиках —
  `app/src/main/assets/privacy_policy_{en,ru}.html:21`; публикация `privacy-policy/` на gh-pages —
  `.github/workflows/privacy-policy-pages.yml:1-51`.
- **G34/G35/G36/G37:** `ConnectedModulesCiContractTest` с захардкоженным списком модулей —
  `app/src/test/java/com/kshavrin/mymoney/ConnectedModulesCiContractTest.kt:15-31`; per-module пороги
  Kover — `build.gradle.kts:22-33`; detekt `maxIssues: 0` — `config/detekt/detekt.yml:2`; `mp-runner`
  даёт ложные «ok» по detekt и не гоняет ktlint/kover — `.claude/mp/extras/mp-runner-android.md:29-46`.
- **G38/G39:** ViewModel-тесты с `toRoute<…>()` — только под Robolectric `@Config(sdk=[34])`
  (`feature/dictionaries/src/test/kotlin/.../AccountEditViewModelTest.kt:26-36`); только Fakes на границе
  репозитория (`core/testing/src/main/kotlin/.../fake/FakeCurrencyRepository.kt:9-45`).
- **G40:** обе монетизационные миграции **уже применены** на удалённом проекте `shwzjlkhlpgbmzgnxhxi`
  (`monetization_schema_20260812130000`, `admob_reward_grant_20260812140000`, проверено
  `list_migrations` 2026-08-12). Править `20260812140000` на месте нельзя — только новая миграция.
- **G41:** шаблон для `:core:ads` — `core/network/build.gradle.kts`: `alias(libs.plugins.mymoney.android.library)`,
  `buildConfigField` из gradle-свойств с fallback на `local.properties`, `buildFeatures { buildConfig = true }`.
  Id конвенционных плагинов — `build-logic/build.gradle.kts:21-37`.
- **G42:** в карте `koverLineFloors` (`build.gradle.kts:22-33`) НЕТ `:core:network`, `:core:ui`,
  `:core:sync`, `:core:common` — новый `:core:*` модуль не обязан получать порог автоматически;
  решение принимается при реализации, а не по умолчанию.

## Чеклист для человека (НЕ для агента)

Ни один пункт агент выполнить не может — нужен доступ к консолям и секретам. Помечено, что именно
блокирует.

**AdMob / SSV** — блокирует SPEC 03 и приёмку 05:
- [ ] Создать приложение в AdMob-консоли, получить `APPLICATION_ID`; положить в `gradle.properties`
      / `local.properties` под ключом из SPEC 02.
- [ ] Создать rewarded ad unit, получить боевой ad unit id (тестовые id в debug уже в SPEC 02).
- [ ] Вбить SSV callback URL, указывающий на Edge Function `admob-ssv`.

**Supabase** — блокирует SPEC 01 и 04:
- [ ] Задеплоить `admob-ssv` **с выключенной проверкой JWT** (AdMob вызывает анонимно) и
      `create-ad-reward-token` — с включённой.
- [ ] Положить секреты функций (HMAC-ключ reward-token, TTL) в секреты проекта.
- [ ] Применить миграцию из SPEC 01 и проверить `list_migrations`.

**Google Play** — блокирует релиз:
- [ ] Поставить флаг «Содержит рекламу» в листинге.
- [ ] Обновить Data safety: сбор Advertising ID (цель — реклама), список третьих сторон.

**GitHub Pages** — идёт со SPEC 06:
- [ ] Взять строку издателя (`pub-…`) из AdMob-консоли для `app-ads.txt`.
- [ ] Разместить `app-ads.txt` в **корне домена**, указанного сайтом разработчика в Play. Текущий
      workflow публикует `privacy-policy/{en,ru}/index.html` в подпапку проектного Pages-сайта, поэтому
      корневого размещения он сам по себе не даёт — скорее всего файл придётся положить в корневой
      Pages-репозиторий (`desvingns.github.io`). Проверить фактическую отдачу по URL до релиза.
- [ ] Дождаться статуса проверки `app-ads.txt` в AdMob-консоли (обход занимает до суток).
- [ ] Сверить, что опубликованная версия политики совпадает с той, что в `app/src/main/assets/`.

## Implementation links
- commit: —
- files: —
