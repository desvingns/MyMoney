# Style Analysis — Monefy

## Overall style
**Style:** `flat-custom` with **Material 2 / early-Material 3 lineage** — flat surfaces, soft-mint pastel theme, hand-drawn-feeling outline icons, generously rounded shapes, near-zero elevation.
**Confidence:** 0.85
**One-line essence:** "A pastel mint piggy-bank donut chart surrounded by colorful outline icons — playful and friendly, not corporate."

## Palette (light)
*Confidence column reflects how reliably the hex was sampled — APK ground-truth will override.*

| Token | HEX | Use | Confidence |
|---|---|---|---|
| `primary` | `#7AC29A` | top app bar background, amount input field, + button ring, brand green | high |
| `on_primary` | `#FFFFFF` | white text/logo on top app bar, white "0" in amount field | high |
| `secondary` | `#E89090` | "-" (expense) button ring, red balance pill, expense slice in donut | high |
| `tertiary` | `#F4B85C` | yellow accent (taxi icon, price-tag icon, accent slice) | medium |
| `background` | `#EAF6EC` / `#E8F5EA` | very pale mint screen background | high |
| `on_background` | `#3D5A4A` | dark greenish-gray body text | medium |
| `surface` | `#F1F8F2` | calculator key surface, drawer panel surface, slightly lighter than bg | medium |
| `on_surface` | `#3D5A4A` | text on cards/keys | medium |
| `surface_variant` | `#D9EBDC` | category-grid cell hover/selected outline tone | medium |
| `error` | `#E07A7A` | red expense balance pill, "-" ring, "Здоровье" icon red | medium |
| `outline` | `#B5D6BC` | thin mint outline around input field, category cards, calculator keys | medium |
| `text_primary` | `#2E4A3A` | titles, balance text | medium |
| `text_secondary` | `#7A9685` | date row, percentage labels, captions | medium |
| `divider` | `#CFE3D2` | thin separators between drawer items | low |

### Category icon accent palette (decorative, not theme tokens)
Each expense/income category uses its own bright outline color — this is a **content palette**, not part of the M3 token set:

| Category | Color | HEX (approx) |
|---|---|---|
| Одежда (clothing) | purple | `#9C5BB8` |
| Счета (bills/tag) | mustard | `#C9A227` |
| Еда (food/basket) | pink | `#E07AAE` |
| Развлечения (cocktail) | orange | `#F08A3E` |
| Такси | yellow-amber | `#E0A52C` |
| Жилье (house) | blue | `#4A8FCB` |
| Здоровье (thermometer) | red | `#D85A5A` |
| Питомцы (cat) | teal/green | `#3DA98A` |
| Спорт | mint | `#7AC29A` |
| Подарки | dusty-pink | `#D9A4A4` |
| Связь (phone) | sage-gray | `#9CBBA8` |
| Транспорт (train) | red-pink | `#E07A7A` |
| Гигиена (toothbrush) | navy | `#3A4F8C` |
| Кафе (fork-knife) | gray-green | `#7A9685` |
| Машина | navy-gray | `#4A5870` |

## Palette (dark) — detected: **no**
Not visible across screens 01–10. (Keyboard in 08 is OS-level dark, not app dark mode.) The app appears light-only in this version.

## Typography

- **Family guess:** `Roboto` (confidence 0.6) — could be **Open Sans** or a system fallback. Heading "Monefy" wordmark is a **script/handwritten custom logo font** (confidence high it is custom — likely a packaged asset, not a system font).
- **Calculator digits** in 06/07 look slightly thinner/rounder — could be Roboto Light or a custom monoweight; treating as Roboto Light.

### Type scale (sp, estimated)

| Role | Size | Weight | Where seen |
|---|---|---|---|
| `display_large` | 48 | Light/Regular | "0" / "8" amount in green input field (03, 06, 07, 09, 10) |
| `headline_large` | 28 | Regular | "Monefy" wordmark (script) |
| `headline_medium` | 22 | Medium | calculator digits 1–9 (06, 07) |
| `title_large` | 20 | Regular | top-app-bar titles "Новый расход", "Новый перевод" (03, 06, 07, 09, 10) |
| `title_medium` | 18 | Regular | balance numbers "2 442 740,80 ₽" centered in donut (05, 08) |
| `body_large` | 16 | Regular | drawer item labels "День / Неделя / Месяц / Год / Все" (02), "Категории / Счета / Валюты / Настройки" (04) |
| `body_medium` | 14 | Regular | "Заметка" placeholder, category labels "Гигиена / Еда / Жилье" (09, 10), date "Воскресенье, 17 мая" |
| `label_large` | 14 | Medium/CAPS | "ВЫБОР КАТЕГОРИИ" button (06, 07), "ДОБАВИТЬ" (09) |
| `label_medium` | 12 | Regular | percentage labels "44%", "56%", "3%", "21%" under donut slices |
| `caption` | 11 | Regular | "Динары / RUB" subtitle under wordmark, currency dropdown sub-label |

