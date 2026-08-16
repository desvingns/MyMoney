# Эпик: support-hub-tip — раздел «Поддержать проект» и разовая покупка кофе
Epic: support-hub-tip
Order: 00 of 08 (overview)
Status: done
Depends-on: —
Date: 2026-08-12

## Цель

ADR-0010 отменил Q-B3, и у приложения впервые появляется монетизация. Этот эпик закрывает её
самую маленькую и самостоятельную часть: девятый пункт правого drawer «Поддержать проект» /
«Support the app» с иконкой сердца открывает новый экран `:feature:support`, где пользователь
может один или много раз купить «кофе» (`coffee_small` €1, `coffee_large` €5 — consumable из
ADR-0010 D4). После первой покупки навсегда появляется косметический бейдж Supporter и счётчик
«поддержал N раз». Экран сразу собирается в целевом порядке блоков — вводный текст → слот
рекламы → слот Plus → блок кофе → бейдж и благодарность, — но слоты рекламы и Plus в этом
релизе пусты и наполняются эпиками `support-rewarded-ads` и `plus-subscription-gating`.

Вне скоупа: реклама любого вида, подписка, paywall, гейтинг синхронизации, экран About,
верификация покупок через Google Play Developer API и RTDN.

## Заблокированные решения (из grill)

- **D1:** Supporter badge — косметический маркер за **первую покупку кофе**, не entitlement.
  Строка «Supporter badge» в таблице D2 ADR-0010 (`docs/DECISIONS/ADR-0010-monetization.md:64`)
  относила его к Plus — правится в SPEC-01. Драйвер: `public.supporters` в миграции уже отделена
  от `entitlements`, её provider-check `('google_play','activation_code','manual')` не содержит
  подписочных провайдеров.
- **D2:** «Вечность» бейджа держат **DataStore + Supabase**, а не `queryPurchases`.
  Консьюмнутый consumable Play в `queryPurchases` не возвращает, а ADR-0010 D4 требует консьюмить
  сразу ради повторных покупок. `queryPurchases` используется строго для дозакрытия незавершённых
  и `PENDING` покупок. **Принятое следствие: анонимный пользователь после переустановки теряет
  бейдж и счётчик** — это заявленное поведение, а не дефект.
- **D3:** Запись на сервер — **клиентский INSERT под своим JWT + триггер**, без Edge Function.
  Текущая RLS даёт `authenticated` только SELECT, поэтому добавляется INSERT-политика
  `with check ((select auth.uid()) = user_id)` и триггер, апсертящий бейдж-строку `supporters` —
  тот же паттерн, что `grant_admob_plus_from_reward()`. Запись **не верифицируется у Google и
  технически подделываема**; принято осознанно, поскольку бейдж косметический и серверных
  ресурсов не открывает.
- **D4:** Счётчик — **новая таблица-журнал** `public.supporter_purchases` (строка на покупку,
  `purchase_token` unique). `supporters.user_id` — PRIMARY KEY, журнала туда не положить.
- **D5:** Блоки рекламы и Plus — **пустые composable-слоты** с дефолтом `{}`; в релизе
  пользователь не видит ни карточек «скоро», ни неактивных кнопок.
- **D6:** При недоступном биллинге заменяется **только блок кофе**; вводный текст, бейдж и
  благодарность остаются. Тон спокойный, без слова «ошибка» и без кнопки «Повторить»
  (ADR-0010:143-144).
- **D7 (assumption):** debug-выключатель повторяет форму sync-флагов: Gradle-property
  `billing.enabled` → `BuildConfig.BILLING_ENABLED`, default `false`, release-CI передаёт
  `-Pbilling.enabled=true`. Санкционировано ADR-0010 D6.
- **D8 (assumption):** аналитика заводится через собственный `AnalyticsGateway` в `:core:domain`
  + Firebase-реализация в data-слое, зеркально границе `BillingGateway`; при `HAS_FIREBASE=false`
  — no-op.
- **D9:** политика конфиденциальности — Block 1 + Block 2 **вариант B**; Remote Config **не**
  описывается как управляющий Shared workspace (`sharedSyncEnabled()` его не читает).
- **O1 (assumption):** бейдж виден только владельцу в своём приложении, другим участникам общего
  workspace — нет. Это же закрывает открытый вопрос ADR-0010 (строки 226-227).
- **O2 (assumption):** цены в UI берутся из `ProductDetails` (форматирование Play по локали
  пользователя); «€1/€5» — конфигурация Play Console, а не строковый ресурс.

## SPEC'и (собираются через `/mp --feature --next` в порядке)

| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `support-hub-tip-01-adr-badge-amendment.md` | — | docs | ADR-0010: бейдж за кофе, косметический; счётчик разрешений TDD:2113 → 6 |
| 02 | `support-hub-tip-02-billing-domain-contract.md` | 01 | domain | `BillingGateway` + модели + `FakeBillingGateway` |
| 03 | `support-hub-tip-03-core-billing-play.md` | 02 | data, build | `:core:billing` на Google Play Billing + `BILLING_ENABLED` |
| 04 | `support-hub-tip-04-supporter-state.md` | 02 | domain, data | Бейдж и счётчик в `AppSettings` + `SupporterRepository` |
| 05 | `support-hub-tip-05-supporter-supabase.md` | 04 | data, backend | Журнал `supporter_purchases`, RLS, триггер, синхронизация |
| 06 | `support-hub-tip-06-analytics-gateway-firebase.md` | — | domain, data, build | `AnalyticsGateway` + Firebase Analytics |
| 07 | `support-hub-tip-07-feature-support-screen.md` | 03, 04, 06 | presentation | `:feature:support`, маршрут, 9-й пункт drawer |
| 08 | `support-hub-tip-08-privacy-policy-purchases-firebase.md` | 03, 06 | docs, legal | Purchases + Firebase-блоки политики, обе локали |

## Почему такой порядок

01 идёт первым, потому что правка ADR авторизует саму модель бейджа, которую реализуют 02 и 04:
писать код против решения, которое в ADR записано иначе, — это заведомая рассинхронизация.
Дальше фундамент до UI: контракт (02) → реализация (03) → состояние (04) → сервер (05).

Клэши, вынуждающие последовательность: **03, 06 и 07** все правят `gradle/libs.versions.toml`
и/или `app/build.gradle.kts` — параллелить нельзя. **02 и 04** оба добавляют файлы в
`:core:domain`. **04 и 05** делят `SupporterRepository`.

08 стоит последним намеренно. Черновик политики (`docs/legal/privacy-policy-monetization-draft.md:15-20`)
задаёт правило: блок применяется в том релизе, который везёт описанное поведение, и **никогда
раньше**. Объявить сбор данных, которого ещё нет, — та же ложь в опубликованном документе, что и
умолчать о реальном сборе. Оба HTML публикуются на GitHub Pages, то есть правка — публичное
юридическое заявление, а не изменение строки в приложении.

## Предусловия вне репозитория (не покрываются SPEC-ами и не проверяются тестами)

- **Play Console:** создать `coffee_small` (€1) и `coffee_large` (€5) как consumable-товары.
  Без этого покупка не пройдёт даже на внутреннем треке (ADR-0010:191-193).
- **CI-секрет `GOOGLE_SERVICES_JSON`** (OQ-9): без него `firebase.enabled` не выставляется,
  `HAS_FIREBASE=false` и аналитика молча не работает — при этом сборка остаётся зелёной.
- **Форма Play Data Safety:** обновляется в том же релизе; правкой HTML-политики она не
  покрывается (ADR-0010:194-195, черновик политики:217-219).

## Ключевые факты (verified, из grounding)

- **G1/G2:** `RightDrawerContent.kt:40-102` — ровно 8 вызовов `RightDrawerItem(label, icon,
  onClick, testTag)`; иконка 44dp в `Box` 56dp, `mergeDescendants = true` (`:105-139`) — целевые
  48dp a11y перевыполнены самой конструкцией. Test-tag константы `:141-148`.
- **G4/G5/G6:** `DashboardEvent` (`DashboardState.kt:260-297`) → `DashboardViewModel.onEvent()`
  (`:1108-1132`, `closeDrawers()` + `emit`) → `DashboardAction` (`DashboardAction.kt:31-55`) →
  `when` в `MyMoneyNavHost.kt:59-104`.
- **G7:** маршруты — `@Serializable data object` внутри `object Destinations`
  (`core/ui/.../navigation/Destinations.kt:6-145`).
- **G10:** `:core:billing`, `:core:ads`, `:feature:support` описаны в AGENTS.md и ADR-0010 D7,
  но **на диске отсутствуют**; `settings.gradle.kts:34-52` заканчивается на `:feature:lockscreen`.
- **G15:** `billing-ktx` в `gradle/libs.versions.toml` отсутствует; `firebase-bom` есть (`:39`),
  но объявлен только `firebase-config-ktx` (`:135`) — `firebase-analytics` в сборке нет.
- **G16/G18:** `BuildConfig.HAS_FIREBASE` приходит из Gradle-property `firebase.enabled`
  (`app/build.gradle.kts:67-91`, `core/sync/build.gradle.kts:18-27`); CI материализует
  `app/google-services.json` из секрета и передаёт `-Pfirebase.enabled=true`
  (`.github/workflows/ci.yml:61-72, 201-212`); `google-services.json` в `.gitignore:29`.
  `sharedSyncEnabled()` (`RemoteConfigRepositoryImpl.kt:55-59`) читает **только** BuildConfig-флаги.
