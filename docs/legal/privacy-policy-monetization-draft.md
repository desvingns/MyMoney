# Privacy-policy draft blocks — monetization (ADR-0010)

- Status: Block 4 (Advertising) applied 2026-08-16; Blocks 1–3 remain draft, not published
- Date: 2026-08-12
- Governing decision: [ADR-0010](../DECISIONS/ADR-0010-monetization.md)

Target files (both must change together, they are translations of one document):

- `app/src/main/assets/privacy_policy_en.html`
- `app/src/main/assets/privacy_policy_ru.html`

Both are also published to GitHub Pages by `.github/workflows/privacy-policy-pages.yml`, so a
change here is a public legal statement, not an in-app string edit.

## Sequencing rule

**A block is applied in the release that ships the behaviour it describes — never earlier.**
Declaring a collection that is not yet happening is the same class of error as omitting one that
is: both make the published policy false. The `Last updated` date (line 21 EN / line 21 RU) moves
to the date of whichever release applies a block.

| Block | Applied in epic | Describes |
|---|---|---|
| [Purchases](#block-1--purchases-google-play-billing) | `support-hub-tip` | Play Billing: subscription + coffee consumables |
| [Firebase](#block-2--firebase-services) | `support-hub-tip` | Remote Config (and Analytics, if added) |
| [Shared workspace wording](#block-3--shared-workspace-wording-fixes) | `plus-subscription-gating` | Drops "compatible builds" hedging once shared sync is on for everyone |
| [Advertising](#block-4--advertising-google-admob) | `support-rewarded-ads` (applied 2026-08-16) | AdMob rewarded ads, advertising ID, SSV |

---

## Block 1 — Purchases (Google Play Billing)

Epic: **`support-hub-tip`**. Insert as a new `<h3>` under "Information sent over the network",
after the Dropbox/Google Drive subsection.

```html
<h3>Purchases (Google Play Billing)</h3>
<p>Optional purchases — the Plus subscription and one-off support purchases — are processed
entirely by Google Play. MyMoney never sees or stores your card, bank or payment details. The app
receives from Google Play only the purchase state needed to grant what you bought (product
identifier, purchase token, and the resulting entitlement), and stores that entitlement against
your account so it applies across your devices. Refunds, cancellation and payment history are
handled in your Google Play account. Purchases are not available in every region; where Play
Billing is unavailable, every local capability of MyMoney continues to work.</p>
```

RU:

```html
<h3>Покупки (Google Play Billing)</h3>
<p>Необязательные покупки — подписка Plus и разовые покупки в поддержку — полностью
обрабатываются Google Play. MyMoney никогда не видит и не хранит данные вашей карты, банковского
счёта или платёжных реквизитов. Приложение получает от Google Play только состояние покупки,
необходимое для выдачи оплаченного (идентификатор товара, токен покупки и итоговое право
доступа), и сохраняет это право доступа за вашим аккаунтом, чтобы оно действовало на всех ваших
устройствах. Возвраты, отмена и история платежей управляются в вашем аккаунте Google Play.
Покупки доступны не во всех регионах; там, где Play Billing недоступен, все локальные
возможности MyMoney продолжают работать.</p>
```

---

## Block 2 — Firebase services

Epic: **`support-hub-tip`**. Replaces the "Services and feature flags" paragraph (EN line 55 /
RU line 55).

> ⚠️ **Verify before applying.** As of 2026-08-12 the build declares **only**
> `firebase-config-ktx` (`gradle/libs.versions.toml:135`, `core/sync/build.gradle.kts:92-93`).
> There is no `firebase-analytics` artifact, so no analytics SDK runs and nothing is collected —
> even though a `google-services.json` produced with Analytics enabled in the Firebase console
> contains an Analytics configuration block. `BuildConfig.HAS_FIREBASE`
> (`RemoteConfigRepositoryImpl.kt:16-19`) gates whether Firebase is touched at all; today it gates
> Remote Config only.
>
> Use **variant A** unless `firebase-analytics` has actually been added to the build by the time
> this block ships. Then, and only then, use **variant B**.

**Variant A — Remote Config only (matches the build as it stands):**

```html
<h3>Firebase services</h3>
<p>Builds configured with Firebase use Firebase Remote Config to deliver feature configuration
values to the app. Remote Config transmits configuration values and the technical app, device and
runtime context needed to deliver them; it does not receive your financial data. Firebase
Analytics is not included in the app. Dropbox, Google Drive and Supabase are included in the
release artifact, but they transmit data only after you connect and use the corresponding
feature.</p>
```

**Variant B — Remote Config + Analytics (only once `firebase-analytics` is on the classpath):**

```html
<h3>Firebase services</h3>
<p>Builds configured with Firebase use two Google Firebase services. Firebase Remote Config
delivers feature configuration values to the app, transmitting those values and the technical app,
device and runtime context needed to deliver them. Firebase Analytics records app usage events —
such as screens opened and features used — together with a Firebase installation identifier and
technical device context, and is used to understand how the app is used in aggregate. Neither
service receives your transactions, accounts, categories, notes or other financial records.
Google's own privacy policy and retention rules also apply. Dropbox, Google Drive and Supabase are
included in the release artifact, but they transmit data only after you connect and use the
corresponding feature.</p>
```

> Do **not** describe Remote Config as a kill switch for any feature. No such mechanism exists:
> `RemoteConfigRepositoryImpl.sharedSyncEnabled()` (line 55) reads only `BuildConfig` flags, and
> the `KEY_SHARED_SYNC` / `DEFAULT_SHARED_SYNC` constants — like the Dropbox and GDrive ones — are
> declared but never read. See ADR-0010 §D1.

RU (variant A):

```html
<h3>Сервисы Firebase</h3>
<p>Сборки, настроенные с Firebase, используют Firebase Remote Config для доставки в приложение
значений конфигурации функций. Remote Config передаёт значения конфигурации и технический контекст
приложения, устройства и среды выполнения, необходимый для их доставки; он не получает ваши
финансовые данные. Firebase Analytics в приложение не включён. Dropbox, Google Drive и Supabase
включены в release-артефакт, но передают данные только после подключения и использования
соответствующей функции.</p>
```

RU (variant B):

```html
<h3>Сервисы Firebase</h3>
<p>Сборки, настроенные с Firebase, используют два сервиса Google Firebase. Firebase Remote Config
доставляет в приложение значения конфигурации функций, передавая эти значения и технический
контекст приложения, устройства и среды выполнения, необходимый для их доставки. Firebase
Analytics записывает события использования приложения — например, открытые экраны и использованные
функции — вместе с идентификатором установки Firebase и техническим контекстом устройства, и
служит для понимания того, как приложение используется в совокупности. Ни один из этих сервисов не
получает ваши операции, счета, категории, заметки и другие финансовые записи. Также действуют
собственные политика конфиденциальности и правила хранения Google. Dropbox, Google Drive и Supabase
включены в release-артефакт, но передают данные только после подключения и использования
соответствующей функции.</p>
```

---

## Block 3 — Shared workspace wording fixes

Epic: **`plus-subscription-gating`** — apply in the same release that flips
`PLAY_RELEASE_SYNC_ENABLED` (ADR-0010 §D1).

Once shared sync is on by default, the "compatible builds" hedging is no longer accurate: the
feature is present for every user, gated by entitlement rather than by build. Line 49 EN already
uses the correct phrasing ("The release build includes Shared workspaces as an optional
feature"); the others must be brought in line.

| File / line | Current | Replace with |
|---|---|---|
| EN 33 | `…are stored in the encrypted secure store if that feature is enabled in a compatible build.` | `…are stored in the encrypted secure store when you sign in to a Shared workspace.` |
| EN 48 | `Compatible builds may include an optional Shared workspace feature. When that feature is enabled, users can sign in…` | `The release build includes an optional Shared workspace feature. Users can sign in…` |
| RU 33 | `…если эта функция включена в совместимой сборке.` | `…когда вы входите в общее рабочее пространство.` |
| RU 48 | `В совместимых сборках может быть доступна необязательная функция общих рабочих пространств. При её включении пользователи могут входить…` | `Release-сборка включает необязательную функцию общих рабочих пространств. Пользователи могут входить…` |

Also add to the Shared workspace subsection, in the same release:

```html
<p>Access to a Shared workspace requires an active Plus entitlement. The entitlement is recorded
on our Supabase backend against your Google account and Supabase user, so that it applies to your
devices; it is not stored per device.</p>
```

RU:

```html
<p>Для доступа к общему рабочему пространству требуется активное право доступа Plus. Право доступа
хранится в нашем бэкенде Supabase и привязано к вашему аккаунту Google и пользователю Supabase,
поэтому действует на ваших устройствах; оно не хранится отдельно для каждого устройства.</p>
```

---

## Block 4 — Advertising (Google AdMob)

Epic: **`support-rewarded-ads`**. Insert as a new `<h3>` under "Information sent over the
network". Do not apply before the Google Mobile Ads SDK is actually in the build — until then the
app collects no advertising ID.

> **Applied 2026-08-16** in the `support-rewarded-ads` release: both locales (app assets and
> GitHub Pages copies) carry this block verbatim and the `Last updated` date moved to
> 2026-08-16. `privacy-policy/app-ads.txt`
> (`google.com, pub-2270788427402644, DIRECT, f08c47fec0942fa0`) was added in the same commit and
> the Pages workflow now also copies it to the project-site root
> (`/MyMoney/app-ads.txt`). Residual manual step: the AdMob crawler fetches `app-ads.txt` from the
> domain root (`https://desvingns.github.io/app-ads.txt`), which is served by the separate
> `desvingns.github.io` Pages repository — that root copy is placed there manually and cannot be
> deployed from this repo.

```html
<h3>Advertising (Google AdMob)</h3>
<p>MyMoney shows <strong>rewarded ads only</strong> — an ad never appears unless you deliberately
start one to earn temporary Plus access. There are no banner, interstitial or app-open ads.
When you start a rewarded ad, the Google Mobile Ads SDK collects your device's advertising ID
(<code>com.google.android.gms.permission.AD_ID</code>) and technical device and request context,
and may use them to select and measure the ad. Google's own advertising policies apply. The
reward itself is confirmed to MyMoney's backend by Google through server-side verification;
the confirmation carries an opaque identifier tied to your account, not your financial records.
No transaction, account, category, note or other financial record from MyMoney is ever sent to
Google's advertising services. Ads are not served in every region; where they are unavailable,
the rest of the app is unaffected.</p>
```

RU:

```html
<h3>Реклама (Google AdMob)</h3>
<p>MyMoney показывает <strong>только награждаемую рекламу</strong> — ролик никогда не появляется
сам, вы запускаете его осознанно, чтобы получить временный доступ Plus. Баннерной, полноэкранной
и стартовой рекламы нет. Когда вы запускаете награждаемый ролик, SDK Google Mobile Ads собирает
рекламный идентификатор устройства (<code>com.google.android.gms.permission.AD_ID</code>), а также
технический контекст устройства и запроса, и может использовать их для подбора и измерения
рекламы. Действуют собственные рекламные политики Google. Само вознаграждение подтверждается
бэкенду MyMoney со стороны Google через серверную верификацию; подтверждение содержит непрозрачный
идентификатор, привязанный к вашему аккаунту, а не ваши финансовые записи. Никакие операции,
счета, категории, заметки и другие финансовые записи MyMoney никогда не передаются рекламным
сервисам Google. Реклама показывается не во всех регионах; там, где она недоступна, остальная
часть приложения работает без изменений.</p>
```

---

## Also required in the same releases

- **Play Data Safety form.** The current listing declares no ads and no purchases. It must be
  updated in the Play Console alongside Block 1 (purchases) and Block 4 (advertising ID) — the
  form is a separate declaration from this policy and is not covered by editing these HTML files.
- **Line 21 `Last updated`** in both files.