**Weights used:** Regular (400), Medium (500). No bold body text observed; calculator digits look Light (300). Logo is decorative script.

## Spacing

- **Base unit:** **8dp** (most paddings/gaps are multiples of 8; tighter elements like icon-to-label use 4)
- **Steps observed:** `4, 8, 12, 16, 20, 24, 32`

| Token | Value | Use |
|---|---|---|
| `xs` | 4dp | icon-to-label vertical gap in category grid |
| `s` | 8dp | between calculator keys, inside chip padding |
| `m` | 16dp | screen horizontal padding, between major form fields |
| `l` | 24dp | section spacing on settings drawer, around donut chart |
| `xl` | 32dp | between amount field and category grid in expense screen |

## Corner radius (dp)

| Element | Radius | Notes |
|---|---|---|
| **Buttons (rounded rect)** | 12 | balance pill in 01/05, "День/Неделя" buttons in drawer (02), currency dropdowns |
| **Big amount field** | 16 | green block in 03/06/07/09/10 |
| **Cards (category cells)** | 12 | grid cells in 09/10, light outline + slight inner padding |
| **Calculator keys** | 12 | each key card in 06/07 |
| **+ / − circular buttons** | 999 (full circle) | bottom of home screen |
| **FAB / dial-pad button** | 999 (full circle) | green circle bottom-center in transfer screen (03) |
| **Drawer panel** | 0 (square edges, full-height sheet) | side drawers (02, 04) |
| **Dialog/sheet** | not directly observed | likely 12–16 |
| **Avatars / icon circles** | n/a | icons are line-art, not contained |

**Default radius:** 12dp covers most components.

## Elevation / shadows (dp)

This app is **aggressively flat**. Shadows are nearly absent.

| Surface | Elevation | Shadow style |
|---|---|---|
| Top app bar | 0 | flat, no shadow — color block only |
| Card (category cell) | 0 | flat with thin mint outline instead of shadow |
| Calculator key | 0 | flat with thin outline |
| Balance pill | 1 (barely) | subtle soft shadow under red pill in 01/05 |
| Circular +/− buttons | 1–2 | very faint shadow visible |
| Drawer (side sheet) | 4 | drop shadow on the edge where it overlays the dimmed home screen |
| FAB (dial-pad green circle) | 4 | soft visible shadow |
| Donut chart slices | 1 | very subtle inner shadow on inner edge of ring (depth illusion) |

**Shadow softness:** soft, low-opacity — no sharp M2-style elevation. Matches the friendly/flat aesthetic.

## Component kit

Compose / Material analogues observed:

- **TopAppBar** — green `#7AC29A` background, white icons, centered/left-aligned title; sometimes shows custom **script wordmark "Monefy"** as title with currency subtitle
- **NavigationDrawer** (right-side, screen 04) — vertical icon+label list, full-height
- **NavigationDrawer / BottomSheet hybrid** (left-side, screen 02) — period-filter list with button-like rows
- **CustomDonutChartView** — central, with center balance numbers and category icons radiating outward with thin connector lines
- **CustomCategoryIconRow** — 14+ outline category glyphs arranged around donut on home
- **CustomBalancePill** — pill-shaped balance summary (rounded 12, red bg when negative)
- **CircularIconButton** — large 56–64dp +/− circles at bottom of home
- **FAB** — green circle with dial-pad icon (transfer screen)
- **OutlinedTextField** — currency dropdown rows (3) with leading icon, label, sublabel, trailing chevron; mint outline
- **TextField (single underline)** — "Заметка" note field with leading pen icon
- **Custom amount input** — large green rounded-rect with currency prefix ("RUB" or "€"), big white number, trailing close/clear icon
- **CalculatorKeypad** — 4×5 grid of bordered keys (digits + operators), bottom-spanning "ВЫБОР КАТЕГОРИИ" text button
- **CategoryGrid** — 3-column scrollable grid of outlined cards, each with colored line-icon + label; "+ ДОБАВИТЬ" cell at end
- **Button (filled, primary)** — "День" highlighted with light-green fill in drawer
- **Button (outlined)** — "Неделя / Месяц / Год / Все" in drawer (mint outline, transparent fill)
- **SearchField** — full-bleed in top app bar (screen 08): leading back arrow, hint "Поиск записей", trailing mic
- **Banner / DateHeader** — date row above content "16 мая · Воскресенье, 17 мая"
- **Snackbar** — not observed in these 10 screens
- **Dialog** — not directly observed
- **Tab** — not used; period switching is via drawer
- **BottomNavigation** — not used; navigation is via top-bar icons + drawer

