# Shared backend sync epic
Epic: shared-backend-sync
Order: 00 of 04
Status: backlog
Depends-on: review-2026-07-22-cloud-creds-setup
Date: 2026-07-22

## Goal

Add an experimental Shared mode backed by Supabase/Postgres so up to five different Google users can
collaborate on one financial workspace. Shared is a third mutually exclusive sync mode; it does not
reuse Google Drive sharing and does not replace private Dropbox or Google Drive sync.

## Ordered SPECs

1. `shared-backend-sync-01-supabase-auth-workspaces` — Auth, schema, RLS, membership, and invites.
2. `shared-backend-sync-02-operation-api-and-conflicts` — immutable operation API, cursors, and conflicts.
3. `shared-backend-sync-03-android-shared-mode` — Android mode, join/import, safety backups, and lifecycle.
4. `shared-backend-sync-04-realtime-hardening-e2e` — Realtime, background fallback, security, and live E2E.

## Cross-cutting decisions

- Supabase free tier in the Frankfurt region; TLS plus RLS for MVP, no end-to-end encryption.
- Google identity through Supabase Auth (`openid email profile`), with no Drive permission.
- One active workspace and one active local database; no workspace/profile column on every domain row.
- Shared operations cover Transaction, Account, and Category. Device/security preferences stay local.
- Owner/editor roles, maximum five active members, hashed one-time invites valid for 24 hours.
- Realtime in foreground; WorkManager/manual sync and an offline queue remain authoritative fallbacks.
- Manual conflict queue is Shared-only. Any active member may resolve a conflict by choosing either
  participant version; author attribution is shown only in conflict UI.
- Free-tier MVP accepts no automatic server backup. Internal local safety backups and manual export are
  required. Public rollout stays disabled behind an experimental/internal gate.
- Former shared Google Drive files and the archived shared-folder SPEC are not migration sources.

## Epic acceptance

- Two different Google users in one workspace converge on the same financial data; a non-member cannot
  read it through direct API calls.
- Invite expiry/replay/revoke, five-member limit, owner transfer, final-owner deletion, removal access,
  offline replay, idempotency, cursor recovery, Realtime reconnect, and sleeping-project recovery pass.
- Update/update, update/delete, and delete/update conflicts do not block unrelated operations.
- Join with import and join without import both preserve a recoverable local safety backup.
- All four child SPECs are in `done/` with commits/files and the union satisfies these cross-cutting rules.

## Implementation links

- commit: pending
- files: pending
