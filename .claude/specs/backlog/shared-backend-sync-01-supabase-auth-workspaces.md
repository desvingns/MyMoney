# Shared backend foundation: Supabase Auth, workspaces, membership, and invites
Epic: shared-backend-sync
Order: 01 of 04
Status: backlog
Depends-on: review-2026-07-22-cloud-creds-setup
Date: 2026-07-22

## SPEC
=== SPEC ===
TASK: feature
WHAT: Provision the free-tier Frankfurt Supabase foundation for Google-authenticated shared workspaces, with owner/editor membership, a five-member cap, secure one-time invitations, and workspace lifecycle RPCs protected by RLS.
LAYERS: [data]
CHANGED_HINT: Supabase migrations/config/docs, :core:network Shared auth/API contracts, secret injection seams
TEST_TYPES: unit [integration]
CONSTRAINTS: Google Sign-In uses Supabase Auth without Drive scope; tables include workspaces, workspace_members, and hashed workspace_invites; invites expire after 24 hours and are single-use/revocable; roles are owner/editor; maximum five active members; one active workspace per user in MVP; owner transfer selects the earliest-joined active editor; the last owner cannot leave and must explicitly delete the workspace; TLS+RLS only, no E2EE; FREE TIER ONLY; no secrets committed.
=== END SPEC ===

## Acceptance

- RLS and RPC tests prove members can access only their own workspace and non-members cannot bypass it.
- Invite create/join/replay/expiry/revoke and five-member limit are deterministic and race-safe.
- Owner departure transfers ownership to the earliest-joined active editor.
- A sole owner receives a normal destructive confirmation and must delete rather than leave.
- Google identity is represented by stable Supabase user ID; email is display-only.

## Implementation links

- commit: pending
- files: pending