- **G19:** аналитической абстракции в проекте нет вообще — из телеметрии только Sentry.
- **G20/G21:** `SupabaseSharedAuth.currentSession(): SharedSession?`
  (`core/network/.../SupabaseSharedAuth.kt:35-38`), `SharedSyncCoordinator.isSignedIn()`
  (`core/sync/.../SharedSyncCoordinator.kt:32-34`); авторизованные запросы —
  `SupabaseHttpTransport.post/get(path, payload, accessToken)`, образец
  `core/network/.../SupabaseSharedWorkspaceRpc.kt:24-36`.
- **G22/G23:** `public.supporters(user_id uuid PRIMARY KEY, provider, provider_reference,
  granted_at, revoked_at, metadata jsonb)` — `supabase/migrations/20260812130000_monetization_schema.sql:32-44`;
  RLS даёт `authenticated` только SELECT (`:99, 108-110, 117, 123`).
- **G25:** вся папка `supabase/` (config.toml, functions, обе миграции монетизации) **untracked
  в git** — исходники не закоммичены. Но на сервере всё живо: `list_migrations` для
  `shwzjlkhlpgbmzgnxhxi` (проверено 2026-08-12) показывает `monetization_schema_20260812130000`
  и `admob_reward_grant_20260812140000` как **применённые**, а Edge Functions задеплоены и активны
  (`.ai/memory/MEMORY.md`, раздел «Monetization backend setup»). Следствие: коммитить `supabase/`
  нужно, **переприменять или править существующие миграции — нельзя**, только новые файлы.
- **G26:** новое поле `AppSettings` = 3 правки: `model/AppSettings.kt:3-38`,
  `AppSettingsKeys.kt:8-41`, `AppSettingsRepositoryImpl.kt:60-100`.
- **G28-G31:** политика — `app/src/main/assets/privacy_policy_{en,ru}.html`, показывается через
  `AssetWebViewScreen` (`feature/settings/.../about/PrivacyPolicyScreen.kt:8-14`), публикуется на
  GitHub Pages; `Last updated` — строка 21; EN строка 55 — «Firebase Remote Config is not enabled
  in this release»; готовые блоки — `docs/legal/privacy-policy-monetization-draft.md:31-59` и
  `:91-137`.
- **T4:** ViewModel, читающий `savedStateHandle.toRoute<Destinations.*>()`, требует
  `@RunWith(RobolectricTestRunner::class)` + `@Config(sdk=[34])`.
- **T6:** dashboard-скриншот Roborazzi снимается с закрытыми drawer'ами
  (`DashboardScreenshotTest.kt:37`) — 9-й пункт его не ломает.
- **T7/T8:** `ConnectedModulesCiContractTest.kt:22-28` и `DestinationsTest.kt:98-133` содержат
  захардкоженные списки (модули с connected-тестами; 28 destination'ов) — новый модуль и новый
  маршрут ломают их, пока списки не обновлены.
- **CI2:** `mp-runner-android.sh` не запускает `ktlintCheck`/`koverVerify` и может отрапортовать
  `detekt:ok` при реально упавшей сборке (`.claude/mp/extras/mp-runner-android.md:29-32`).

## Implementation links
- Epic closed 2026-08-16. All 8 SPECs shipped to `done/`:
  - 01 `45e8103a`, `6852733c` — ADR-0010 badge amendment
  - 02 `3ee42b7a`, `efab650` — BillingGateway domain contract
  - 03 `45cc52e3`, `06842106`, `06feceb7`, `50f2ec44`, `d0c344f2` — :core:billing Play Billing
  - 04 `4f10deee`, `30e265cb` — Supporter state (AppSettings + SupporterRepository)
  - 05 `2d525f41`, `7110ac42`, `353cbb56`, `f728fe59`, `891699c0`, `a14fbe88`, `67b98cdf`, `07a0bb5a` — Supabase supporter_purchases journal
  - 06 `24e17bac`, `3ced475e` — AnalyticsGateway + Firebase Analytics
  - 07 `1234da3e`..`a2ca9229` (14 commits) — :feature:support screen + drawer wiring
  - 08 `d6dd1eb6`, `81bf0462`, `87d30326`, `5c6ed25e` — privacy policy Purchases + Firebase blocks
- Remaining manual (out-of-repo) prerequisites, not covered by any SPEC: Play Console
  `coffee_small`/`coffee_large` consumable products, CI secret `GOOGLE_SERVICES_JSON` (OQ-9),
  Play Console Data Safety form update.
