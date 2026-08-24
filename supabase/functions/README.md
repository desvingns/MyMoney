# Monetization Edge Functions

## Functions

| Function | Caller | Purpose |
|---|---|---|
| `redeem-activation-code` | Authenticated Supabase user | Redeems one activation code through the protected RPC. |
| `create-ad-reward-token` | Authenticated Supabase user | Creates short-lived signed `custom_data` for a rewarded-ad request. |
| `admob-ssv` | AdMob only | Verifies the signed callback and records one verified reward. |
| `google-play-rtdn` | Google Play Pub/Sub only | Authenticates the webhook token, deduplicates RTDN messages, verifies purchase state through the Play Developer API, and reconciles already-bound Plus subscriptions. |
| `bind-google-play-purchase` | Authenticated Supabase user | Verifies a subscription purchase token through the Play Developer API and binds the resulting Plus entitlement to the JWT user. |

`admob-ssv` intentionally has JWT verification disabled because AdMob does not send a Supabase JWT. Its handler verifies the ECDSA callback signature against Google's rotating public keys before writing anything.

## Required secrets

Configure these in Supabase Dashboard → Edge Functions → Secrets. Do not commit their values:

- `AD_REWARD_TOKEN_SECRET` — random secret of at least 32 bytes.
- `AD_REWARD_TOKEN_TTL_SECONDS` — optional, defaults to `600` and must be between `60` and `3600`.
- `ADMOB_REWARDED_AD_UNIT_ID` — optional until the AdMob rewarded ad unit exists; when set, callbacks for another ad unit are rejected.
- `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` — the complete JSON key for the service account invited in Play Console.
- `RTDN_WEBHOOK_TOKEN` — legacy rollout secret; no longer required after OIDC verification and should be removed from Supabase secrets after the token-free delivery test.
- `RTDN_PUSH_AUDIENCE` — optional OIDC audience, defaults to the RTDN function URL without query parameters.
- `RTDN_PUSH_SERVICE_ACCOUNT_EMAIL` — optional expected OIDC email, defaults to `mymoney-pubsub-push@my-money-502807.iam.gserviceaccount.com`.
- `GOOGLE_PLAY_PACKAGE_NAME` — optional, defaults to `com.kshavrin.mymoney`.
- `GOOGLE_PLAY_PLUS_PRODUCT_IDS` — optional comma-separated subscription product IDs; defaults to `plus_monthly,plus_yearly`.

The Supabase runtime supplies the project URL, public key, and service-role/secret key used by the functions.

## Reward identity flow

1. The authenticated client calls `create-ad-reward-token`.
2. The client passes the returned `custom_data` to the rewarded-ad request.
3. AdMob includes `custom_data`, `transaction_id`, and the other signed parameters in the SSV callback.
4. `admob-ssv` verifies the callback and the signed custom data, then inserts into `ad_rewards`.
5. The companion migration atomically groups five unassigned verified rewards into a 24-hour `admob_reward` entitlement without extending an active window.

The current Android production code is not changed by this server setup; the client call in step 1 and the AdMob custom-data assignment remain a later, separately authorized integration step.

## Google Play identity boundary

RTDN contains a purchase token, not a Supabase user ID. The function therefore never invents an owner. It reconciles a purchase only when a matching `entitlements.provider_reference` was created by a future authenticated purchase-binding endpoint; otherwise it records the verified event as `awaiting_purchase_binding`.

`bind-google-play-purchase` accepts `{ "purchase_token": "..." }` with a Supabase access token. It does not accept a client-supplied `user_id`; the authenticated JWT is the owner boundary.
