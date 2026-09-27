package com.kshavrin.mymoney.core.domain.csv

import java.io.IOException

object MyMoneyCsvTextEncoding {
    const val VERSION = "apostrophe-v1"
    val TEXT_COLUMNS = setOf(3, 4, 5, 6, 9)

    fun encode(value: String): String = if (value.isEmpty()) value else "'$value"

    fun decodeRow(fields: List<String>): List<String> {
        if (fields.lastOrNull() != VERSION || fields.size != MonefyCsvImportParser.MYMONEY_SAFE_HEADER.size) {
            throw IOException("Invalid MyMoney CSV text encoding")
        }
        return fields.dropLast(1).mapIndexed { index, value ->
            if (index !in TEXT_COLUMNS || value.isEmpty()) {
                value
            } else if (value.startsWith("'")) {
                value.drop(1)
            } else {
                throw IOException("Invalid encoded MyMoney CSV text field")
            }
        }
    }
}
