package com.kshavrin.mymoney.core.sync

import com.kshavrin.mymoney.core.domain.sync.Operation

/**
 * Append-only operation journal synchronization in each provider's private application storage.
 *
 * [previewMigration] reads a target without changing the local database. [applyMigration] is only
 * called after the user has created a local safety backup and explicitly selected a collision policy.
 */
interface JournalSync {
    suspend fun push()

    suspend fun pull()

    suspend fun syncNow()

    suspend fun previewMigration(target: SyncTarget): Result<JournalMigrationPreview> =
        Result.failure(UnsupportedOperationException("Migration preview is unavailable"))

    suspend fun applyMigration(
        preview: JournalMigrationPreview,
        resolution: MigrationResolution,
    ): Result<Unit> = Result.failure(UnsupportedOperationException("Migration is unavailable"))
}

data class JournalMigrationPreview(
    val target: SyncTarget,
    val remoteOperations: List<Operation>,
    val conflictingEntityUuids: Set<String>,
)

enum class MigrationResolution {
    KeepLocal,
    UseTarget,
}
