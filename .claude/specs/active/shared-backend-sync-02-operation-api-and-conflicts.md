# Shared backend journal API, cursors, and conflict queue
Epic: shared-backend-sync
Order: 02 of 04
Status: active
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

- commit: pending (see git log)
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
