package com.kshavrin.mymoney.feature.transactionslist.list

object RecordsTestTags {
    const val EMPTY = "records_empty"
    const val FILTER = "records_filter"
    const val LIST = "records_list"

    fun transaction(id: Long): String = "records_tx_$id"

    fun transfer(id: Long): String = "records_transfer_$id"
}
