# Эпик: plus-subscription-gating — подписка MyMoney Plus и гейтинг shared-sync
Epic: plus-subscription-gating
Order: 00 of 10 (overview)
Status: done
Completed: 2026-08-16
Depends-on: support-hub-tip (внешний блокер — заводит `:core:billing` и `:feature:support`)
Date: 2026-08-12

## Цель

Закрыть единственную статью расходов проекта: shared-workspace синхронизация на Supabase
(`core/network/.../shared/`, эпик `shared-backend-sync` 01–04) становится платной возможностью
**MyMoney Plus**, а приватный синк Dropbox/Google Drive остаётся бесплатным навсегда — платно
только то, что стоит денег владельцу приложения (ADR-0010 D2).

Эпик даёт: доменный `UserEntitlement (Free | Plus)` как единственный источник истины для гейтинга;
подписки €1.99/мес и €12.99/год с 7-дневным триалом только на годовом; единый paywall с двумя
точками входа; **серверную** валидацию entitlement (клиентская проверка допустима только для
косметики); правило «платит владелец воркспейса, участники бесплатны»; полную машину состояний
`None → Trial → Active → Grace → Expired → LocalOnly` с предупреждениями и без единой потерянной
записи при отцепке; удалённый killswitch на случай неконтролируемых расходов Supabase; и —
последним шагом — переворот `PLAY_RELEASE_SYNC_ENABLED` в релизных дефолтах.

**В скоуп НЕ входит:** реклама и AdMob SDK (следующий эпик — здесь только модель состояний
ad-Plus), разовая покупка «кофе» (приходит из `support-hub-tip`), новые возможности синхронизации,
экран About, backup version history и supporter badge, «перехват оплаты» участником (v2 — заложена
только схема данных).

## Заблокированные решения (из grill)

- **D1**: незакоммиченный серверный код монетизации в `supabase/` (схема `entitlements`/`supporters`/
  `ad_rewards`/`provider_events`/`activation_codes`, триггер наград, функции `google-play-rtdn`,
  `admob-ssv`, `create-ad-reward-token`, `redeem-activation-code`) считается **готовым фундаментом**:
  эпик коммитит его как есть и достраивает только недостающее.
- **D2**: `:core:billing`, `:feature:support` и раздел «Поддержать проект» заводит эпик
  `support-hub-tip`; здесь только расширение подписками и entitlement (ADR-0010 D7).
- **D3**: полный notification-стек (канал + `POST_NOTIFICATIONS` + WorkManager-воркер) берётся в
  этот эпик; отказ от разрешения → деградация в баннер на экране Cloud sync.
- **D4**: аналитика монетизации входит в скоуп. *(Уточнено после появления соседних эпиков на доске:
  сам `AnalyticsGateway`, `firebase-analytics` и обе реализации везёт `support-hub-tip-06`; этот эпик
  добавляет только шесть событий подписки. Guard `HAS_FIREBASE` обязателен в любом случае.)*
- **D5**: плательщик — **поле воркспейса**: `workspaces.payer_user_id` + денормализованное
  `billing_state (active|grace|expired)` с дедлайном, обновляемое RTDN и планировщиком. Все
  shared-RPC читают его. «Перехват оплаты» в v2 = смена `payer_user_id`, **без миграции**.
- **D6**: ad-Plus даёт **полный Plus** — тот же объём прав, что у платной подписки, включая
  shared-sync, создание воркспейса с участниками и выпуск приглашений. Источник entitlement
  хранится, но на объём прав не влияет. Единственное отличие — срок: 24 часа и **никакого grace**,
  по истечении сразу диалог оплаты и отцепка (решение пользователя, msg 3 п.3; ADR-0010 D5).
  То же читает соседний эпик — `support-rewarded-ads-00:13-14`.
- **D7**: разворот пункта «no remote kill switch» оформляется **новым ADR-0011**, amend'ящим
  ADR-0010 D1.
- **D8**: killswitch = **полная отцепка в LocalOnly** тем же путём, что Expired. Только это реально
  обнуляет счёт Supabase — расходы генерируют realtime-подписки и pull, а не запись.
