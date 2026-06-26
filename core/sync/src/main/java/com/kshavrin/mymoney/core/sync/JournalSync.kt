package com.kshavrin.mymoney.core.sync

/**
 * Append-only journal sync (operations-journal-sync epic). Replaces [SnapshotSync] on the autosync
 * path (D6): instead of pushing/pulling whole-DB snapshots with a conflict prompt, every device
 * writes its own `ops-<deviceId>.jsonl` journal into a shared Drive folder and merges peers' journals
 * via last-writer-wins (LWW, D5) — no conflict dialog.
 *
 * - [push] uploads the local device's unsynced operations.
 * - [pull] downloads peers' journals and applies their operations locally.
 * - [syncNow] runs bootstrap (if needed), then [pull] followed by [push].
 *
 * When the shared folder is not configured (D10) every call is a no-op.
 */
interface JournalSync {
    suspend fun push()

    suspend fun pull()

    suspend fun syncNow()
}
