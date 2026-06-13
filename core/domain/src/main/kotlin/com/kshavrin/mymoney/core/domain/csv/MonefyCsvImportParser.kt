package com.kshavrin.mymoney.core.domain.csv

import com.kshavrin.mymoney.core.domain.model.TransactionKind
import java.io.IOException
import java.io.PushbackReader
import java.io.Reader
import java.io.StringReader
import java.math.BigDecimal
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class CsvImportFormat {
    MyMoney,
    Monefy,
    Unknown,
}

data class MonefyCsvRow(
    val date: LocalDate,
    val accountName: String,
    val categoryName: String,
    val kind: TransactionKind,
    val amount: BigDecimal,
    val currencyCode: String,
    val note: String?,
)

data class MonefyCsvImport(
    val format: CsvImportFormat,
    val rows: List<MonefyCsvRow>,
)

object MonefyCsvImportParser {
    val MONEFY_HEADER: List<String> =
        listOf("date", "account", "category", "amount", "currency", "converted amount", "currency", "description")

    val MYMONEY_HEADER: List<String> =
        listOf("id", "kind", "amount", "currency", "account", "category", "note", "occurredAt", "createdAt")

    // Additive extension carrying transfer destination columns; legacy 9-column files stay importable.
    val MYMONEY_TRANSFER_HEADER: List<String> =
        MYMONEY_HEADER + listOf("to_account", "to_amount")

    private const val MAX_NOTE_LENGTH = 256

    private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    // Java's \s excludes Unicode spaces such as U+00A0 (which Monefy emits), so match any
    // Unicode whitespace plus the explicit non-breaking space to collapse them all.
    private val WHITESPACE_RUN = Regex("[\\s\\p{Z}\\u00A0]+")

    /**
     * Canonical key for matching an imported entity name against an existing one.
     * Trim, Unicode-normalize (NFC), collapse internal whitespace runs (including the
     * non-breaking spaces Monefy emits) to a single ASCII space, then lowercase.
     * Both the CSV name and the stored entity name must pass through this identically
     * so "Кафе и  рестораны" merges into the seeded "Кафе и рестораны".
     */
    fun normalizeName(raw: String): String =
        Normalizer
            .normalize(raw, Normalizer.Form.NFC)
            .replace(WHITESPACE_RUN, " ")
            .trim()
            .lowercase(Locale.ROOT)

    fun parseText(text: String): MonefyCsvImport {
        val records = tokenize(StringReader(text))
        val format = detectFormat(records.firstOrNull())
        val rows = if (format == CsvImportFormat.Monefy) parse(records) else emptyList()
        return MonefyCsvImport(format = format, rows = rows)
    }

    fun detectFormat(header: List<String>?): CsvImportFormat =
        when {
            header == null -> CsvImportFormat.Unknown
            header.map { it.trim() } == MONEFY_HEADER -> CsvImportFormat.Monefy
            header == MYMONEY_HEADER || header == MYMONEY_TRANSFER_HEADER -> CsvImportFormat.MyMoney
            else -> CsvImportFormat.Unknown
        }

    fun parse(records: List<List<String>>): List<MonefyCsvRow> =
        records.drop(1).mapIndexed { index, fields ->
            val rowNumber = index + 2
            if (fields.size < MONEFY_HEADER.size) {
                throw IOException("Invalid Monefy CSV row $rowNumber: expected ${MONEFY_HEADER.size} fields")
            }
            val date =
                runCatching { LocalDate.parse(fields[0].trim(), DATE_FORMATTER) }
                    .getOrElse { throw IOException("Invalid Monefy CSV row $rowNumber: date must be dd/MM/yyyy") }
            val accountName = fields[1].trim()
            val categoryName = fields[2].trim()
            val amount =
                parseAmount(fields[3])
                    ?: throw IOException("Invalid Monefy CSV row $rowNumber: amount must be a number")
            if (amount.signum() == 0) {
                throw IOException("Invalid Monefy CSV row $rowNumber: amount must not be zero")
            }
            val kind = if (amount.signum() < 0) TransactionKind.Expense else TransactionKind.Income
            val currencyCode = fields[4].trim()
            val note = fields[7].ifBlank { null }?.take(MAX_NOTE_LENGTH)
            MonefyCsvRow(
                date = date,
                accountName = accountName,
                categoryName = categoryName,
                kind = kind,
                amount = amount.abs(),
                currencyCode = currencyCode,
                note = note,
            )
        }

    fun tokenize(reader: Reader): List<List<String>> {
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

    // Monefy groups thousands with U+00A0 (non-breaking space, not ASCII space) and uses ',' as the
    // decimal separator, so both space variants must be stripped before swapping comma for a dot.
    private fun parseAmount(raw: String): BigDecimal? {
        val normalized =
            raw
                .replace(" ", "")
                .replace(" ", "")
                .replace(',', '.')
                .trim()
        if (normalized.isEmpty()) return null
        return normalized.toBigDecimalOrNull()
    }
}
