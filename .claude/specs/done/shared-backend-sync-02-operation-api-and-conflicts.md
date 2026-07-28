# Shared backend journal API, cursors, and conflict queue
Epic: shared-backend-sync
Order: 02 of 04
Status: done
Depends-on: shared-backend-sync-01-supabase-auth-workspaces
Date: 2026-07-22

## SPEC
=== SPEC ===
TASK: feature
WHAT: Add a workspace-scoped immutable operation API with idempotent pushes, ordered cursors, tombstones, and non-blocking manual conflicts for shared transactions, accounts, and categories.
LAYERS: [domain] [data]
CHANGED_HINT: Supabase operations/conflicts migrations and RPCs, shared journal DTOs/repository, sync protocol tests
TEST_TYPES: unit [integration]
CONSTRAINTS: Operation identity is unique per workspace; every accepted operation receives server sequence, base revision, author, device, payload/tombstone, and entity identity; concurrent edits to the same entity create a conflict while unrelated operations continue; any active member can resolve by choosing either stored participant version; resolution appends a superseding operation; author attribution is exposed only to conflict UI; removed members immediately lose API and Realtime access; no E2EE in MVP.
=== END SPEC ===

## Acceptance

- Retried pushes are idempotent and cursor paging returns every accepted operation exactly once.
- Offline replay and out-of-order delivery converge without overwriting unresolved conflicts.
- Update/update, update/delete, and delete/update conflicts are stored and resolvable.
- Resolution is membership-authorized, auditable, and does not mutate historical operations.
- RLS blocks cross-workspace operations/conflicts even with guessed identifiers.

## Implementation links

- commits: `82f8d6f2` (feat), `f132bdc6` + `8b3e0a78` (semantic-review blocker/warning fixes), `c81a5f11` (tests) — pushed to `main`.
- files:
  - supabase/migrations/0002_shared_operations.sql
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/sync/SharedOperation.kt
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/sync/SharedConflict.kt
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/repository/SharedJournalRepository.kt
  - core/network/src/main/java/com/kshavrin/mymoney/core/network/shared/SharedOperationDto.kt
  - core/network/src/main/java/com/kshavrin/mymoney/core/network/shared/SharedConflictDto.kt
  - core/network/src/main/java/com/kshavrin/mymoney/core/network/shared/SharedJournalRpc.kt
  - core/network/src/main/java/com/kshavrin/mymoney/core/network/shared/SupabaseSharedJournalApi.kt
  - core/network/build.gradle.kts (added :core:domain dependency)
  - core/network/src/test/java/com/kshavrin/mymoney/core/network/shared/SupabaseSharedJournalApiTest.kt
  - core/network/src/test/java/com/kshavrin/mymoney/core/network/shared/SharedOperationDtoTest.kt
  - core/network/src/test/java/com/kshavrin/mymoney/core/network/shared/SharedConflictDtoTest.kt

## Deferred hardening (flagged by independent critic, non-blocking)

- Default Supabase table grants (`GRANT ALL ... TO authenticated`) let a client SELECT `operations`/`conflicts`
  directly via PostgREST, bypassing `pull_operations`'s column allowlist and exposing `author_id`. RLS restricts
  *rows*, not *columns*. Fix: revoke default table SELECT for `authenticated` and force reads through the
  SECURITY DEFINER RPCs (or a column-restricted view). Candidate scope: SPEC 04 (realtime-hardening-e2e).
- `resolve_conflict`'s `base_sequence` for the superseding op is computed via a non-atomic `MAX(server_sequence)`
  scan instead of reusing the already-locked winner row's sequence — metadata staleness only, cursor ordering
  (IDENTITY-based) is unaffected.
- Open question: cursor pagination ("exactly once") uses a raw IDENTITY sequence with no safe-visibility
  watermark; a narrow commit-ordering race could let a client's cursor skip an operation that commits after a
  higher-sequence one it already pulled. Candidate scope: SPEC 04.
- Open question: `resolve_conflict` retries are idempotent by resolution key only — a retry with a different
  `p_winner_operation_id` silently returns the original resolution rather than validating winner consistency.