- *(assumption)* whitelist администрируется вручную через SQL editor + runbook в `supabase/README.md`;
  UI администрирования не делается.
- *(assumption)* Play Console (два base-plan, 7-дневный оффер только на годовом, RTDN-топик) и форма
  Data Safety — ручные пререквизиты вне репозитория.
- *(assumption)* серверный отказ по entitlement возвращается кодом `entitlement_required`, который
  клиент мапит в `SyncError` и в баннер.

## SPEC'и (собираются через `/mp --feature --next` в порядке)

| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `plus-subscription-gating-01-entitlement-domain-model.md` | — | domain | `UserEntitlement` + машина состояний и пороги предупреждений |
| 02 | `plus-subscription-gating-02-supabase-entitlement-and-rpc-gating.md` | 01 | server+data | RPC чтения entitlement, `payer_user_id`/`billing_state`, гейтинг всех shared-RPC |
| 03 | `plus-subscription-gating-03-billing-subscriptions-and-entitlement-repo.md` | 01, 02, shtip-02, shtip-03 | data | подписки `plus_monthly`/`plus_yearly` + server-authoritative `EntitlementRepository` |
| 04 | `plus-subscription-gating-04-paywall-screen.md` | 03, shtip-07 | presentation | единый paywall, две точки входа, деградация по региону |
| 05 | `plus-subscription-gating-05-cloudsync-gating-and-warning-banners.md` | 04 | presentation | гейт включения Shared, баннеры Trial/Grace/Expired, правило «платит владелец» |
| 06 | `plus-subscription-gating-06-local-only-transition.md` | 01, 03 | data+domain | снапшот → отцепка без потери данных → переподключение после покупки |
| 07 | `plus-subscription-gating-07-entitlement-notifications.md` | 01, 03, 04 | data+presentation+docs | канал, `POST_NOTIFICATIONS`, воркер, три предупреждения, отмена Q-D3 в TDD |
| 08 | `plus-subscription-gating-08-monetization-analytics-events.md` | 04, 05, 06, shtip-06 | domain+data | шесть событий монетизации поверх `AnalyticsGateway` |
| 09 | `plus-subscription-gating-09-shared-killswitch-and-release-flip.md` | 01–08 | data+build | killswitch по `KEY_SHARED_SYNC`, ADR-0011, флип `PLAY_RELEASE_SYNC_ENABLED` |
| 10 | `plus-subscription-gating-10-privacy-policy-monetization-update.md` | 09 | docs | Block 3 черновика политики: снятие оговорок + хранение entitlement |

## Почему такой порядок

01 — чистый домен, безопасен в любой момент и задаёт словарь для всех остальных. 02 обязан выйти
до 03: без `get_my_entitlement()` клиенту нечего читать, а без гейтинга RPC требование «сервер сам
перестаёт обслуживать» не закрывается. 03 блокируется внешним эпиком `support-hub-tip` (модулей
`:core:billing`/`:feature:support` в `settings.gradle.kts` ещё нет — G17).

**09 идёт последним по прямому требованию ADR-0010 D1 (:38-48):** переворот
`PLAY_RELEASE_SYNC_ENABLED` до готового entitlement-гейта открывает окно, в котором Supabase-воркспейс
достаётся всем бесплатно — ровно тот расход, который эпик и закрывает. 10 обязан выйти **в том же
релизе**, что 09 (ADR-0010 «Resolved during review» :216-220).

**Клэши по файлам (требуют секвенции, не параллельного редактирования):**
- 03 и 07 оба правят `app/src/main/AndroidManifest.xml` (`com.android.vending.BILLING` vs
  `POST_NOTIFICATIONS`) → 03 первым.
- 04 и 05 оба правят навигационный граф в `:app` (маршрут paywall и переход на него с Cloud sync)
  → 04 первым.
- 05 и 06 оба правят `CloudSyncViewModel.kt` → 05 первым.

## Пересечения с соседними эпиками на доске

На доске одновременно лежат `support-hub-tip` (8 SPEC-ов) и `support-rewarded-ads` (6). Границы
поделены так — нарушать нельзя, иначе получим два `AnalyticsGateway` и дублирующие абзацы в
публичной юридической политике:

