package com.kshavrin.mymoney.feature.transactionslist.list

enum class RecordSort { TotalDesc, TotalAsc }

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
}
