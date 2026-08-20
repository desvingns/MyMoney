# Support screen redesign — epic overview
Epic: support-screen-redesign
Order: 00 of 07
Status: backlog
Depends-on: —
Date: 2026-08-20

## Цель
Экран «Поддержать проект» (`Destinations.Support`) сейчас — вертикальный стек разнородных M3-карточек:
строка «Всего просмотрено рекламы», абзац описания, встроенная rewarded-карточка, карточка Plus,
карточка покупки кофе списком кнопок, бейдж и благодарность. Эпик приводит экран к утверждённому
макету (`Экран поддержки 1A`): hero-иллюстрация, крупный заголовок с акцентным словом и три
однотипных «плашки» (реклама → кофе → Plus) плюс постоянная карточка благодарности с тремя
счётчиками. Бизнес-логика биллинга и rewarded-рекламы не меняется — редизайн затрагивает
presentation-слой, кроме одного дополнения в данных (раздельные счётчики покупок).

Источник дизайна (в репозитории): `docs/design/support-screen-1A.html` — распакованная вёрстка
утверждённого макета; в ней все иллюстрации — штрихованные заглушки с подписями («чашка кофе»,
«S CUP», «L CUP», «AD», «★», «аватар»). Стилевой референс иллюстраций —
`docs/design/category-icons-new-28-neon.png`. Рабочие файлы пайплайна (grounding / grill /
decomposition) лежат вне репозитория: `AppSpecs/support-screen-redesign/pipeline/`.

## Итоговая раскладка экрана (сверху вниз)
1. Кастомная строка «‹ Назад» + заголовок экрана (вместо M3 `TopAppBar`).
2. Hero-иллюстрация 196dp (чашка кофе).
3. Заголовок в две строки: «Помогите MyMoney» + акцентное «расти» (мятное, с подчёркиванием) + подзаголовок.
4. **Плашка рекламы** — иллюстрация, заголовок, прогресс-бар N/5, CTA; все статусы внутри плашки.
5. **Плашка кофе** — две колонки (маленький/большой): иллюстрация, название, цена, «Поддержать»; статус биллинга строкой под плашкой.
6. **Плашка Plus** — иллюстрация, текст, кнопка → существующий экран Paywall.
7. **Карточка благодарности** — аватар, текст (разный для «уже поддержал» / «ещё нет»), чип Supporter, три счётчика: реклама / мл. кофе / бл. кофе.

Убираются: секция «Безопасные платежи. Отмена в любой момент.» и отдельная строка
«Всего просмотрено рекламы: N» (счётчик переезжает в п. 7).

## Заблокированные решения (grill 2026-08-20)
- D1: никаких новых экранов/destination'ов и bottom sheet'ов — весь контент на одном экране Support (макетная строка-со-шевроном для рекламы отменена).
- D2: новый порядок секций — реклама → кофе → Plus → благодарность.
- D3: секция «Безопасные платежи» не реализуется вообще.
- D4: отдельная строка «Всего просмотрено рекламы» удаляется; слот `videosWatchedSlot` / `TotalAdsWatchedBadge` (G11) становится не нужен.
- D5: топбар — кастомная строка «‹ Назад» + заголовок экрана; цель касания 48dp, не 44px как в макете (G32, H4).
- D6: плашка рекламы верстается в стилистике плашки кофе + прогресс-бар; все 10 состояний `RewardedAdStatus` (G16) остаются внутри плашки строкой под кнопкой, для `Unauthenticated` CTA меняется на «Войти»; логика `RewardedAdViewModel` не трогается.
- D7: статусы биллинга (Loading / Pending / NetworkError / Unavailable, G7) — строкой под плашкой кофе; сама плашка из вёрстки не выпадает.
- D8: кнопка плашки Plus ведёт на существующий `Destinations.Paywall(SupportSection)` (G4); цены подписки на плашке нет, планы остаются на Paywall.
- D9: карточка благодарности показывается всегда, текст разный для supporter / не-supporter; внутри три счётчика.
- D10: раздельные счётчики — два новых Int-поля в `AppSettings`, инкремент в `SupporterRepositoryImpl.recordPurchase` по `PurchaseOutcome.Purchased.productId` (G34).
- D11: миграция — накопленный `supportPurchaseCount` бэкфиллится в «мл. кофе», чтобы сумма счётчиков сходилась с итогом.
- D12 (assumption): раздельные счётчики не синхронизируются через `mergeRemote` и остаются локальными; суммарный `supportPurchaseCount` синхронизируется как раньше — изменений в Supabase нет.
- D13 (assumption): счётчик роликов попадает в `SupportState` через существующий `ObserveAdRewardStateUseCase` (G36), а не через слот-композабл.
- D14: иллюстрации — растровые PNG в `core/designsystem/src/main/res/drawable-nodpi/`, по прецеденту `category_neon_*.png` (G25).
- D15 (assumption): шрифт остаётся `FontFamily.Default`; из макета переносятся только начертания и размеры. Manrope не подключается (G20).
- D16 (assumption): все цвета — из существующих семантических алиасов `ColorScheme` (G18/G19); новые оттенки объявляются как новые extension-property в `Color.kt`, сырой `Color(0x…)` на месте вызова запрещён.

