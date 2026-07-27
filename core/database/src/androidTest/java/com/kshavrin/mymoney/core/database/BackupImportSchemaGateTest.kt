package com.kshavrin.mymoney.core.database

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kshavrin.mymoney.core.common.database.DatabaseFileNames
import com.kshavrin.mymoney.core.database.entity.CurrencyEntity
import com.kshavrin.mymoney.core.database.repository.BackupRepositoryImpl
import com.kshavrin.mymoney.core.domain.repository.BackupSchemaTooNewException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * Guards the restore schema gate: a backup carrying a PRAGMA user_version newer
 * than the app's MoneyDatabase.SCHEMA_VERSION is rejected BEFORE the file swap,
 * so the @Singleton Room is never downgraded into a crash-loop.
 */
@RunWith(AndroidJUnit4::class)
class BackupImportSchemaGateTest {
    private lateinit var db: MoneyDatabase
    private lateinit var context: Context
    private lateinit var repositoryContext: Context
    private lateinit var databaseDirectory: File
    private lateinit var backupDirectory: File
    private lateinit var repo: BackupRepositoryImpl
    private var restoredDb: MoneyDatabase? = null
    private val temporaryFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseDirectory = File(context.cacheDir, "backup_schema_gate_db_${UUID.randomUUID()}").also { it.mkdirs() }
        backupDirectory = File(context.cacheDir, "backup_schema_gate_files_${UUID.randomUUID()}").also { it.mkdirs() }
        repositoryContext =
            object : ContextWrapper(context) {
                override fun getDatabasePath(name: String): File = File(databaseDirectory, name)
            }
        db =
            Room
                .inMemoryDatabaseBuilder(context, MoneyDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repo = BackupRepositoryImpl(repositoryContext, db, Dispatchers.IO)
    }

    @After
    fun tearDown() {
        restoredDb?.close()
        db.close()

        val leftovers = mutableListOf<File>()
        temporaryFiles
            .filter { it.exists() }
            .forEach { file ->
                if (!file.delete()) leftovers += file
            }
        listOf(databaseDirectory, backupDirectory).forEach { directory ->
            if (directory.exists() && !directory.deleteRecursively()) leftovers += directory
        }
        check(leftovers.isEmpty()) { "Test cleanup failed: ${leftovers.joinToString()}" }
    }

    private fun createBackupFile(userVersion: Int): File {
        val file = File.createTempFile("schema_gate_", ".db", context.cacheDir)
        temporaryFiles += file
        SQLiteDatabase.openOrCreateDatabase(file, null).use { sqlite ->
            sqlite.execSQL("CREATE TABLE marker(id INTEGER PRIMARY KEY)")
            sqlite.version = userVersion
        }
        return file
    }

    @Test
    fun `import rejects a backup with a newer schema version`() =
        runTest {
            val newer = createBackupFile(MoneyDatabase.SCHEMA_VERSION + 1)

            val result = repo.importFromFile(newer.absolutePath)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is BackupSchemaTooNewException)
        }

    @Test
    fun `import does not touch the current database when rejecting a newer backup`() =
        runTest {
            db.currencyDao().upsert(
                CurrencyEntity(
                    code = "RUB",
                    symbol = "₽",
                    name = "Russian Ruble",
                    decimalDigits = 2,
                    isActive = true,
                    sortOrder = 0,
                ),
            )
            val newer = createBackupFile(MoneyDatabase.SCHEMA_VERSION + 1)

            repo.importFromFile(newer.absolutePath)

            assertEquals(
                1,
                db
                    .currencyDao()
                    .observeAll()
                    .first()
                    .size,
            )
        }

    @Test
    fun `import accepts a backup at the current schema version`() =
        runTest {
            val same = createBackupFile(MoneyDatabase.SCHEMA_VERSION)

            val result = repo.importFromFile(same.absolutePath)

            assertTrue(result.isSuccess)
        }

    @Test
    fun `legacy named export and import restores in memory data`() =
        runTest {
            db.currencyDao().upsert(
                CurrencyEntity(
                    code = "USD",
                    symbol = "$",
                    name = "US Dollar",
                    decimalDigits = 2,
                    isActive = true,
                    sortOrder = 0,
                ),
            )
            materializeInMemoryDatabase()
            val backup = legacyBackupFile()

            assertTrue(repo.exportToFile(backup.absolutePath).isSuccess)
            assertEquals(DatabaseFileNames.LEGACY_DATABASE_NAME, backup.name)

            assertTrue(repo.clearDatabase().isSuccess)
            assertTrue(
                db
                    .currencyDao()
                    .observeAll()
                    .first()
                    .isEmpty(),
            )

            assertTrue(repo.importFromFile(backup.absolutePath).isSuccess)
            val restored = reopenRestoredDatabase()

            assertEquals(
                "USD",
                restored
                    .currencyDao()
                    .observeAll()
                    .first()
                    .single()
                    .code,
            )
        }

    @Test
    fun `export reads neutral database and imports a legacy named backup`() =
        runTest {
            val currencyId =
                db.currencyDao().upsert(
                    CurrencyEntity(
                        code = "NEUTRAL",
                        symbol = "N",
                        name = "Neutral Currency",
                        decimalDigits = 2,
                        isActive = true,
                        sortOrder = 0,
                    ),
                )
            materializeInMemoryDatabase()
            assertFalse(repositoryContext.getDatabasePath(DatabaseFileNames.LEGACY_DATABASE_NAME).exists())
            val backup = legacyBackupFile()

            assertTrue(repo.exportToFile(backup.absolutePath).isSuccess)
            assertEquals("NEUTRAL", readCurrencyCode(backup))

            db.currencyDao().setActive(currencyId, false)
            assertFalse(db.currencyDao().findById(currencyId)!!.isActive)
            assertTrue(repo.importFromFile(backup.absolutePath).isSuccess)
            val restored = reopenRestoredDatabase()

            assertEquals(
                "NEUTRAL",
                restored
                    .currencyDao()
                    .observeAll()
                    .first()
                    .single()
                    .code,
            )
            assertTrue(
                restored
                    .currencyDao()
                    .observeAll()
                    .first()
                    .single()
                    .isActive,
            )
        }

    private fun materializeInMemoryDatabase() {
        val neutralFile = repositoryContext.getDatabasePath(DatabaseFileNames.DATABASE_NAME)
        val escapedPath = neutralFile.absolutePath.replace("'", "''")
        db.openHelper.writableDatabase.execSQL("VACUUM INTO '$escapedPath'")
    }

    private fun readCurrencyCode(file: File): String =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
            sqlite.rawQuery("SELECT code FROM currency ORDER BY id LIMIT 1", null).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getString(0)
            }
        }

    private fun legacyBackupFile(): File = File(backupDirectory, DatabaseFileNames.LEGACY_DATABASE_NAME)

    private fun reopenRestoredDatabase(): MoneyDatabase =
        Room
            .databaseBuilder(
                repositoryContext,
                MoneyDatabase::class.java,
                DatabaseFileNames.DATABASE_NAME,
            ).allowMainThreadQueries()
            .build()
            .also { restoredDb = it }
}