### Custom components (no direct M3 equivalent)
- `MonefyDonutChart` — segmented donut with icon-spokes
- `MonefyAmountInput` — green rounded-rect with prefix currency and trailing clear
- `MonefyCalculator` — 4-col digit + operator pad with category CTA
- `MonefyBalancePill` — color-flipping balance summary

## Icon style

**`hand-drawn line-art / custom-illustrative`** — this is the most distinctive signature of the app.
- All category icons are **outlined, single-stroke, hand-drawn-feeling** glyphs (thermometer, cat, house, fork+knife, taxi, gift, etc.)
- Each icon uses its **own color** (not theme-tinted) — purple shirt, blue house, pink basket, etc.
- **Stroke weight** is consistent and slightly imperfect (looks intentionally hand-sketched, not geometric)
- **No filled / two-tone variants** — pure outline only
- Top-bar utility icons (search, swap, hamburger, more-vert, back-arrow, mic) are **standard Material Symbols Outlined** in white
- Wallet, gear, $-circle, notebook icons in drawer (04) are also **outlined, mint-green tinted**

This icon system is the strongest visual identifier — it gives Monefy its "playful" / "friendly notebook" feel and is the gaming-angle hook.

## Brand & misc

- **Brand primary:** `#7AC29A` (mint green) — used for app bar, primary CTA, brand wordmark color when inverted
- **Logo color:** white-on-mint (top bar)
- **Wordmark font:** custom handwritten script "Monefy" — confidence high it is a packaged asset
- **Accent pattern:** none (no gradients, no repeating motif other than the icon set itself)
- **Illustrations:** illustration-as-icon style — the entire app *is* its illustration library. No separate hero illustrations on empty states observed.
- **Illustration style:** `hand-drawn outline / flat vector with personality`

## Whimsical / playful elements (gaming-angle hooks)

| Element | Why it reads as playful |
|---|---|
| Hand-drawn outline icons | Not geometric Material Symbols — feel like a sketchbook |
| Pastel mint+pink palette | Avoids corporate banking blue; reads as friendly/cozy |
| Custom script wordmark | "Monefy" in handwritten cursive — personality-driven |
| Big circular +/− buttons | Tactile, button-mashing feel rather than discrete CTAs |
| Donut chart with icon spokes | Visually rich, almost game-board-like |
| Color-per-category | Reinforces collectible/picker feel like emoji selection |
| Light/airy mint background | Low-stress, lounge mood vs spreadsheet mood |

**No animations or mascots visible in static screenshots**, but the visual language is unusually warm for a finance app and supports a "gamified" or "casual" framing if a gaming-audience product is built on top.

## Screen density / form factor

- **Form factor:** phone, **portrait**
- **Aspect ratio:** ~9:20 (tall modern Android)
- **Density hint:** rendered at **~xxhdpi (480dpi) or xxxhdpi (640dpi)** based on stroke crispness and icon resolution
- **Status bar height:** standard ~24–28dp
- **Bottom gesture bar visible** — Android 10+

## Ambiguities

| ID | Question |
|---|---|
| S-1 | Точные hex-значения palette tokens определены с экранного семпла — APK colors.xml канонический, мои значения округлены до ближайших правдоподобных. |
| S-2 | Шрифт основного UI похож на Roboto Regular, но может быть Open Sans или системный fallback — без файлов шрифта точно не отличить. |
| S-3 | "Monefy" wordmark — точно кастомный (packaged asset), но конкретное имя гарнитуры не определено. |
| S-4 | Темная тема не наблюдается ни на одном из 10 скриншотов — нельзя утверждать, что её нет совсем; APK может содержать `values-night/colors.xml`. |
| S-5 | Calculator digits выглядят легче (Light/Thin) чем body text — может быть отдельный font weight или то же Roboto Regular в большем размере. |
| S-6 | Background и surface очень близки по оттенку (`#EAF6EC` vs `#F1F8F2`) — разница может быть JPEG-артефактом, в colors.xml возможно одно значение. |
| S-7 | Не наблюдалось ни одного диалога, snackbar или bottom-sheet — соответствующие радиусы/elevation выведены по аналогии. |
| S-8 | Category icon palette (purple shirt, blue house etc.) — это контентные цвета, скорее всего хардкод в иконках/ресурсах, а не theme tokens. |