## SPECs (run via /mp --feature --next in Order)
| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `support-screen-redesign-01-neon-illustration-assets.md` | — | resources | Шесть PNG-иллюстраций в `drawable-nodpi` по манифесту |
| 02 | `support-screen-redesign-02-per-product-purchase-counters.md` | — | domain, data | Раздельный учёт мл./бл. кофе + бэкфилл исторического итога |
| 03 | `support-screen-redesign-03-design-tokens-and-strings.md` | — | presentation | Токены темы под новую вёрстку + EN/RU строки |
| 04 | `support-screen-redesign-04-support-state-counters.md` | 02 | presentation | `adsWatchedTotal` + раздельные счётчики в `SupportState` |
| 05 | `support-screen-redesign-05-screen-shell-and-coffee-card.md` | 01, 03 | presentation | Каркас экрана + плашка кофе (clash с 07) |
| 06 | `support-screen-redesign-06-rewarded-ad-card.md` | 01, 03, 05 | presentation | Плашка рекламы с прогресс-баром и статусами внутри |
| 07 | `support-screen-redesign-07-plus-card-and-gratitude.md` | 01, 03, 04, 05 | presentation | Плашка Plus + постоянная карточка благодарности (clash с 05) |

## Почему такой порядок
Foundation-first: ассеты (01), данные (02) и токены/строки (03) не зависят ни от чего и могут
идти параллельно; 04 достраивает `SupportState` поверх 02. UI-SPEC'и идут строго последовательно
05 → 06 → 07, потому что верстают один экран поверх общей стилистики, введённой в 05.

Clash-check (файлы, которые правят несколько SPEC — параллельный запуск запрещён):
- `feature/support/.../SupportScreen.kt` — SPEC-05 и SPEC-07.
- `feature/support/.../rewardedad/RewardedAdScreen.kt` — SPEC-05 (удаляет `TotalAdsWatchedBadge`) и SPEC-06.
- `feature/support/src/main/res/values{,-ru}/strings.xml` — SPEC-03 (добавляет), SPEC-05 и SPEC-07 (выводят из употребления старые ключи).
- `feature/support/.../SupportScreenContentTest.kt` — SPEC-05 и SPEC-07.

