# Supabase backend (shared workspaces)

Free-tier Supabase project, **EU / Ireland region**. Backs MyMoney's experimental Shared mode:
up to five Google-authenticated users collaborating on one financial workspace over TLS + RLS
(no end-to-end encryption in the MVP).

## What lives here

- `migrations/` — versioned, **schema-as-code** SQL. No secrets. Source of truth for the tables,
  row-level-security policies, and lifecycle RPCs.

The project URL and anon/public key are **not** committed. They live in the git-ignored
`local.properties` as `supabase.url` and `supabase.anonKey`, injected into `:core:network`
`BuildConfig` at build time (mirrors the existing `dropbox.appKey` seam in `:core:sync`).

## Applying a migration (Dashboard SQL Editor)

There is no Supabase CLI, Docker, service-role key, or DB password on the dev machine, so
migrations are applied **manually**:

1. Open the Supabase Dashboard for the EU/Ireland project.
2. Go to **SQL Editor** → **New query**.
3. Paste the full contents of the next unapplied file in `migrations/` (start with
   `0001_shared_workspaces.sql`).
4. **Run**. Verify success (no red errors) in the results pane.
5. Optionally confirm in **Table Editor** that `workspaces`, `workspace_members`, and
   `workspace_invites` exist and that RLS is enabled (the shield icon) on each.

Apply files in ascending numeric order and never edit an already-applied file — add a new,
higher-numbered migration instead.

## Auth (Google, no Drive)

Google sign-in goes through **Supabase Auth** with scope `openid email profile` only — the app
requests **no Google Drive permission**. Enable the Google provider and its redirect URL on the
Dashboard (**Authentication → Providers → Google**); the Android side only consumes the resulting
session. A user's stable identity is the Supabase user ID; email is display-only.

## Schema summary (`0001`)

- `workspaces` — `id`, `name`, `owner_id → auth.users`, `created_at`.
- `workspace_members` — `workspace_id`, `user_id`, `role` (`owner`/`editor`), `joined_at`,
  `active`. A partial unique index enforces **one active workspace per user**.
- `workspace_invites` — `token_hash` (SHA-256 hex; the plaintext token is never stored),
  `role` (editor-only), `expires_at` (default `now() + 24h`), `revoked_at`, `consumed_at`.

All writes go through `SECURITY DEFINER` RPCs; tables expose **only** SELECT policies scoped to
active members, so a non-member cannot read another workspace via the REST/PostgREST API.

## Plus entitlement and workspace billing

`workspaces.payer_user_id` is the account whose entitlement controls the entire workspace. It is
independent from `owner_id` so a future payment handoff only changes `payer_user_id`. The scheduled
workspace billing refresh and the Google Play RTDN handler are the only paths that update the
denormalized `billing_state`: `active` permits reads and writes, `grace` permits reads only, and
`expired` blocks shared RPCs.

Clients read their own effective entitlement only through `get_my_entitlement()`; direct access to
`entitlements` is intentionally unavailable. Run the RPC in an authenticated client session:

```sql
select public.get_my_entitlement();
```

To grant an indefinite Plus entitlement to a whitelist account, run this as an administrator:

```sql
insert into public.entitlements (user_id, provider)
values (:user_id, 'whitelist');
```

To revoke it, set `revoked_at` on that entitlement record:

```sql
update public.entitlements
set revoked_at = now()
where user_id = :user_id
  and provider = 'whitelist'
  and revoked_at is null;
```

### RPCs

| Function | Purpose |
|---|---|
| `create_workspace(name)` | Create a workspace and its owner membership (fails if the caller already has an active workspace). |
| `create_invite(workspace_id, token_hash)` | Member creates a single-use, 24h invite. Only the SHA-256 hash is stored. |
| `join_workspace(token)` | Hash the plaintext token server-side, validate (not revoked / used / expired), enforce the five-member cap under a row lock, add the caller as an editor, and mark the invite consumed. |
| `revoke_invite(invite_id)` | Member revokes an unused invite. |
| `leave_workspace(workspace_id)` | Editor leaves; an owner's departure transfers ownership to the earliest-joined active editor. A **sole owner cannot leave** and must delete instead. |
| `delete_workspace(workspace_id)` | Owner-only; cascades to members and invites. |

## Cost guardrails

Free tier only. No paid add-ons, no server-side scheduled backups. Local safety backups and
manual export remain the user's recovery path (handled in later Shared-mode SPECs).
