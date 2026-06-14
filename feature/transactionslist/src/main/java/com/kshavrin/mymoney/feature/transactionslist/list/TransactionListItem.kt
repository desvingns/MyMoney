package com.kshavrin.mymoney.feature.transactionslist.list

enum class RecordSort { TotalDesc, TotalAsc }

enum class RecordsTab { Operations, Transfers }

object RecordsTestTags {
    const val BALANCE = "records_balance"
    const val SORT = "records_sort"
    const val EMPTY = "records_empty"
    const val FILTER = "records_filter"

    fun category(id: Long): String = "records_category_$id"

    fun chevron(id: Long): String = "records_chevron_$id"

    fun count(id: Long): String = "records_count_$id"

    fun total(id: Long): String = "records_total_$id"

    fun transaction(id: Long): String = "records_tx_$id"

    fun transactionNote(id: Long): String = "records_tx_note_$id"

    const val TAB_OPERATIONS = "records_tab_operations"
    const val TAB_TRANSFERS = "records_tab_transfers"
    const val TRANSFERS_EMPTY = "records_transfers_empty"

    fun transfer(id: Long): String = "records_transfer_$id"
}
