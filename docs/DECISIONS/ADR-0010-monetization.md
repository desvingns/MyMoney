# ADR-0010: Monetization — Plus subscription, coffee consumables, rewarded ads

- Status: Accepted
- Date: 2026-08-12
- Amended by: ADR-0011
- Supersedes: decision Q-B3 ("free, no IAP, no ads") recorded in `TDD/MyMoney/MyMoney_TDD.md`
  lines 24, 2030, 2036, 2110, 2382, 2772

## Context

Q-B3 was taken during the initial spec pass (`/app-tdd-creator`, v1.0 of the TDD): MyMoney was to
ship stripped of the original Monefy monetization — no Google Play Billing, no ads, no Premium
tier. The TDD kickoff list (§14.4, line 2772) explicitly asked to confirm this was a launch
decision and not a phase-1 simplification.

Two things changed since:

1. **Server-side cost appeared.** The shared-workspace sync epic landed on Supabase
   (`shared-backend-sync` SPECs 01–04, closed 2026-08-06). Unlike Dropbox/Google Drive snapshot
   backup — which runs entirely on the *user's own* storage quota — a shared workspace consumes
   the project's own Supabase project (`shwzjlkhlpgbmzgnxhxi`): rows, Realtime connections,
   egress. A permanently free shared backend does not scale past a handful of users on the free
   tier.
2. **Shared sync is release-ready.** It currently sits behind an experimental gate:
   `RemoteConfigRepositoryImpl.sharedSyncEnabled()`
   (`core/sync/src/main/java/com/kshavrin/mymoney/core/sync/remoteconfig/RemoteConfigRepositoryImpl.kt:55`)
   returns `BuildConfig.PLAY_INTERNAL_SYNC_ENABLED || BuildConfig.PLAY_RELEASE_SYNC_ENABLED ||
   syncForced()`, and both build-config flags come from Gradle properties
   (`sync.playInternalEnabled`, `sync.playReleaseEnabled`, `app/build.gradle.kts:69-73`) that
   default to `false`. A `shared_sync_enabled` entry also exists in
   `core/sync/src/main/res/xml/remote_config_defaults.xml:20`, but nothing reads it — the gate is
   build-time only.

This ADR reverses Q-B3 and defines the monetization model, its regional limits, and the build
configuration that carries it.

## Decision

### D1 — Shared sync ships on by default in release, but only once it is gated

`sync.playReleaseEnabled` becomes the release default (`true`), so `sharedSyncEnabled()` is `true`
in production builds. Shared sync stops being an experiment and becomes the flagship paid
capability.

**The flip is performed in the `plus-subscription-gating` epic, not now.** Turning
`PLAY_RELEASE_SYNC_ENABLED` on before the entitlement check exists would open a window in which
every user gets the Supabase shared workspace for free and ungated — exactly the cost this ADR
sets out to control. The order is: entitlement lands first, the flag flips second, in the same
epic.

There is **no remote kill switch** for this. `sharedSyncEnabled()` reads only `BuildConfig` flags;
the `KEY_SHARED_SYNC` / `DEFAULT_SHARED_SYNC` constants and the `shared_sync_enabled` entry in
`core/sync/src/main/res/xml/remote_config_defaults.xml` are declared but never read (the same is
true of the Dropbox and GDrive keys). Rolling shared sync back therefore requires a new release.
Building an actual remote kill switch is separate work and is not decided by this ADR.

### D2 — Two tiers

| Capability | Free | Plus |
|---|---|---|
| All local bookkeeping (accounts, categories, transactions, budgets, recurring, charts, CSV) | yes | yes |
| Private backup to the user's own Dropbox / Google Drive | yes | yes |
| Shared workspace on Supabase (multi-user / multi-device ledger) | no | yes |
| Backup version history (multiple restorable snapshots) | no | yes |

Free is a complete money-tracking app, not a crippled trial. Nothing that works offline today
moves behind the paywall — Plus only sells capabilities that cost the project money to run
(Supabase) or that did not exist before (version history).

### D3 — Plus pricing

| SKU | Type | Price | Trial |
|---|---|---|---|
| `plus_monthly` | subscription | €1.99 / month | none |
| `plus_yearly` | subscription | €12.99 / year | 7 days |

The free trial is attached to the **annual** base plan only. Monthly has no trial — a 7-day trial
on a €1.99 plan is mostly a churn generator, and Google Play's offer model lets a trial be scoped
to a single base plan.

### D4 — "Coffee" consumables

