# Экран поддержки — финальные ассеты и карточка первого действия
Epic: support-screen-artwork-counters
Order: 00 of 02 (overview)
Status: done
Depends-on: —
Date: 2026-08-23

## Goal
Экран поддержки получает пять утверждённых растровых иллюстраций: аватар пользователя, premium-зерно с короной, две покупки кофе и просмотр рекламы. Hero-чашка больше не используется: заголовок и описание остаются, а ниже них появляется карточка с аватаром и тремя счётчиками только после первого подтверждённого действия поддержки.

Карточка становится постоянной после первого подтверждённого просмотра рекламы, покупки кофе или подписки Plus; другие счётчики при этом отображаются с нулём. Покупки, рекламная воронка, Paywall и навигация сохраняют существующую бизнес-логику.

## Locked decisions
- Визуальные входы сохранены в `C:\Users\Admin\AppSpecs\support-screen-artwork-counters\pipeline\assets\`: `support_neon_avatar.png` → avatar; `support_neon_plus.png` → Plus/premium; `support_neon_coffee_large_soft.png` → large coffee после снижения свечения; `support_neon_coffee_small.png` → small espresso; `support_neon_ads.png` → rewarded ads. Исходник большой чашки сохранён как `source-large-coffee-original.png`.
- Большая takeaway-чашка остаётся cappuccino/кофе с надписью `Thanks`; меняется только чрезмерное внешнее свечение.
- Из Support удаляется только hero-изображение чашки. Заголовок и описание сохраняются.
- Порядок: back row → headline/description → avatar/counters (после gate) → ads → small coffee → large coffee → Plus.
- Gate основан на подтверждённых данных: `AdRewardState.totalWatched`, успешно reconciled coffee counters или активный subscription entitlement.
- Видимость карточки монотонна и переживает перезапуск/окончание подписки; исторический факт поддержки хранится в существующем Supporter/DataStore-контуре.

## SPECs (run via `/mp --feature --next` in Order)
| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `support-screen-artwork-counters-01-support-screen-artwork-refresh.md` | — | presentation | заменить пять bitmap-ассетов и смягчить glow большой takeaway-чашки |
| 02 | `support-screen-artwork-counters-02-support-screen-layout-first-action.md` | 01 | domain, data, presentation | убрать только hero-чашку, поменять порядок и добавить постоянный first-action gate |

## Why this ordering
Сначала фиксируются ресурсы с сохранением существующих имён и consumer-контрактов. Затем экран может менять только композицию и состояние, не смешивая визуальную подготовку с логикой первого действия. SPEC-01 не трогает Kotlin consumers; SPEC-02 не заменяет bitmap-файлы.

## Key facts (verified)
- G2/G3: `SupportContent` сейчас выводит `SupportHeroIntro`, а hero image находится отдельно от headline/description — `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportScreen.kt:74-195`.
- G4/G5/G6: аватар и три счётчика уже собраны в `SupporterGratitude`, а SupportViewModel получает supporter/ad totals — `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportScreen.kt:399-507`; `SupportState.kt:8-14`; `SupportViewModel.kt:78-128`.
- G8/G9/G10: authoritative ad total и subscription sources уже представлены в domain; entitlement наблюдается существующим use case — `core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/ads/AdRewardState.kt:5-14`; `core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/model/UserEntitlement.kt:5-22`; `core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/ObserveEntitlementUseCase.kt:8-16`.
- G16/G17/G18: Supporter counters хранятся в AppSettings/DataStore и уже защищены монотонными обновлениями — `core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/model/AppSettings.kt:20-26`; `SupporterRepositoryImpl.kt:25-71`; `AppSettingsRepositoryImpl.kt:35-79`.
- G13/G15: Compose tests фиксируют текущий hero/order/counters, а визуальные изменения требуют Pixel 5/API 34 gate — `feature/support/src/test/java/com/kshavrin/mymoney/feature/support/SupportScreenContentTest.kt:38-108,347-405`; `AGENTS.md`, «Visual-change device gate».

## Implementation links
- commits: b3e81e5f, 22b02f19, d17ff0be, 4fa76689, 51c7d840, 59741951
- files: approved Support bitmap assets and support-screen composition, durable first-action gate, focused tests, and edge-to-edge safe-area repair
