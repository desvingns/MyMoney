package com.kshavrin.mymoney.core.database.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.kshavrin.mymoney.core.common.category.categoryIconDominantHex
import com.kshavrin.mymoney.core.common.category.categoryTextColorHex
import com.kshavrin.mymoney.core.common.database.DatabaseFileNames
import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.database.MoneyDatabase
import com.kshavrin.mymoney.core.database.entity.AccountEntity
import com.kshavrin.mymoney.core.database.entity.CategoryEntity
import com.kshavrin.mymoney.core.database.entity.OperationEntity
import com.kshavrin.mymoney.core.database.entity.TransactionEntity
import com.kshavrin.mymoney.core.database.journal.OperationPayloadCodec
import com.kshavrin.mymoney.core.database.mapper.toDomain
import com.kshavrin.mymoney.core.database.mapper.toEntity
import com.kshavrin.mymoney.core.domain.csv.CsvImportFormat
import com.kshavrin.mymoney.core.domain.csv.ExistingCategorySummary
import com.kshavrin.mymoney.core.domain.csv.ImportCategoryStrategy
import com.kshavrin.mymoney.core.domain.csv.ImportDataStrategy
import com.kshavrin.mymoney.core.domain.csv.ImportPlan
import com.kshavrin.mymoney.core.domain.csv.ImportPreview
import com.kshavrin.mymoney.core.domain.csv.MergeAction
import com.kshavrin.mymoney.core.domain.csv.MonefyCsvImportParser
import com.kshavrin.mymoney.core.domain.csv.OrphanDecision
import com.kshavrin.mymoney.core.domain.csv.PreviewAccount
import com.kshavrin.mymoney.core.domain.csv.PreviewCategory
import com.kshavrin.mymoney.core.domain.csv.PreviewDateRange
import com.kshavrin.mymoney.core.domain.csv.StagedImport
import com.kshavrin.mymoney.core.domain.csv.TransactionDedupKey
import com.kshavrin.mymoney.core.domain.csv.dedupTransactions
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.BackupFile
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.model.toMoneyScale
import com.kshavrin.mymoney.core.domain.repository.BackupRepository
import com.kshavrin.mymoney.core.domain.repository.BackupSchemaTooNewException
import com.kshavrin.mymoney.core.domain.repository.CsvImportFocus
import com.kshavrin.mymoney.core.domain.sync.DeviceIdProvider
import com.kshavrin.mymoney.core.domain.sync.EntityKind
import com.kshavrin.mymoney.core.domain.sync.OpType
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MoneyDatabase,
        private val payloadCodec: OperationPayloadCodec,
        private val deviceIdProvider: DeviceIdProvider,
        private val clock: Clock,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : BackupRepository {
        constructor(
            context: Context,
            database: MoneyDatabase,
            ioDispatcher: CoroutineDispatcher,
        ) : this(
            context = context,
            database = database,
            payloadCodec = OperationPayloadCodec(database.currencyDao()),
            deviceIdProvider =
                object : DeviceIdProvider {
                    override suspend fun deviceId(): String = TEST_DEVICE_ID
                },
            clock = Clock.systemUTC(),
            ioDispatcher = ioDispatcher,
        )

        override suspend fun exportDb(treeUriString: String): Result<Unit> =
            withContext(ioDispatcher) {
                runCatching {
                    val tree =
                        DocumentFile.fromTreeUri(context, Uri.parse(treeUriString))
                            ?: throw IOException("Cannot open backup directory")

                    val dbFile = context.getDatabasePath(DatabaseFileNames.DATABASE_NAME)
                    checkpoint()

                    val name = "$BACKUP_PREFIX${TIMESTAMP_FORMATTER.format(Instant.now())}$BACKUP_SUFFIX"
                    val target =
                        tree.createFile(MIME_TYPE, name)
                            ?: throw IOException("Cannot create backup file")

                    context.contentResolver.openOutputStream(target.uri)?.use { output ->
                        dbFile.inputStream().use { input -> input.copyTo(output) }
                    } ?: throw IOException("Cannot write backup file")

                    BackupRepository.backupsToDelete(listBackups(tree)).forEach { backup ->
                        DocumentFile.fromSingleUri(context, Uri.parse(backup.uriString))?.delete()
                    }
                }
            }

        override suspend fun importDb(documentUriString: String): Result<Unit> =
            withContext(ioDispatcher) {
                runCatching {
                    val dbFile = context.getDatabasePath(DatabaseFileNames.DATABASE_NAME)
                    val dbDirectory = dbFile.parentFile ?: throw IOException("Cannot locate database directory")
                    val staged = File.createTempFile("monefy_restore_", ".db", dbDirectory)
                    try {
                        context.contentResolver.openInputStream(Uri.parse(documentUriString))?.use { input ->
                            staged.outputStream().use { output -> input.copyTo(output) }
                        } ?: throw IOException("Cannot read backup file")

                        validateSqlite(staged)
                        requireCompatibleSchema(staged)

                        replaceDatabase(staged, dbFile)
                    } finally {
                        staged.delete()
                    }
                }
            }

        override suspend fun exportTransactionsCsv(documentUriString: String): Result<Unit> =
            withContext(ioDispatcher) {
                runCatching {
                    val transactions =
                        database
                            .transactionDao()
                            .observeAll()
                            .first()
                            .map { it.toDomain() }
                    val currencies =
                        transactions.map { it.currencyId }.distinct().associateWith { id ->
                            database
                                .currencyDao()
                                .findById(id)
                                ?.toDomain()
                                ?.code
                                .orEmpty()
                        }
                    val accountIds =
                        (transactions.map { it.accountId } + transactions.mapNotNull { it.toAccountId })
                            .distinct()
                    val accounts =
                        accountIds.associateWith { id ->
                            database
                                .accountDao()
                                .findById(id)
                                ?.toDomain()
                                ?.name
                                .orEmpty()
                        }
                    val categories =
                        transactions.mapNotNull { it.categoryId }.distinct().associateWith { id ->
                            database
                                .categoryDao()
                                .findById(id)
                                ?.toDomain()
                                ?.name
                                .orEmpty()
                        }

                    context.contentResolver
                        .openOutputStream(Uri.parse(documentUriString), "wt")
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
                                        transaction.toAccountId?.let { accounts[it] }.orEmpty(),
                                        transaction.toAmount?.toPlainString().orEmpty(),
                                    ).joinToString(separator = ",", transform = ::csvField),
                                )
                                writer.write(CSV_LINE_ENDING)
                            }
                        } ?: throw IOException("Cannot write CSV export")
                }
            }

        override suspend fun importTransactionsCsv(documentUriString: String): Result<CsvImportFocus?> =
            parseImport(documentUriString).mapCatching { staged ->
                commitImport(
                    staged,
                    ImportPlan(
                        dataStrategy = ImportDataStrategy.Append,
                        categoryStrategy = ImportCategoryStrategy.Append,
                    ),
                ).getOrThrow()
            }

        override suspend fun parseImport(documentUriString: String): Result<StagedImport> =
            withContext(ioDispatcher) {
                runCatching {
                    val records =
                        context.contentResolver
                            .openInputStream(Uri.parse(documentUriString))
                            ?.bufferedReader(Charsets.UTF_8)
                            ?.use(::parseCsv)
                            ?: throw IOException("Cannot read CSV import")
                    val format = MonefyCsvImportParser.detectFormat(records.firstOrNull())
                    if (format == CsvImportFormat.Unknown) {
                        throw IOException("CSV header does not match the transaction schema")
                    }
                    StagedImport(
                        format = format,
                        records = records,
                        preview = buildPreview(format, records),
                    )
                }
            }

        override suspend fun commitImport(
            staged: StagedImport,
            plan: ImportPlan,
        ): Result<CsvImportFocus?> =
            withContext(ioDispatcher) {
                runCatching {
                    val deviceId = deviceIdProvider.deviceId()
                    database.withTransaction {
                        if (plan.dataStrategy is ImportDataStrategy.ReplaceAll) {
                            // O2: wipe transactions, accounts and app categories; currencies and
                            // AppSettings live in other stores and survive. Same transaction as the
                            // re-import, so a failure rolls the whole clean-slate back (D8).
                            database.transactionDao().deleteAll()
                            database.accountDao().deleteAll()
                            database.categoryDao().deleteAll()
                        } else if (plan.categoryStrategy is ImportCategoryStrategy.ReplaceCurrent) {
                            // D5: drop the current categories, honouring per-category OrphanDecision for
                            // those that still carry transactions. Runs inside the same withTransaction as
                            // the re-import so the whole plan commits or rolls back atomically (D8).
                            replaceCurrentCategories(plan.orphanDecisions)
                        }
                        val focus =
                            when (staged.format) {
                                CsvImportFormat.Monefy ->
                                    importMonefyCsv(staged.records, plan.dataStrategy, plan.categoryStrategy, deviceId)
                                CsvImportFormat.MyMoney -> importMyMoneyCsv(staged.records, plan.dataStrategy, deviceId)
                                CsvImportFormat.Unknown ->
                                    throw IOException("CSV header does not match the transaction schema")
                            }
                        // Imports resolve currencies via findByCode, which ignores is_active. A currency
                        // switched off in the currency list would import live transactions yet stay
                        // invisible, so the persisted import-focus selection (and the imported accounts)
                        // never resolve on a cold-started dashboard. Re-activate any currency that now
                        // owns rows so the focus currency is guaranteed queryable after a restart.
                        database.currencyDao().activateCurrenciesWithLiveTransactions()
                        focus
                    }
                }
            }

        override suspend fun existingCategorySummaries(): List<ExistingCategorySummary> =
            withContext(ioDispatcher) {
                database
                    .categoryDao()
                    .observeAll()
                    .first()
                    .map { it.toDomain() }
                    .filterNot { it.isArchived }
                    .map { category ->
                        ExistingCategorySummary(
                            id = category.id,
                            name = category.name,
                            kind = category.kind,
                            transactionCount = database.transactionDao().countByCategory(category.id),
                        )
                    }
            }

        private suspend fun replaceCurrentCategories(orphanDecisions: Map<String, OrphanDecision>) {
            val existingCategories =
                database
                    .categoryDao()
                    .observeAll()
                    .first()
                    .map { it.toDomain() }
                    .filterNot { it.isArchived }
            val idsToDelete = mutableListOf<Long>()
            existingCategories.forEach { category ->
                val txCount = database.transactionDao().countByCategory(category.id)
                if (txCount == 0) {
                    // Empty categories are removed silently (D5).
                    idsToDelete += category.id
                    return@forEach
                }
                when (orphanDecisions[category.name] ?: OrphanDecision.KeepCategory) {
                    OrphanDecision.KeepCategory -> Unit
                    OrphanDecision.DeleteTransactions -> {
                        database.transactionDao().deleteByCategory(category.id)
                        idsToDelete += category.id
                    }
                }
            }
            if (idsToDelete.isNotEmpty()) {
                database.categoryDao().deleteByIds(idsToDelete)
            }
        }

        private suspend fun buildPreview(
            format: CsvImportFormat,
            records: List<List<String>>,
        ): ImportPreview =
            when (format) {
                CsvImportFormat.Monefy -> {
                    val rows = MonefyCsvImportParser.parse(records)
                    ImportPreview(
                        rowCount = rows.size,
                        categories =
                            rows
                                .map {
                                    PreviewCategory(
                                        name = it.categoryName,
                                        kind =
                                            when (it.kind) {
                                                TransactionKind.Income -> CategoryKind.Income
                                                else -> CategoryKind.Expense
                                            },
                                    )
                                }.toSet(),
                        accounts =
                            rows
                                .map { PreviewAccount(name = it.accountName, currencyCode = it.currencyCode) }
                                .toSet(),
                        dateRange =
                            rows.map { it.date }.let { dates ->
                                val min = dates.minOrNull()
                                val max = dates.maxOrNull()
                                if (min != null && max != null) PreviewDateRange(min, max) else null
                            },
                    )
                }
                CsvImportFormat.MyMoney, CsvImportFormat.Unknown ->
                    ImportPreview(
                        rowCount = (records.size - 1).coerceAtLeast(0),
                        categories = emptySet(),
                        accounts = emptySet(),
                        dateRange = null,
                    )
            }

        private suspend fun existingDedupKeys(): Set<TransactionDedupKey> =
            database
                .transactionDao()
                .listDedupRows()
                .map { row ->
                    TransactionDedupKey.of(
                        accountName = row.accountName,
                        occurredAt = Instant.ofEpochMilli(row.occurredAt),
                        amount = BigDecimal.valueOf(row.amount),
                        categoryName = row.categoryName.orEmpty(),
                        kind = TransactionKind.fromString(row.kind),
                        note = row.note,
                    )
                }.toSet()

        private suspend fun importMyMoneyCsv(
            records: List<List<String>>,
            dataStrategy: ImportDataStrategy,
            deviceId: String,
        ): CsvImportFocus? {
            val now = clock.instant()
            val currencies =
                database
                    .currencyDao()
                    .observeAll()
                    .first()
                    .map { it.toDomain() }
                    .groupBy { it.code.lowercase(Locale.ROOT) }
            val currenciesById = currencies.values.flatten().associateBy { it.id }
            val accounts =
                database
                    .accountDao()
                    .observeActive()
                    .first()
                    .map { it.toDomain() }
                    .groupBy { it.name }
            val accountsById = accounts.values.flatten().associateBy { it.id }
            val categories =
                database
                    .categoryDao()
                    .observeAll()
                    .first()
                    .map { it.toDomain() }
                    .filterNot { it.isArchived }
                    .groupBy { it.name to it.kind }
            val seenIds = mutableSetOf<Long>()

            val transactions =
                records.drop(1).mapIndexed { index, fields ->
                    val rowNumber = index + 2
                    if (fields.size != CSV_COLUMNS.size && fields.size != CSV_LEGACY_COLUMN_COUNT) {
                        invalidCsvRow(rowNumber, "expected $CSV_LEGACY_COLUMN_COUNT or ${CSV_COLUMNS.size} fields")
                    }
                    val id =
                        fields[0].toLongOrNull()?.takeIf { it > 0L }
                            ?: invalidCsvRow(rowNumber, "id must be a positive integer")
                    if (!seenIds.add(id)) invalidCsvRow(rowNumber, "duplicate id")
                    val kind =
                        runCatching { TransactionKind.fromString(fields[1]) }
                            .getOrElse { invalidCsvRow(rowNumber, "unknown transaction kind") }
                    val amount =
                        fields[2].toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
                            ?: invalidCsvRow(rowNumber, "amount must be positive")
                    val currency =
                        currencies[fields[3].lowercase(Locale.ROOT)]?.singleOrNull()
                            ?: invalidCsvRow(rowNumber, "currency must resolve to one existing code")
                    val account =
                        accounts[fields[4]]?.singleOrNull()
                            ?: invalidCsvRow(rowNumber, "account must resolve to one active existing name")
                    if (account.currencyId != currency.id) {
                        invalidCsvRow(rowNumber, "currency does not match account currency")
                    }
                    val note = fields[6].ifEmpty { null }
                    if ((note?.length ?: 0) > MAX_NOTE_LENGTH) {
                        invalidCsvRow(rowNumber, "note exceeds $MAX_NOTE_LENGTH characters")
                    }
                    val occurredAt =
                        runCatching { Instant.parse(fields[7]) }
                            .getOrElse { invalidCsvRow(rowNumber, "occurredAt must be an instant") }
                    val createdAt =
                        runCatching { Instant.parse(fields[8]) }
                            .getOrElse { invalidCsvRow(rowNumber, "createdAt must be an instant") }

                    if (kind == TransactionKind.Transfer) {
                        if (fields.size != CSV_COLUMNS.size) {
                            invalidCsvRow(rowNumber, "transfer requires to_account and to_amount columns")
                        }
                        if (fields[5].isNotEmpty()) {
                            invalidCsvRow(rowNumber, "transfer must not carry a category")
                        }
                        val toAccount =
                            accounts[fields[9]]?.singleOrNull()
                                ?: invalidCsvRow(rowNumber, "to_account must resolve to one active existing name")
                        val toAmount =
                            fields[10].toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
                                ?: invalidCsvRow(rowNumber, "to_amount must be positive")
                        Transaction(
                            id = id,
                            kind = kind,
                            amount = amount,
                            currencyId = currency.id,
                            accountId = account.id,
                            categoryId = null,
                            note = note,
                            occurredAt = occurredAt,
                            createdAt = createdAt,
                            updatedAt = createdAt,
                            isDeleted = false,
                            toAccountId = toAccount.id,
                            toAmount = toAmount,
                            exchangeRate = null,
                        )
                    } else {
                        val categoryKind =
                            when (kind) {
                                TransactionKind.Expense -> CategoryKind.Expense
                                TransactionKind.Income -> CategoryKind.Income
                                TransactionKind.Transfer -> error("transfer handled above")
                            }
                        val category =
                            categories[fields[5] to categoryKind]?.singleOrNull()
                                ?: invalidCsvRow(rowNumber, "category must resolve to one active matching name")
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
                }

            for ((index, transaction) in transactions.withIndex()) {
                if (database.transactionDao().findById(transaction.id) != null) {
                    invalidCsvRow(index + 2, "id already exists in the local database")
                }
            }

            val accountNamesById = accounts.values.flatten().associate { it.id to it.name }
            val categoryNamesById = categories.values.flatten().associate { it.id to it.name }
            val toInsert =
                if (dataStrategy is ImportDataStrategy.AppendDedup) {
                    dedupTransactions(
                        items = transactions,
                        existingKeys = existingDedupKeys(),
                        keyOf = { it.dedupKey(accountNamesById, categoryNamesById, currenciesById) },
                    ).unique
                } else {
                    transactions
                }

            toInsert.forEach { transaction ->
                val currency = checkNotNull(currenciesById[transaction.currencyId])
                val toCurrency =
                    transaction.toAccountId
                        ?.let { toAccountId -> checkNotNull(accountsById[toAccountId]) }
                        ?.let { toAccount -> checkNotNull(currenciesById[toAccount.currencyId]) }
                upsertImportedTransaction(
                    transaction.toImportEntity(
                        currency = currency,
                        toCurrency = toCurrency,
                        deviceId = deviceId,
                        updatedAt = now,
                    ),
                    deviceId = deviceId,
                    updatedAt = now,
                )
            }
            return toInsert.maxByOrNull { it.occurredAt }?.let { latest ->
                CsvImportFocus(
                    occurredAtEpochMs = latest.occurredAt.toEpochMilli(),
                    currencyId = latest.currencyId,
                )
            }
        }

        private fun Transaction.dedupKey(
            accountNamesById: Map<Long, String>,
            categoryNamesById: Map<Long, String>,
            currenciesById: Map<Long, Currency>,
        ): TransactionDedupKey =
            TransactionDedupKey.of(
                accountName = accountNamesById[accountId].orEmpty(),
                occurredAt = occurredAt,
                amount = amount.toMoneyScale(checkNotNull(currenciesById[currencyId])),
                categoryName = categoryId?.let { categoryNamesById[it] }.orEmpty(),
                kind = kind,
                note = note,
            )

        private fun Transaction.toImportEntity(
            currency: Currency,
            toCurrency: Currency? = null,
            deviceId: String,
            updatedAt: Instant,
        ) = copy(
            amount = amount.toMoneyScale(currency),
            toAmount =
                toAmount?.let { amount ->
                    amount.toMoneyScale(checkNotNull(toCurrency))
                },
        ).toEntity().copy(
            uuid = UUID.randomUUID().toString(),
            deviceId = deviceId,
            updatedAt = updatedAt.toEpochMilli(),
        )

        private suspend fun importMonefyCsv(
            records: List<List<String>>,
            dataStrategy: ImportDataStrategy,
            categoryStrategy: ImportCategoryStrategy,
            deviceId: String,
        ): CsvImportFocus? {
            val parsedRows = MonefyCsvImportParser.parse(records)
            val now = clock.instant()
            val currenciesByCode = mutableMapOf<String, Currency>()
            val accountsByNameCurrency = mutableMapOf<Pair<String, Long>, Long>()
            val categoriesByNameKind = mutableMapOf<Pair<String, CategoryKind>, Long>()
            var latestImportFocus: CsvImportFocus? = null

            suspend fun resolveCurrency(code: String): Currency {
                val key = code.lowercase(Locale.ROOT)
                currenciesByCode[key]?.let { return it }
                val currency =
                    database.currencyDao().findByCode(code)
                        ?: throw IOException("Currency '$code' is not a seeded currency")
                return currency.toDomain().also { currenciesByCode[key] = it }
            }

            val rows =
                if (dataStrategy is ImportDataStrategy.AppendDedup) {
                    val rowsWithCurrency = parsedRows.map { row -> row to resolveCurrency(row.currencyCode) }
                    dedupTransactions(
                        items = rowsWithCurrency,
                        existingKeys = existingDedupKeys(),
                        keyOf = { (row, currency) ->
                            TransactionDedupKey.of(
                                accountName = row.accountName,
                                occurredAt = row.date.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                                amount = row.amount.toMoneyScale(currency),
                                categoryName = row.categoryName,
                                kind = row.kind,
                                note = row.note,
                            )
                        },
                    ).unique.map { (row, _) -> row }
                } else {
                    parsedRows
                }

            run {
                val existingAccounts =
                    database
                        .accountDao()
                        .observeActive()
                        .first()
                        .map { it.toDomain() }
                existingAccounts.forEach { account ->
                    accountsByNameCurrency.putIfAbsent(
                        MonefyCsvImportParser.normalizeName(account.name) to account.currencyId,
                        account.id,
                    )
                }
                var accountSortOrder = (existingAccounts.maxOfOrNull { it.sortOrder } ?: -1) + 1

                val existingCategories =
                    database
                        .categoryDao()
                        .observeAll()
                        .first()
                        .map { it.toDomain() }
                        .filterNot { it.isArchived }
                existingCategories.forEach { category ->
                    categoriesByNameKind.putIfAbsent(
                        MonefyCsvImportParser.normalizeName(category.name) to category.kind,
                        category.id,
                    )
                }
                var categorySortOrder = (existingCategories.maxOfOrNull { it.sortOrder } ?: -1) + 1
                var paletteIndex = 0

                if (categoryStrategy is ImportCategoryStrategy.AppendManualMerge) {
                    // D6: route each import category onto its chosen target id (reuse, no duplicate) and
                    // rename the target to resultName. Rows merge under the shared categoryId; CreateNew
                    // mappings fall through to the normal exact-name resolve below.
                    categoryStrategy.mappings.forEach { mapping ->
                        val action = mapping.action
                        if (action is MergeAction.MergeInto) {
                            val target =
                                action.targetId?.let { id -> existingCategories.firstOrNull { it.id == id } }
                                    ?: existingCategories.firstOrNull {
                                        MonefyCsvImportParser.normalizeName(it.name) ==
                                            MonefyCsvImportParser.normalizeName(action.targetCategoryName)
                                    }
                            if (target != null) {
                                if (action.resultName != target.name) {
                                    val targetEntity =
                                        requireNotNull(database.categoryDao().findById(target.id)) {
                                            "category not found: ${target.id}"
                                        }
                                    upsertImportedCategory(
                                        targetEntity.copy(name = action.resultName),
                                        deviceId = deviceId,
                                        updatedAt = now,
                                    )
                                    categoriesByNameKind[
                                        MonefyCsvImportParser.normalizeName(action.resultName) to target.kind,
                                    ] = target.id
                                }
                                categoriesByNameKind[
                                    MonefyCsvImportParser.normalizeName(mapping.importCategoryName) to target.kind,
                                ] = target.id
                            }
                        }
                    }
                }

                suspend fun resolveAccountId(
                    name: String,
                    currencyId: Long,
                    currencyCode: String,
                ): Long {
                    val normalized = MonefyCsvImportParser.normalizeName(name)
                    val nameMatchesOtherCurrency =
                        accountsByNameCurrency.keys.any { (existingName, existingCurrencyId) ->
                            existingName == normalized && existingCurrencyId != currencyId
                        }
                    val accountName = if (nameMatchesOtherCurrency) "${name.trim()} ($currencyCode)" else name.trim()
                    val key = MonefyCsvImportParser.normalizeName(accountName) to currencyId
                    accountsByNameCurrency[key]?.let { return it }
                    val account =
                        Account(
                            id = 0L,
                            name = accountName,
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
                    return upsertImportedAccount(
                        account.toEntity().copy(uuid = UUID.randomUUID().toString()),
                        deviceId = deviceId,
                        updatedAt = now,
                    ).also { accountsByNameCurrency[key] = it }
                }

                suspend fun resolveCategoryId(
                    name: String,
                    kind: CategoryKind,
                ): Long {
                    val key = MonefyCsvImportParser.normalizeName(name) to kind
                    categoriesByNameKind[key]?.let { return it }
                    val category =
                        Category(
                            id = 0L,
                            name = name.trim(),
                            kind = kind,
                            iconKey = AUTO_CATEGORY_ICON,
                            colorHex = categoryIconDominantHex(AUTO_CATEGORY_ICON),
                            textColor = categoryTextColorHex(AUTO_CATEGORY_ICON),
                            sortOrder = categorySortOrder++,
                            isDefault = false,
                            isArchived = false,
                            createdAt = now,
                        )
                    return upsertImportedCategory(
                        category.toEntity().copy(uuid = UUID.randomUUID().toString()),
                        deviceId = deviceId,
                        updatedAt = now,
                    ).also { categoriesByNameKind[key] = it }
                }

                rows.forEach { row ->
                    val currency = resolveCurrency(row.currencyCode)
                    val currencyId = currency.id
                    val accountId = resolveAccountId(row.accountName, currencyId, row.currencyCode)
                    val categoryKind =
                        when (row.kind) {
                            TransactionKind.Expense -> CategoryKind.Expense
                            TransactionKind.Income -> CategoryKind.Income
                            TransactionKind.Transfer -> error("Monefy CSV import never produces transfers")
                        }
                    val categoryId = resolveCategoryId(row.categoryName, categoryKind)
                    val occurredAt = row.date.atStartOfDay(ZoneId.systemDefault()).toInstant()
                    val occurredAtEpochMs = occurredAt.toEpochMilli()
                    val currentLatest = latestImportFocus
                    if (currentLatest == null || occurredAtEpochMs > currentLatest.occurredAtEpochMs) {
                        latestImportFocus =
                            CsvImportFocus(
                                occurredAtEpochMs = occurredAtEpochMs,
                                currencyId = currencyId,
                            )
                    }
                    val transaction =
                        Transaction(
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
                    upsertImportedTransaction(
                        transaction.toImportEntity(
                            currency = currency,
                            deviceId = deviceId,
                            updatedAt = now,
                        ),
                        deviceId = deviceId,
                        updatedAt = now,
                    )
                }
            }
            return latestImportFocus
        }

        private suspend fun upsertImportedAccount(
            entity: AccountEntity,
            deviceId: String,
            updatedAt: Instant,
        ): Long {
            val persistedAt = updatedAt.toEpochMilli()
            val prepared =
                entity.copy(
                    uuid = entity.uuid.takeIf(String::isNotBlank) ?: UUID.randomUUID().toString(),
                    deviceId = deviceId,
                    updatedAt = persistedAt,
                )
            val id = database.accountDao().upsert(prepared).takeIf { prepared.id == 0L } ?: prepared.id
            val persisted = prepared.copy(id = id)
            database.operationDao().insert(
                operation(
                    deviceId = deviceId,
                    entityKind = EntityKind.Account,
                    entityUuid = persisted.uuid,
                    payload = payloadCodec.encodeAccount(persisted),
                    updatedAt = updatedAt,
                ),
            )
            return id
        }

        private suspend fun upsertImportedCategory(
            entity: CategoryEntity,
            deviceId: String,
            updatedAt: Instant,
        ): Long {
            val persistedAt = updatedAt.toEpochMilli()
            val prepared =
                entity.copy(
                    uuid = entity.uuid.takeIf(String::isNotBlank) ?: UUID.randomUUID().toString(),
                    deviceId = deviceId,
                    updatedAt = persistedAt,
                )
            val id = database.categoryDao().upsert(prepared).takeIf { prepared.id == 0L } ?: prepared.id
            val persisted = prepared.copy(id = id)
            database.operationDao().insert(
                operation(
                    deviceId = deviceId,
                    entityKind = EntityKind.Category,
                    entityUuid = persisted.uuid,
                    payload = payloadCodec.encodeCategory(persisted),
                    updatedAt = updatedAt,
                ),
            )
            return id
        }

        private suspend fun upsertImportedTransaction(
            entity: TransactionEntity,
            deviceId: String,
            updatedAt: Instant,
        ): Long {
            val persistedAt = updatedAt.toEpochMilli()
            val prepared =
                entity.copy(
                    uuid = entity.uuid.takeIf(String::isNotBlank) ?: UUID.randomUUID().toString(),
                    deviceId = deviceId,
                    updatedAt = persistedAt,
                )
            val id = database.transactionDao().upsert(prepared).takeIf { prepared.id == 0L } ?: prepared.id
            val persisted = prepared.copy(id = id)
            val accountUuid = ensureAccountUuid(persisted.accountId, deviceId, updatedAt)
            val categoryUuid = persisted.categoryId?.let { ensureCategoryUuid(it, deviceId, updatedAt) }
            val toAccountUuid = persisted.toAccountId?.let { ensureAccountUuid(it, deviceId, updatedAt) }
            database.operationDao().insert(
                operation(
                    deviceId = deviceId,
                    entityKind = EntityKind.Transaction,
                    entityUuid = persisted.uuid,
                    payload = payloadCodec.encodeTransaction(persisted, accountUuid, categoryUuid, toAccountUuid),
                    updatedAt = updatedAt,
                ),
            )
            return id
        }

        private suspend fun ensureAccountUuid(
            id: Long,
            deviceId: String,
            updatedAt: Instant,
        ): String {
            val entity = requireNotNull(database.accountDao().findById(id)) { "account not found: $id" }
            val uuid = entity.uuid.takeIf(String::isNotBlank) ?: UUID.randomUUID().toString()
            if (entity.uuid.isNotBlank() && entity.deviceId.isNotBlank()) {
                return uuid
            }
            upsertImportedAccount(
                entity.copy(uuid = uuid),
                deviceId = deviceId,
                updatedAt = updatedAt,
            )
            return uuid
        }

        private suspend fun ensureCategoryUuid(
            id: Long,
            deviceId: String,
            updatedAt: Instant,
        ): String {
            val entity = requireNotNull(database.categoryDao().findById(id)) { "category not found: $id" }
            val uuid = entity.uuid.takeIf(String::isNotBlank) ?: UUID.randomUUID().toString()
            if (entity.uuid.isNotBlank() && entity.deviceId.isNotBlank()) {
                return uuid
            }
            upsertImportedCategory(
                entity.copy(uuid = uuid),
                deviceId = deviceId,
                updatedAt = updatedAt,
            )
            return uuid
        }

        private fun operation(
            deviceId: String,
            entityKind: EntityKind,
            entityUuid: String,
            payload: String,
            updatedAt: Instant,
        ): OperationEntity =
            OperationEntity(
                opId = UUID.randomUUID().toString(),
                deviceId = deviceId,
                entityKind = entityKind.name,
                entityUuid = entityUuid,
                opType = OpType.Upsert.name,
                payload = payload,
                updatedAt = updatedAt.toEpochMilli(),
            )

        override suspend fun clearDatabase(): Result<Unit> =
            withContext(ioDispatcher) {
                runCatching {
                    database.clearAllTables()
                }
            }

        override suspend fun exportToFile(destAbsolutePath: String): Result<Unit> =
            withContext(ioDispatcher) {
                runCatching {
                    checkpoint()
                    val dbFile = context.getDatabasePath(DatabaseFileNames.DATABASE_NAME)
                    dbFile.copyTo(File(destAbsolutePath), overwrite = true)
                    Unit
                }
            }

        override suspend fun importFromFile(srcAbsolutePath: String): Result<Unit> =
            withContext(ioDispatcher) {
                runCatching {
                    val src = File(srcAbsolutePath)
                    val dbFile = context.getDatabasePath(DatabaseFileNames.DATABASE_NAME)
                    val dbDirectory = dbFile.parentFile ?: throw IOException("Cannot locate database directory")
                    val staged = File.createTempFile("monefy_restore_", ".db", dbDirectory)
                    try {
                        src.copyTo(staged, overwrite = true)
                        validateSqlite(staged)
                        requireCompatibleSchema(staged)

                        replaceDatabase(staged, dbFile)
                    } finally {
                        staged.delete()
                    }
                }
            }

        override suspend fun listLocalBackups(treeUriString: String): List<BackupFile> =
            withContext(ioDispatcher) {
                val tree = DocumentFile.fromTreeUri(context, Uri.parse(treeUriString)) ?: return@withContext emptyList()
                listBackups(tree).sortedByDescending { it.lastModifiedEpochMs }
            }

        override suspend fun rotateBackups(treeUriString: String): Result<Unit> =
            withContext(ioDispatcher) {
                runCatching {
                    val tree =
                        DocumentFile.fromTreeUri(context, Uri.parse(treeUriString))
                            ?: throw IOException("Cannot open backup directory")
                    BackupRepository.backupsToDelete(listBackups(tree)).forEach { backup ->
                        DocumentFile.fromSingleUri(context, Uri.parse(backup.uriString))?.delete()
                    }
                }
            }

        private fun listBackups(tree: DocumentFile): List<BackupFile> =
            tree
                .listFiles()
                .filter { it.isFile && it.name?.let(::isBackupName) == true }
                .map { BackupFile(it.name.orEmpty(), it.uri.toString(), it.lastModified()) }

        private fun checkpoint() {
            database.query("PRAGMA wal_checkpoint(FULL)", null).use { cursor ->
                check(cursor.moveToFirst()) { "Database checkpoint returned no row" }
                val busy = cursor.getInt(0)
                check(busy == 0) { "Database checkpoint is busy (result=$busy)" }
            }
        }

        private fun validateSqlite(file: File) {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { /* throws if invalid */ }
        }

        private fun requireCompatibleSchema(file: File) {
            val backupVersion =
                SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    db.version
                }
            if (backupVersion > MoneyDatabase.SCHEMA_VERSION) {
                throw BackupSchemaTooNewException(
                    backupVersion = backupVersion,
                    supportedVersion = MoneyDatabase.SCHEMA_VERSION,
                )
            }
        }

        private fun replaceDatabase(
            staged: File,
            dbFile: File,
        ) {
            check(staged.isFile) { "Staged database is not a file" }
            checkpoint()
            database.close()
            clearSidecars(dbFile)
            Files.move(
                staged.toPath(),
                dbFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }

        private fun clearSidecars(dbFile: File) {
            DatabaseFileNames.sidecarSuffixes.forEach { suffix ->
                val sidecar = File(dbFile.parentFile, dbFile.name + suffix)
                if (sidecar.exists()) {
                    check(sidecar.isFile) { "Database sidecar is not a file" }
                    check(sidecar.delete()) { "Cannot clear database sidecar" }
                }
                check(!sidecar.exists()) { "Database sidecar remains after cleanup" }
            }
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
                    closedQuote ->
                        when (char) {
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
                    else ->
                        when (char) {
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

        private fun invalidCsvRow(
            rowNumber: Int,
            reason: String,
        ): Nothing =
            throw IOException("Invalid CSV row $rowNumber: $reason")

        private companion object {
            const val BACKUP_PREFIX = "monefy_backup_"
            const val BACKUP_SUFFIX = ".db"
            const val MIME_TYPE = "application/octet-stream"
            const val MAX_NOTE_LENGTH = 256
            const val CSV_HEADER =
                "id,kind,amount,currency,account,category,note,occurredAt,createdAt,to_account,to_amount"
            val CSV_COLUMNS: List<String> = CSV_HEADER.split(",")
            const val CSV_LEGACY_COLUMN_COUNT = 9
            const val CSV_LINE_ENDING = "\r\n"
            const val AUTO_ACCOUNT_ICON = "ic_account_cash"
            const val AUTO_CATEGORY_ICON = "ic_cat_other"
            const val TEST_DEVICE_ID = "test-device"
            val AUTO_PALETTE: List<String> =
                listOf(
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