| SKU | Type | Price | Repeatable |
|---|---|---|---|
| `coffee_small` | consumable | €1 | yes, unlimited |
| `coffee_large` | consumable | €5 | yes, unlimited |

Pure support purchases. They are **consumed immediately** on acknowledgement so they can be bought
again. They grant no entitlement — buying coffee does not grant Plus, and Plus does not hide the
coffee button.

The first acknowledged purchase of any coffee SKU permanently awards a cosmetic Supporter badge.
The badge is visible only to its owner in their app; it is not an entitlement, does not unlock
Shared workspace, and has no effect on Plus. Plus neither grants nor hides the badge.

### D5 — Rewarded ads → temporary Plus

Watching **5 rewarded ads grants 24 hours of Plus**, counted from the moment the fifth reward is
verified. The counter and the resulting entitlement window are granted **only** through **AdMob
Server-Side Verification (SSV)** — the client never mints entitlement locally.

Rationale: the reward unlocks a *server* resource (the Supabase shared workspace), so the grant
has to be a server fact. A client-side counter in DataStore would be trivially forgeable by clock
change or local edit, and would hand out real backend capacity for free.

Rewarded ads are the only ad format. No banners, no interstitials, no app-open ads — the app never
shows an ad the user did not deliberately start.

### D6 — Single build, no product flavours

No `free`/`paid` product flavours are introduced; `app/build.gradle.kts` keeps
`Product flavours = none` (TDD §8.1, line 2014). Billing and ads are switched off in `debug`
through a `BuildConfig` flag, in the same shape as the existing sync flags: a Gradle property
resolved in `app/build.gradle.kts` and exposed via `buildConfigField`.

Rationale: flavours multiply the build matrix (variants, test source sets, CI tasks, KSP runs)
for a boolean. The existing `PLAY_INTERNAL_SYNC_ENABLED` / `PLAY_RELEASE_SYNC_ENABLED` pattern
already proved a single-build, property-driven gate works here.

### D7 — New modules

| Module | Contents |
|---|---|
| `:core:billing` | Google Play Billing client wrapper, SKU catalogue, purchase/acknowledge/consume flow, entitlement repository |
| `:core:ads` | AdMob SDK wrapper, rewarded-ad loading and presentation, SSV callback contract |
| `:feature:support` | Paywall / Plus screen, coffee purchase UI, rewarded-ad entry point, entitlement state surface |

Dependency direction is unchanged: `:feature:support` → `:core:*`, never `:feature:*` →
`:feature:*`. Entitlement is read through a `:core:domain` repository interface so that
`:feature:cloudsync` can gate the shared card without depending on `:core:billing` directly.

## Regional constraints (Russia) and the accepted consequence

These are the known, verified limits — recorded here so they are never re-litigated as bugs.

| Constraint | Effect |
|---|---|
| Google Play Billing is not available to users in Russia | No subscription and no consumable purchase can complete for them |
| AdMob does not serve ads to users in Russia | The rewarded-ad path yields no fill, so temporary Plus cannot be earned either |
| RuStore is not a planned distribution channel | Since **2026-02-01** RuStore's built-in monetization requires a Russian sole proprietorship (ИП) or legal entity, which the developer does not have |

**Accepted consequence.** For users in Russia, monetization and the shared workspace are
effectively unavailable. This is accepted, not worked around:

- The app stays **fully functional locally** for them — every Free capability in D2 works,
  including private Dropbox / Google Drive backup on their own storage.
- The paywall must degrade honestly: where billing is unavailable, the Plus surface explains that
  purchases are not available in the region instead of showing a dead button or an error toast.
- **Testers receive Plus through a whitelist** — a server-side entitlement grant keyed to the
  account, independent of Billing. This is the same mechanism SSV writes to (D5), so there is one
  entitlement source of truth, not two.
- No RuStore build, no alternative payment rail, no crypto, no external-web-checkout workaround.
  Attempting an off-Play payment rail for Play-distributed users would breach Play policy.

## Rejected alternatives

- **Keep Q-B3 (stay fully free).** Rejected: the Supabase shared workspace is a recurring,
  usage-proportional cost carried by the developer. A free-forever shared backend caps the user
  base at whatever the free tier absorbs, and the project's standing constraint is free-tier-only
  external services.
- **Paywall existing local features (budgets, recurring, CSV, dark theme — Monefy's own Premium
  split).** Rejected: those features already shipped as free, and clawing them back punishes
  existing users for upgrading. Plus only sells what costs money to run or is genuinely new.
