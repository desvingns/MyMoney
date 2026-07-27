package com.kshavrin.mymoney.core.database.migration

import com.kshavrin.mymoney.core.common.database.DatabaseFileNames
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DatabaseFileMigrationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var databaseDirectory: File

    @Before
    fun setUp() {
        databaseDirectory = temporaryFolder.newFolder("databases")
    }

    @Test
    fun `does nothing when database directory has no files`() {
        DatabaseFileMigration.migrate(databaseDirectory)

        assertTrue(databaseDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `renames legacy database and both sidecars while preserving bytes`() {
        val expectedFiles =
            mapOf(
                DatabaseFileNames.LEGACY_DATABASE_NAME to "legacy-main".toByteArray(),
                "${DatabaseFileNames.LEGACY_DATABASE_NAME}-shm" to "legacy-shm".toByteArray(),
                "${DatabaseFileNames.LEGACY_DATABASE_NAME}-wal" to "legacy-wal".toByteArray(),
            )
        expectedFiles.forEach { (name, bytes) -> writeFile(name, bytes) }

        DatabaseFileMigration.migrate(databaseDirectory)

        expectedFiles.forEach { (legacyName, bytes) ->
            val suffix = legacyName.removePrefix(DatabaseFileNames.LEGACY_DATABASE_NAME)
            assertFileBytes(DatabaseFileNames.DATABASE_NAME + suffix, bytes)
            assertFalse(file(legacyName).exists())
        }
        assertFalse(file(RENAME_MARKER_NAME).exists())
    }

    @Test
    fun `second migration call is idempotent`() {
        val expectedFiles =
            mapOf(
                DatabaseFileNames.LEGACY_DATABASE_NAME to "main-bytes".toByteArray(),
                "${DatabaseFileNames.LEGACY_DATABASE_NAME}-shm" to "shm-bytes".toByteArray(),
                "${DatabaseFileNames.LEGACY_DATABASE_NAME}-wal" to "wal-bytes".toByteArray(),
            )
        expectedFiles.forEach { (name, bytes) -> writeFile(name, bytes) }

        DatabaseFileMigration.migrate(databaseDirectory)
        DatabaseFileMigration.migrate(databaseDirectory)

        expectedFiles.forEach { (legacyName, bytes) ->
            val suffix = legacyName.removePrefix(DatabaseFileNames.LEGACY_DATABASE_NAME)
            assertFileBytes(DatabaseFileNames.DATABASE_NAME + suffix, bytes)
            assertFalse(file(legacyName).exists())
        }
        assertFalse(file(RENAME_MARKER_NAME).exists())
    }

    @Test
    fun `rejects an orphan neutral database sidecar`() {
        writeFile("${DatabaseFileNames.DATABASE_NAME}-wal", "orphan-wal".toByteArray())

        assertThrows(IllegalStateException::class.java) {
            DatabaseFileMigration.migrate(databaseDirectory)
        }
    }

    @Test
    fun `rejects an orphan legacy database sidecar`() {
        writeFile("${DatabaseFileNames.LEGACY_DATABASE_NAME}-shm", "orphan-shm".toByteArray())

        assertThrows(IllegalStateException::class.java) {
            DatabaseFileMigration.migrate(databaseDirectory)
        }
    }

    @Test
    fun `rejects both legacy and neutral database files`() {
        writeFile(DatabaseFileNames.LEGACY_DATABASE_NAME, "legacy".toByteArray())
        writeFile(DatabaseFileNames.DATABASE_NAME, "neutral".toByteArray())

        assertThrows(IllegalStateException::class.java) {
            DatabaseFileMigration.migrate(databaseDirectory)
        }
    }

    @Test
    fun `resumes an interrupted rename after the neutral main file was moved`() {
        val mainBytes = "neutral-main".toByteArray()
        val shmBytes = "legacy-shm".toByteArray()
        val walBytes = "legacy-wal".toByteArray()
        writeFile(DatabaseFileNames.DATABASE_NAME, mainBytes)
        writeFile("${DatabaseFileNames.LEGACY_DATABASE_NAME}-shm", shmBytes)
        writeFile("${DatabaseFileNames.LEGACY_DATABASE_NAME}-wal", walBytes)
        writeFile(RENAME_MARKER_NAME, ByteArray(0))

        DatabaseFileMigration.migrate(databaseDirectory)

        assertFileBytes(DatabaseFileNames.DATABASE_NAME, mainBytes)
        assertFileBytes("${DatabaseFileNames.DATABASE_NAME}-shm", shmBytes)
        assertFileBytes("${DatabaseFileNames.DATABASE_NAME}-wal", walBytes)
        assertFalse(file("${DatabaseFileNames.LEGACY_DATABASE_NAME}-shm").exists())
        assertFalse(file("${DatabaseFileNames.LEGACY_DATABASE_NAME}-wal").exists())
        assertFalse(file(RENAME_MARKER_NAME).exists())
    }

    private fun file(name: String): File = File(databaseDirectory, name)

    private fun writeFile(
        name: String,
        bytes: ByteArray,
    ) {
        file(name).writeBytes(bytes)
    }

    private fun assertFileBytes(
        name: String,
        expected: ByteArray,
    ) {
        val actualFile = file(name)
        assertTrue("Expected $name to exist", actualFile.isFile)
        assertArrayEquals(expected, actualFile.readBytes())
    }

    private companion object {
        const val RENAME_MARKER_NAME = "mymoney.db.rename-in-progress"
    }
}
