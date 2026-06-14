package com.kshavrin.mymoney.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kshavrin.mymoney.core.database.entity.CurrencyEntity
import com.kshavrin.mymoney.core.database.repository.BackupRepositoryImpl
import com.kshavrin.mymoney.core.domain.repository.BackupSchemaTooNewException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Guards the restore schema gate: a backup carrying a PRAGMA user_version newer
 * than the app's MoneyDatabase.SCHEMA_VERSION is rejected BEFORE the file swap,
 * so the @Singleton Room is never downgraded into a crash-loop.
 */
@RunWith(AndroidJUnit4::class)
class BackupImportSchemaGateTest {
    private lateinit var db: MoneyDatabase
    private lateinit var context: Context
    private lateinit var repo: BackupRepositoryImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db =
            Room
                .inMemoryDatabaseBuilder(context, MoneyDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repo = BackupRepositoryImpl(context, db, Dispatchers.IO)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createBackupFile(userVersion: Int): File {
        val file = File.createTempFile("schema_gate_", ".db", context.cacheDir)
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
}