## Ключевые факты (verified)
- G2/G3: `composable<Destinations.Support>` подставляет три слота — `TotalAdsWatchedBadge`, `RewardedAdSupportEntry`, `PaywallSupportEntry` — `app/src/main/java/com/kshavrin/mymoney/navigation/MyMoneyNavHost.kt:248-266`.
- G5/G6: текущий `SupportContent` — Scaffold + M3 `TopAppBar` + `Column(verticalScroll)`, порядок секций `videosWatched → description → ad → plus → purchase → badge → gratitude` — `SupportScreen.kt:54-105`.
- G7/G8: `SupportPurchaseSection` подменяет карточку кофе на статус-карточку; `CoffeePurchaseCard` — вертикальный список `Button` — `SupportScreen.kt:107-187`.
- G9: благодарность и бейдж под условием `supporterState.badgeEarned` — `SupportScreen.kt:99-102`.
- G16: у rewarded-блока 10 статусов — `rewardedad/RewardedAdState.kt:15-34`.
- G18/G19: вся палитра макета уже есть в `NeonColors` + семантические алиасы — `core/ui/.../theme/Color.kt:9-52,497-538`. Новых сырых цветов эпик не вводит.
- G20: тема на `FontFamily.Default`, ни одного `res/font` в проекте — `Typography.kt:11-22`.
- G22: `Spacing.support*` — 5 токенов, минимальная цель касания 48dp — `Spacing.kt:207-211`.
- G25: прецедент растровых неон-иллюстраций — `core/designsystem/src/main/res/drawable-nodpi/category_neon_*.png`.
- G27/G28: `SupportScreenContentTest` и `RewardedAdScreenContentTest` ассертят текст, который редизайн убирает — переписываются в тех же SPEC, ослаблять ассерты запрещено.
- G34/G35: `PurchaseOutcome.Purchased.productId` доступен в `recordPurchase`; `supporterBadgeEarned` монотонен (откат true→false кидает исключение) — `AppSettingsRepositoryImpl.kt:42-43`.
- G36: `ObserveAdRewardStateUseCase` → `AdRewardState.totalWatched`, доступен из `feature/support` — `core/domain/.../ads/AdRewardState.kt:5-14`.
- G37: `PaywallStringsTest` держит EN/RU паритет по префиксам `paywall_` и `support_ads_` — `PaywallStringsTest.kt:11-30`.
- G38: `rewardAdProgressIndicator` / `rewardAdProgressTrack` уже объявлены — `Color.kt:546-551`.

## Открытые вопросы (assumption)
- O1 (SPEC-01): Codex не генерирует растровые изображения. Приёмка SPEC-01 — **манифест ассетов**
  (имена, точные размеры, стиль, contentDescription). Если готовых PNG на момент реализации нет,
  кладутся временные заглушки того же размера, сборка и тесты остаются зелёными, а факт заглушки
  фиксируется в `## Implementation links` SPEC-01. Финальные картинки подкладываются отдельно.
- O2: Roborazzi-скриншот-тесты для `feature/support` в этом эпике **не заводятся** (G30).
  Визуальная приёмка — ручная, на Pixel 5 API 34 (visual device gate, AGENTS.md).
- O3 (D12): раздельные счётчики локальны. Если позже понадобится их синхронизация, это отдельный
  эпик с изменением контракта `mergeRemote` и схемы Supabase.

## Чеклист для человека
- [ ] Экран открывается: строка «‹ Назад» + заголовок, hero-иллюстрация, заголовок с мятным подчёркнутым словом.
- [ ] Порядок плашек: реклама → кофе → Plus → благодарность; секции «Безопасные платежи» нет.
- [ ] Плашка рекламы: прогресс N/5, кнопка; при выходе из аккаунта — CTA «Войти», статусы видны внутри плашки.
- [ ] Плашка кофе: две колонки с ценами из Play, кнопки «Поддержать» некликабельны во время покупки.
- [ ] Авиарежим → под плашкой кофе появляется статус ошибки, сама плашка остаётся на месте.
- [ ] Плашка Plus открывает экран Paywall.
- [ ] Карточка благодарности видна и до первой покупки (другой текст, без чипа), после покупки — с чипом Supporter.
- [ ] Три счётчика показывают реальные значения; после покупки маленького кофе растёт «мл. кофе».
- [ ] TalkBack: у каждой иллюстрации есть описание, у кнопки возврата — метка, цели касания ≥48dp.
- [ ] RU и EN локали — тексты не обрезаются на 390dp ширине.

## Implementation links
- commit: —
- files: —
