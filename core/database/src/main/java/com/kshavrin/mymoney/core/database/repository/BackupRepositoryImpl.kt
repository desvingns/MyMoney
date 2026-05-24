package com.kshavrin.mymoney.core.database.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.database.MoneyDatabase
import com.kshavrin.mymoney.core.domain.model.BackupFile
import com.kshavrin.mymoney.core.domain.repository.BackupRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MoneyDatabase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BackupRepository {

    override suspend fun exportDb(treeUriString: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(treeUriString))
                ?: throw IOException("Cannot open backup directory")

            val dbFile = context.getDatabasePath(DATABASE_NAME)
            checkpoint()

            val name = "$BACKUP_PREFIX${TIMESTAMP_FORMATTER.format(Instant.now())}$BACKUP_SUFFIX"
            val target = tree.createFile(MIME_TYPE, name)
                ?: throw IOException("Cannot create backup file")

            context.contentResolver.openOutputStream(target.uri)?.use { output ->
                dbFile.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException("Cannot write backup file")

            BackupRepository.backupsToDelete(listBackups(tree)).forEach { backup ->
                DocumentFile.fromSingleUri(context, Uri.parse(backup.uriString))?.delete()
            }
        }
    }

    override suspend fun importDb(documentUriString: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val temp = File.createTempFile("restore", ".db", context.cacheDir)
            try {
                context.contentResolver.openInputStream(Uri.parse(documentUriString))?.use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IOException("Cannot read backup file")

                validateSqlite(temp)

                database.close()
                val dbFile = context.getDatabasePath(DATABASE_NAME)
                temp.copyTo(dbFile, overwrite = true)
                deleteSidecars(dbFile)
            } finally {
                temp.delete()
            }
        }
    }

    override suspend fun exportToFile(destAbsolutePath: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            checkpoint()
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            dbFile.copyTo(File(destAbsolutePath), overwrite = true)
            Unit
        }
    }

    override suspend fun importFromFile(srcAbsolutePath: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val src = File(srcAbsolutePath)
            validateSqlite(src)

            database.close()
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            src.copyTo(dbFile, overwrite = true)
            deleteSidecars(dbFile)
        }
    }

    override suspend fun listLocalBackups(treeUriString: String): List<BackupFile> = withContext(ioDispatcher) {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(treeUriString)) ?: return@withContext emptyList()
        listBackups(tree).sortedByDescending { it.lastModifiedEpochMs }
    }

    private fun listBackups(tree: DocumentFile): List<BackupFile> =
        tree.listFiles()
            .filter { it.isFile && it.name?.let(::isBackupName) == true }
            .map { BackupFile(it.name.orEmpty(), it.uri.toString(), it.lastModified()) }

    private fun checkpoint() {
        database.query("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
    }

    private fun validateSqlite(file: File) {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { /* throws if invalid */ }
    }

    private fun deleteSidecars(dbFile: File) {
        File("${dbFile.path}-wal").delete()
        File("${dbFile.path}-shm").delete()
    }

    private fun isBackupName(name: String): Boolean =
        name.startsWith(BACKUP_PREFIX) && name.endsWith(BACKUP_SUFFIX)

    private companion object {
        const val DATABASE_NAME = "monefy.db"
        const val BACKUP_PREFIX = "monefy_backup_"
        const val BACKUP_SUFFIX = ".db"
        const val MIME_TYPE = "application/octet-stream"
        val TIMESTAMP_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMddHHmm").withZone(ZoneId.systemDefault())
    }
}