| Что | Кто делает | Что делает этот эпик |
|---|---|---|
| `:core:billing`, `BillingGateway`, `BILLING_ENABLED` | `support-hub-tip-02/03` | расширяет подписками (SPEC 03) |
| `:feature:support`, маршрут, пункт drawer | `support-hub-tip-07` | добавляет paywall внутрь (SPEC 04) |
| `AnalyticsGateway` + Firebase Analytics + фейк | `support-hub-tip-06` | добавляет 6 событий (SPEC 08) |
| Политика: Block 1 «Purchases», Block 2 «Firebase services» (строка 55) | `support-hub-tip-08` | не трогает |
| Политика: Block 3 «Shared workspace wording» | **этот эпик** (SPEC 10) | применяет дословно |
| Политика: Block 4 «Advertising» | `support-rewarded-ads` | не трогает |
| `ad_rewards`, заморозка прогресса, `get_ad_reward_state()` | `support-rewarded-ads-01` | только модель состояний ad-Plus (SPEC 01, D6) |
| `supporter_purchases` в схеме монетизации | `support-hub-tip-05` | не трогает |

Разметка блоков политики — `docs/legal/privacy-policy-monetization-draft.md:22-29`.

**Расхождение снято (проверено 2026-08-12).** `list_migrations` для `shwzjlkhlpgbmzgnxhxi`
подтверждает, что `monetization_schema_20260812130000` и `admob_reward_grant_20260812140000`
**применены**: прав `support-rewarded-ads-01` (G40). `support-hub-tip-05` исправлен — он больше не
правит `20260812130000` in-place, а заводит отдельный файл. **Правило для всех трёх эпиков:
существующие миграции неприкосновенны, любое изменение схемы — только новым файлом с версией
строго больше `20260812133214`.**

## Конвенция ссылок в SPEC'ах

Каждая строка `CHANGED_HINT`, которая **утверждает что-то о существующем коде** (путь, сигнатура,
номер строки, поведение), ссылается на факт `G#` из раздела ниже либо помечена `(assumption)`.
Строки, создающие **новые** файлы, о репозитории ничего не утверждают и ссылки не требуют — они
трассируются к решению `D#` из grill-раздела выше.

## Ключевые факты (verified, из grounding)

- **G1**: `RemoteConfigRepositoryImpl.sharedSyncEnabled()` возвращает `PLAY_INTERNAL_SYNC_ENABLED ||
  PLAY_RELEASE_SYNC_ENABLED || syncForced()`; константа `KEY_SHARED_SYNC = "shared_sync_enabled"`
  объявлена, но **нигде не читается** — `core/sync/.../remoteconfig/RemoteConfigRepositoryImpl.kt:55-56,75,83`.
- **G2**: `sync.playReleaseEnabled` читается в ДВУХ местах, оба дефолтят `false` —
  `app/build.gradle.kts:71-72` и `core/sync/build.gradle.kts:19-20`. Гейт читает `BuildConfig`
  именно из `:core:sync`, поэтому флип нужен синхронно в обоих.
- **G3**: `BuildConfig.HAS_FIREBASE` — если false, ни один класс Firebase трогать нельзя
  (`FirebaseApp` не инициализируется) — `RemoteConfigRepositoryImpl.kt:16-32`, `core/sync/build.gradle.kts:23-27`.
- **G4**: `requireSyncRuntimeConfiguration()` падает на билде без реальных Dropbox app key / Supabase
  URL / anon key / Google web client ID при включённом флаге — `app/build.gradle.kts:89-101`.
- **G5**: интерфейсы репозиториев живут в `:core:domain`, impl — в `:core:sync`/`:core:database`;
  `sharedSyncEnabled()` имеет default `= false` (`core/domain/.../repository/RemoteConfigRepository.kt:14`).
