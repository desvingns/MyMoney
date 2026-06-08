package com.kshavrin.mymoney.core.database.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.database.MoneyDatabase
import com.kshavrin.mymoney.core.database.mapper.toDomain
import com.kshavrin.mymoney.core.database.mapper.toEntity
import com.kshavrin.mymoney.core.domain.csv.CsvImportFormat
import com.kshavrin.mymoney.core.domain.csv.MonefyCsvImportParser
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.BackupFile
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.BackupRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.PushbackReader
import java.io.Reader
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
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
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            val dbDirectory = dbFile.parentFile ?: throw IOException("Cannot locate database directory")
            val staged = File.createTempFile("monefy_restore_", ".db", dbDirectory)
            try {
                context.contentResolver.openInputStream(Uri.parse(documentUriString))?.use { input ->
                    staged.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IOException("Cannot read backup file")

                validateSqlite(staged)

                database.close()
                Files.move(
                    staged.toPath(),
                    dbFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
                deleteSidecars(dbFile)
            } finally {
                staged.delete()
            }
        }
    }

    override suspend fun exportTransactionsCsv(documentUriString: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val transactions = database.transactionDao().observeAll().first().map { it.toDomain() }
            if (transactions.any { it.kind == TransactionKind.Transfer }) {
                throw IOException("CSV export cannot represent transfer transactions")
            }
            val currencies = transactions.map { it.currencyId }.distinct().associateWith { id ->
                database.currencyDao().findById(id)?.toDomain()?.code.orEmpty()
            }
            val accounts = transactions.map { it.accountId }.distinct().associateWith { id ->
                database.accountDao().findById(id)?.toDomain()?.name.orEmpty()
            }
            val categories = transactions.mapNotNull { it.categoryId }.distinct().associateWith { id ->
                database.categoryDao().findById(id)?.toDomain()?.name.orEmpty()
            }

            context.contentResolver.openOutputStream(Uri.parse(documentUriString), "wt")
                ?.bufferedWriter(Charsets.UTF_8)
                ?.use { writer ->
                    writer.write(CSV_HEADER)
                    writer.write(CSV_LINE_ENDING)
                    transactions.forEach { transaction ->
                        writer.write(
                            listOf(
                                transaction.id.toString(),
                                transaction.kind.name.lowercase(Locale.ROOT),
                                transaction.amount.toPlainString(),
                                currencies[transaction.currencyId].orEmpty(),
                                accounts[transaction.accountId].orEmpty(),
                                transaction.categoryId?.let { categories[it] }.orEmpty(),
                                transaction.note.orEmpty(),
                                transaction.occurredAt.toString(),
                                transaction.createdAt.toString(),
                            ).joinToString(separator = ",", transform = ::csvField),
                        )
                        writer.write(CSV_LINE_ENDING)
                    }
                } ?: throw IOException("Cannot write CSV export")
        }
    }

    override suspend fun importTransactionsCsv(documentUriString: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val records = context.contentResolver.openInputStream(Uri.parse(documentUriString))
                ?.bufferedReader(Charsets.UTF_8)
                ?.use(::parseCsv)
                ?: throw IOException("Cannot read CSV import")
            when (MonefyCsvImportParser.detectFormat(records.firstOrNull())) {
                CsvImportFormat.Monefy -> importMonefyCsv(records)
                CsvImportFormat.MyMoney -> importMyMoneyCsv(records)
                CsvImportFormat.Unknown ->
                    throw IOException("CSV header does not match the transaction schema")
            }
        }
    }

    private suspend fun importMyMoneyCsv(records: List<List<String>>) {
            val currencies = database.currencyDao().observeAll().first()
                .map { it.toDomain() }
                .groupBy { it.code.lowercase(Locale.ROOT) }
            val accounts = database.accountDao().observeActive().first()
                .map { it.toDomain() }
                .groupBy { it.name }
            val categories = database.categoryDao().observeAll().first()
                .map { it.toDomain() }
                .filterNot { it.isArchived }
                .groupBy { it.name to it.kind }
            val seenIds = mutableSetOf<Long>()

            val transactions = records.drop(1).mapIndexed { index, fields ->
                val rowNumber = index + 2
                if (fields.size != CSV_COLUMNS.size) invalidCsvRow(rowNumber, "expected ${CSV_COLUMNS.size} fields")
                val id = fields[0].toLongOrNull()?.takeIf { it > 0L }
                    ?: invalidCsvRow(rowNumber, "id must be a positive integer")
                if (!seenIds.add(id)) invalidCsvRow(rowNumber, "duplicate id")
                val kind = runCatching { TransactionKind.fromString(fields[1]) }
                    .getOrElse { invalidCsvRow(rowNumber, "unknown transaction kind") }
                if (kind == TransactionKind.Transfer) {
                    invalidCsvRow(rowNumber, "transfer import requires destination fields absent from this CSV schema")
                }
                val amount = fields[2].toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
                    ?: invalidCsvRow(rowNumber, "amount must be positive")
                val currency = currencies[fields[3].lowercase(Locale.ROOT)]?.singleOrNull()
                    ?: invalidCsvRow(rowNumber, "currency must resolve to one existing code")
                val account = accounts[fields[4]]?.singleOrNull()
                    ?: invalidCsvRow(rowNumber, "account must resolve to one active existing name")
                if (account.currencyId != currency.id) {
                    invalidCsvRow(rowNumber, "currency does not match account currency")
                }
                val categoryKind = when (kind) {
                    TransactionKind.Expense -> CategoryKind.Expense
                    TransactionKind.Income -> CategoryKind.Income
                    TransactionKind.Transfer -> error("transfer was rejected above")
                }
                val category = categories[fields[5] to categoryKind]?.singleOrNull()
                    ?: invalidCsvRow(rowNumber, "category must resolve to one active matching name")
                val note = fields[6].ifEmpty { null }
                if ((note?.length ?: 0) > MAX_NOTE_LENGTH) {
                    invalidCsvRow(rowNumber, "note exceeds $MAX_NOTE_LENGTH characters")
                }
                val occurredAt = runCatching { Instant.parse(fields[7]) }
                    .getOrElse { invalidCsvRow(rowNumber, "occurredAt must be an instant") }
                val createdAt = runCatching { Instant.parse(fields[8]) }
                    .getOrElse { invalidCsvRow(rowNumber, "createdAt must be an instant") }
                Transaction(
                    id = id,
                    kind = kind,
                    amount = amount,
                    currencyId = currency.id,
                    accountId = account.id,
                    categoryId = category.id,
                    note = note,
                    occurredAt = occurredAt,
                    createdAt = createdAt,
                    updatedAt = createdAt,
                    isDeleted = false,
                    toAccountId = null,
                    toAmount = null,
                    exchangeRate = null,
                )
            }

            for ((index, transaction) in transactions.withIndex()) {
                if (database.transactionDao().findById(transaction.id) != null) {
                    invalidCsvRow(index + 2, "id already exists in the local database")
                }
            }

            database.withTransaction {
                transactions.forEach { transaction ->
                    database.transactionDao().upsert(transaction.toEntity())
                }
            }
    }

    private suspend fun importMonefyCsv(records: List<List<String>>) {
        val rows = MonefyCsvImportParser.parse(records)

        val now = Instant.now()
        val currenciesByCode = mutableMapOf<String, Long>()
        val accountsByName = mutableMapOf<String, Long>()
        val categoriesByNameKind = mutableMapOf<Pair<String, CategoryKind>, Long>()

        suspend fun resolveCurrencyId(code: String): Long {
            val key = code.lowercase(Locale.ROOT)
            currenciesByCode[key]?.let { return it }
            val currency = database.currencyDao().findByCode(code)
                ?: throw IOException("Currency '$code' is not a seeded currency")
            return currency.id.also { currenciesByCode[key] = it }
        }

        database.withTransaction {
            val existingAccounts = database.accountDao().observeActive().first()
                .map { it.toDomain() }
            existingAccounts.forEach { account ->
                accountsByName.putIfAbsent(account.name.trim().lowercase(Locale.ROOT), account.id)
            }
            var accountSortOrder = (existingAccounts.maxOfOrNull { it.sortOrder } ?: -1) + 1

            val existingCategories = database.categoryDao().observeAll().first()
                .map { it.toDomain() }
                .filterNot { it.isArchived }
            existingCategories.forEach { category ->
                categoriesByNameKind.putIfAbsent(
                    category.name.trim().lowercase(Locale.ROOT) to category.kind,
                    category.id,
                )
            }
            var categorySortOrder = (existingCategories.maxOfOrNull { it.sortOrder } ?: -1) + 1
            var paletteIndex = 0

            suspend fun resolveAccountId(name: String, currencyId: Long): Long {
                val key = name.trim().lowercase(Locale.ROOT)
                accountsByName[key]?.let { return it }
                val account = Account(
                    id = 0L,
                    name = name.trim(),
                    currencyId = currencyId,
                    initialBalance = BigDecimal.ZERO,
                    type = AccountType.Cash,
                    colorHex = AUTO_PALETTE[paletteIndex++ % AUTO_PALETTE.size],
                    iconKey = AUTO_ACCOUNT_ICON,
                    isDefault = false,
                    sortOrder = accountSortOrder++,
                    createdAt = now,
                    updatedAt = now,
                    isArchived = false,
                )
                return database.accountDao().upsert(account.toEntity()).also { accountsByName[key] = it }
            }

            suspend fun resolveCategoryId(name: String, kind: CategoryKind): Long {
                val key = name.trim().lowercase(Locale.ROOT) to kind
                categoriesByNameKind[key]?.let { return it }
                val category = Category(
                    id = 0L,
                    name = name.trim(),
                    kind = kind,
                    iconKey = AUTO_CATEGORY_ICON,
                    colorHex = AUTO_PALETTE[paletteIndex++ % AUTO_PALETTE.size],
                    sortOrder = categorySortOrder++,
                    isDefault = false,
                    isArchived = false,
                    createdAt = now,
                )
                return database.categoryDao().upsert(category.toEntity()).also { categoriesByNameKind[key] = it }
            }

            rows.forEach { row ->
                val currencyId = resolveCurrencyId(row.currencyCode)
                val accountId = resolveAccountId(row.accountName, currencyId)
                val categoryKind = when (row.kind) {
                    TransactionKind.Expense -> CategoryKind.Expense
                    TransactionKind.Income -> CategoryKind.Income
                    TransactionKind.Transfer -> error("Monefy CSV import never produces transfers")
                }
                val categoryId = resolveCategoryId(row.categoryName, categoryKind)
                val occurredAt = row.date.atStartOfDay(ZoneId.systemDefault()).toInstant()
                val transaction = Transaction(
                    id = 0L,
                    kind = row.kind,
                    amount = row.amount,
                    currencyId = currencyId,
                    accountId = accountId,
                    categoryId = categoryId,
                    note = row.note,
                    occurredAt = occurredAt,
                    createdAt = now,
                    updatedAt = now,
                    isDeleted = false,
                    toAccountId = null,
                    toAmount = null,
                    exchangeRate = null,
                )
                database.transactionDao().upsert(transaction.toEntity())
            }
        }
    }

    override suspend fun clearDatabase(): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            database.clearAllTables()
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

    override suspend fun rotateBackups(treeUriString: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(treeUriString))
                ?: throw IOException("Cannot open backup directory")
            BackupRepository.backupsToDelete(listBackups(tree)).forEach { backup ->
                DocumentFile.fromSingleUri(context, Uri.parse(backup.uriString))?.delete()
            }
        }
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

    private fun csvField(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }

    private fun parseCsv(reader: Reader): List<List<String>> {
        val source = PushbackReader(reader, 1)
        val records = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var closedQuote = false
        var fieldStarted = false

        fun finishField() {
            row += field.toString()
            field.clear()
            closedQuote = false
            fieldStarted = false
        }

        fun finishRow() {
            finishField()
            records += row
            row = mutableListOf()
        }

        fun consumeLineFeedAfterCarriageReturn() {
            val next = source.read()
            if (next != '\n'.code && next != -1) source.unread(next)
        }

        while (true) {
            val next = source.read()
            if (next == -1) {
                if (inQuotes) throw IOException("Unterminated quoted CSV field")
                if (closedQuote || fieldStarted || field.isNotEmpty() || row.isNotEmpty()) finishRow()
                return records
            }
            val char = next.toChar()
            when {
                inQuotes -> {
                    if (char == '"') {
                        inQuotes = false
                        closedQuote = true
                    } else {
                        field.append(char)
                    }
                }
                closedQuote -> when (char) {
                    '"' -> {
                        field.append('"')
                        inQuotes = true
                        closedQuote = false
                    }
                    ',' -> finishField()
                    '\r' -> {
                        consumeLineFeedAfterCarriageReturn()
                        finishRow()
                    }
                    '\n' -> finishRow()
                    else -> throw IOException("Unexpected character after quoted CSV field")
                }
                else -> when (char) {
                    '"' -> {
                        if (fieldStarted || field.isNotEmpty()) {
                            throw IOException("Unexpected quote in CSV field")
                        }
                        inQuotes = true
                        fieldStarted = true
                    }
                    ',' -> finishField()
                    '\r' -> {
                        consumeLineFeedAfterCarriageReturn()
                        finishRow()
                    }
                    '\n' -> finishRow()
                    else -> {
                        field.append(char)
                        fieldStarted = true
                    }
                }
            }
        }
    }

    private fun invalidCsvRow(rowNumber: Int, reason: String): Nothing =
        throw IOException("Invalid CSV row $rowNumber: $reason")

    private companion object {
        const val DATABASE_NAME = "monefy.db"
        const val BACKUP_PREFIX = "monefy_backup_"
        const val BACKUP_SUFFIX = ".db"
        const val MIME_TYPE = "application/octet-stream"
        const val MAX_NOTE_LENGTH = 256
        const val CSV_HEADER = "id,kind,amount,currency,account,category,note,occurredAt,createdAt"
        val CSV_COLUMNS: List<String> = CSV_HEADER.split(",")
        const val CSV_LINE_ENDING = "\r\n"
        const val AUTO_ACCOUNT_ICON = "ic_account_cash"
        const val AUTO_CATEGORY_ICON = "ic_cat_other"
        val AUTO_PALETTE: List<String> = listOf(
            "#EF5350",
            "#AB47BC",
            "#5C6BC0",
            "#29B6F6",
            "#26A69A",
            "#9CCC65",
            "#FFA726",
            "#8D6E63",
        )
        val TIMESTAMP_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMddHHmm").withZone(ZoneId.systemDefault())
    }
}
