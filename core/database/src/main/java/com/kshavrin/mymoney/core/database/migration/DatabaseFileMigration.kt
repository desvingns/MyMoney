package com.kshavrin.mymoney.core.database.migration

import com.kshavrin.mymoney.core.common.database.DatabaseFileNames
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object DatabaseFileMigration {
    private const val RENAME_MARKER_NAME = "mymoney.db.rename-in-progress"

    @Synchronized
    fun migrate(databaseDirectory: File) {
        val legacyDatabase = File(databaseDirectory, DatabaseFileNames.LEGACY_DATABASE_NAME)
        val database = File(databaseDirectory, DatabaseFileNames.DATABASE_NAME)
        val marker = File(databaseDirectory, RENAME_MARKER_NAME)

        if (!marker.exists()) {
            when {
                legacyDatabase.exists() -> startMigration(legacyDatabase, database, marker)
                hasLegacySidecars(databaseDirectory) ->
                    error("Legacy database sidecars exist without a legacy database file")
                !database.exists() && hasNeutralSidecars(databaseDirectory) ->
                    error("Neutral database sidecars exist without a neutral database file")
                else -> return
            }
        } else {
            check(marker.isFile) { "Database rename marker is not a file" }
        }

        resumeMigration(legacyDatabase, database, marker)
    }

    private fun startMigration(
        legacyDatabase: File,
        database: File,
        marker: File,
    ) {
        check(legacyDatabase.isFile) { "Legacy database is not a file" }
        check(!database.exists()) { "Both legacy and neutral database files exist" }
        DatabaseFileNames.sidecarSuffixes.forEach { suffix ->
            val legacySidecar = File(legacyDatabase.parentFile, legacyDatabase.name + suffix)
            val sidecar = File(database.parentFile, database.name + suffix)
            check(legacySidecar.isFile || !legacySidecar.exists()) { "Legacy database sidecar is not a file" }
            check(!sidecar.exists()) { "Legacy and neutral database sidecars both exist" }
        }
        Files.createFile(marker.toPath())
    }

    private fun resumeMigration(
        legacyDatabase: File,
        database: File,
        marker: File,
    ) {
        val legacyExists = legacyDatabase.isFile
        val databaseExists = database.isFile
        check(legacyExists.xor(databaseExists)) { "Database rename state is ambiguous" }

        if (legacyExists) {
            DatabaseFileNames.sidecarSuffixes.forEach { suffix ->
                check(!File(database.parentFile, database.name + suffix).exists()) {
                    "Neutral database sidecar exists before the database rename"
                }
            }
            move(legacyDatabase, database)
        }

        DatabaseFileNames.sidecarSuffixes.forEach { suffix ->
            val legacySidecar = File(legacyDatabase.parentFile, legacyDatabase.name + suffix)
            val sidecar = File(database.parentFile, database.name + suffix)
            when {
                legacySidecar.exists() && sidecar.exists() ->
                    error("Legacy and neutral database sidecars both exist")
                legacySidecar.exists() -> {
                    check(legacySidecar.isFile) { "Legacy database sidecar is not a file" }
                    move(legacySidecar, sidecar)
                }
            }
        }

        Files.delete(marker.toPath())
    }

    private fun hasLegacySidecars(databaseDirectory: File): Boolean =
        hasSidecars(databaseDirectory, DatabaseFileNames.LEGACY_DATABASE_NAME)

    private fun hasNeutralSidecars(databaseDirectory: File): Boolean =
        hasSidecars(databaseDirectory, DatabaseFileNames.DATABASE_NAME)

    private fun hasSidecars(
        databaseDirectory: File,
        databaseName: String,
    ): Boolean =
        DatabaseFileNames.sidecarSuffixes.any { suffix ->
            File(databaseDirectory, databaseName + suffix).exists()
        }

    private fun move(
        source: File,
        target: File,
    ) {
        check(!target.exists()) { "Database rename target already exists" }
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }
}