- **One-time lifetime purchase instead of a subscription.** Rejected: the cost being covered is
  recurring (Supabase), so a one-time price either underprices long-lived users or overprices
  short-lived ones. The €1/€5 consumables already serve the "pay once, no strings" impulse.
- **Client-side rewarded-ad accounting** (count views in DataStore, grant Plus locally).
  Rejected: forgeable, and it would hand out real backend capacity on an unverified client claim.
  SSV is the only grant path (D5).
- **Separate `free` / `paid` product flavours.** Rejected under D6 — build-matrix cost for a
  boolean.
- **Ship a RuStore build to reach Russian users.** Rejected: built-in monetization there has
  required a Russian ИП/legal entity since 2026-02-01, which does not exist for this project; a
  monetization-less RuStore build would be a second distribution channel to maintain for no
  revenue and no shared sync.
- **Banner or interstitial ads.** Rejected: they degrade a screen the user is trying to read.
  Rewarded-only keeps every ad opt-in.

## Consequences

- `com.android.vending.BILLING` returns to the manifest. The Play Services Ads SDK additionally
  merges `com.google.android.gms.permission.AD_ID`, so the TDD §8.2 permission table and its
  "final count: 4" line (lines 2024–2040) both change.
- TDD §11 (line 2382 ff.) currently lists Premium-related string keys as DROP because of Q-B3.
  Those keys are back in scope in spirit — the paywall needs its own EN/RU strings — though not
  necessarily under Monefy's original key names.
- TDD §14.4 (line 2772) carries "No Premium IAP" as a kickoff talking point; it is now false and
  must be rewritten to point at this ADR.
- An SSV endpoint is required. The natural host is a Supabase Edge Function in the existing
  project, writing the entitlement grant to the same table the whitelist writes to.
- Entitlement becomes a server-authoritative concept. `:feature:cloudsync` must gate the Shared
  card on entitlement in addition to the existing `sharedSyncEnabled()` remote-config check —
  the two are independent (the flag says "the feature exists in this build", entitlement says
  "this user may use it").
- Play Console work is a prerequisite for any implementation SPEC: create the two subscription
  base plans with the annual 7-day offer, the two consumables, and configure the AdMob app +
  rewarded ad unit with the SSV callback URL.
- Privacy policy and the Play Data Safety form need updating — AdMob collects an advertising ID,
  which the current "no ads" listing does not declare.
- Flipping `sync.playReleaseEnabled` to a release default (D1) exposes the shared workspace to
  every user at once, so it must not land before the entitlement gate in `plus-subscription-gating`.
  Rollback is a new release, not a remote toggle.

## Resolved during review (2026-08-12)

- **SSV host.** The AdMob SSV callback is a **Supabase Edge Function** in the existing project
  `shwzjlkhlpgbmzgnxhxi`. It must be deployed with JWT verification disabled (AdMob calls it
  unauthenticated) and verify the AdMob signature itself against Google's published SSV keys
  before writing any grant. It writes to the same entitlement record the tester whitelist uses.
- **Entitlement identity.** Entitlement is keyed to the **Google account and/or the Supabase
  user** — one record linking both identities, not two parallel sources. Play attributes a
  purchase to the Google account; the Supabase user is what the shared workspace authorizes.
  A device is never the unit of entitlement.
- **Rewarded window does not stack.** Watching another 5 ads during an active 24-hour window does
  not extend it to 48 h. The grant sets an expiry of `now + 24 h`; it never adds to an existing
  one.
- **D1 rollout is all-at-once and accepted.** The release that flips the flag opens the shared
  workspace to every user simultaneously. There is no percentage rollout and no remote kill
  switch; rollback means shipping another release. (superseded by ADR-0011 D1 — a remote kill
  switch now exists for shared-sync specifically)
- **Privacy policy must be corrected.** `app/src/main/assets/privacy_policy_{en,ru}.html` (also
  published to GitHub Pages) currently describes an app with no ads and no purchases. It must
  declare the advertising ID collected by the Play Services Ads SDK, the rewarded-ad flow, and
  Play Billing purchase handling — shipped in the *same* release as the SDK integration, never
  before it.

## Open items (not decided here)

- The exact shape of the entitlement record (a new Supabase table with RLS, or an extension of
  the existing profile row) and how the Google-account ↔ Supabase-user link is stored.

## Amended 2026-08-12

This amendment records the [support-hub-tip](../../.claude/specs/backlog/support-hub-tip-00-overview.md) decision that the Supporter badge is a coffee-purchase cosmetic, not a Plus capability.