- **G6**: `SharedSyncCoordinator` — единственная точка оркестрации Shared (`signIn/createWorkspace/
  joinWorkspace/createInvite/syncNow/startForegroundRealtime/disconnectFromDevice/leaveWorkspace/
  activeWorkspaceOwnership`) — `core/sync/.../shared/SharedSyncCoordinator.kt:28-92`.
- **G7**: локальный снапшот уже есть — `BackupRepository.createInternalBackup(): Result<String>`
  (`core/domain/.../repository/BackupRepository.kt:59`), координатор зовёт его в пяти местах —
  `SharedSyncCoordinatorImpl.kt:149,170,192,319,351`.
- **G8**: outbox — `SharedOutboxDao` (`core/database/.../dao/SharedOutboxDao.kt:12`,
  `MoneyDatabase.kt:76`), чистится `clearSharedOutbox()` — `SharedSyncCoordinatorImpl.kt:374,441,470,496`.
- **G9**: точки входа в Shared — `SharedSetupClicked` / `SharedCreateWorkspace` / `SharedJoinWorkspace`
  (`feature/cloudsync/.../CloudSyncEvent.kt:54-66`); состояние карточки — `SharedCardState`
  (`CloudSyncState.kt:46-55`); диалоги — `SharedDialog` (:57-81).
- **G10**: баннер на Cloud sync уже есть — `errorBannerRes: Int?` в `CloudSyncState.kt:23`.
- **G11**: схема монетизации **не закоммичена в git, но применена к БД** (`list_migrations`,
  2026-08-12) — `supabase/migrations/20260812130000_monetization_schema.sql` (+ RLS «select own» и
  `revoke all`), `20260812140000_admob_reward_grant.sql` (5 наград → `now() + 24h`, не стакается).
  Оба файла коммитятся как есть и **не правятся**; любое изменение схемы — новой миграцией.
- **G12**: Edge Functions не закоммичены, но **задеплоены и активны**: `google-play-rtdn` (allowlist
  `plus_monthly`/`plus_yearly`, `GRANTABLE` = ACTIVE/IN_GRACE_PERIOD/CANCELED, дедуп через
  `provider_events`), `admob-ssv`, `create-ad-reward-token`, `redeem-activation-code`.
- **G13**: **ни одна** shared-RPC entitlement не проверяет — только членство `is_active_member`:
  `supabase/migrations/0001_shared_workspaces.sql:53,84,110,162`, `0002_shared_operations.sql:74,149,199,254`.
- **G14**: RPC чтения своего entitlement нет; прямой `select` закрыт `revoke all ... from authenticated`
  → нужен `security definer` RPC.
- **G15**: Firebase Analytics отсутствует полностью — в каталоге только `firebase-bom`
  (`gradle/libs.versions.toml:134`) и `firebase-config-ktx` (:135).
- **G16**: notification-инфраструктуры нет вообще (ни `NotificationCompat`, ни канала, ни
  `POST_NOTIFICATIONS`); `minSdk 31` → на 33+ нужен runtime-permission.
- **G17**: `:core:billing`, `:core:ads`, `:feature:support` отсутствуют в `settings.gradle.kts:32-51`.
- **G18**: WorkManager-инфраструктура есть и переиспользуется: `WorkSchedulerImpl`, `SyncWorker`,
  `RecurringWorker`, `PruneDeletedWorker`, `BackupRotationWorker` — `core/sync/.../worker/`.
- **G19**: ADR-0010 — D1 «флип делается в этом эпике, не раньше» (:38-48); D3 цены и триал (:70-80);
  D7 модули (:116-126) и «entitlement читается через интерфейс в `:core:domain`, чтобы
  `:feature:cloudsync` не зависел от `:core:billing`» (:124-126).
- **G20**: ADR-0010 D1 явно фиксирует «There is no remote kill switch… separate work» (:50-54,
  повтор :196-198) — **противоречие** с требованием killswitch, снимается ADR-0011.
- **G21**: privacy policy — оговорки на `privacy_policy_en.html:33`, `:48`, `:55`; эталонная
  формулировка `:49`; дата `:21` «Last updated: August 11, 2026»; зеркало `privacy_policy_ru.html`
  + GitHub Pages.

## Implementation links
- (заполняется по мере выполнения)
