# Shared sync experimental deployment and E2E runbook

Shared sync is an internal/debug experiment. It is not eligible for public rollout until every
check in this runbook has recorded evidence. Realtime only prompts a durable pull; the local
outbox, cursor, scheduled worker, manual sync button, and internal safety backups remain the
recovery path.

## Deploy

1. Apply `supabase/migrations/0001_shared_workspaces.sql`,
   `0002_shared_operations.sql`, and `0003_shared_realtime_security.sql` in order through the
   Supabase SQL Editor for the intended project.
2. Confirm that the `operations` table appears in the `supabase_realtime` publication.
3. Set `supabase.url`, `supabase.anonKey`, and `supabase.googleWebClientId` only in local or CI
   secret configuration. Do not add them to versioned files.
4. Enable the feature only in a debug/internal build with `-Psync.forceEnabled=true`, or set the
   `shared_sync_enabled` Remote Config value for that debug/internal audience. Its bundled default
   is `false`; non-debug release/public builds hard-disable Shared sync.

## Security verification

Use three distinct Google/Supabase users: owner A, member B, and non-member C. Record the user
identifiers, timestamp, request method, HTTP status, and response body redacted of tokens.

1. A creates a workspace and joins B with a one-time invite. C receives no invite.
2. With C's bearer token, attempt `pull_operations`, `push_operation`,
   `list_pending_conflicts`, and `resolve_conflict` for A's workspace. Each must fail with an
   authorization error and create no row.
3. With C's bearer token, attempt direct REST reads and writes for `operations`, `conflicts`,
   `workspaces`, and `workspace_members` scoped to A's workspace. Reads must return no rows and
   writes must be denied.
4. Subscribe C to `realtime:public:operations` with A's workspace filter. It must receive no
   join authorization and no operation notifications. A and B may each subscribe and receive
   only their workspace's notifications.
5. With A or B, direct `operations` REST reads must not expose `author_id`; conflict attribution
   is available only through `list_pending_conflicts` in the conflict UI.

## Two-user recovery E2E

Run the flow on two independent Pixel 5 API 34 emulator instances or physical devices. Capture a
screen recording from each device and keep the recordings with the dated evidence record.

1. A creates a workspace, creates an invite, and B joins once with each import choice on separate
   disposable workspaces. Verify the internal safety backup before each adoption.
2. While both Cloud sync screens are foregrounded, create recognizable Account, Category, and
   Transaction changes on A, then B. Realtime should show connected and trigger convergence in
   both directions without using its message payload as data.
3. Disable network, create local changes, restore network, and confirm cursor-based manual or
   scheduled sync drains the durable outbox exactly once. Repeat after killing and relaunching
   each process, and after an intentional Realtime disconnect/reconnect.
4. Exercise update/update, update/delete, and delete/update conflicts while an unrelated
   operation continues to converge. Resolve each conflict and record the selected winner.
5. Wake a free-tier sleeping project by opening Shared sync or pressing Sync now. Record the
   server-starting status, the bounded retry attempts, the eventual cursor recovery, and that no
   local outbox operation is removed before a successful push.
6. Record invite expiry/replay/revoke, five-member limit, owner transfer, final-owner deletion,
   removal, backup restore, leave, and manual sync after every recovery path.

## Evidence record

For each execution, store the date, build commit, Android build version, Supabase project region,
users A/B/C, device serials, recordings, redacted request transcript, cursor/outbox observations,
and pass/fail result for every step above. A failed or incomplete record blocks public rollout.
