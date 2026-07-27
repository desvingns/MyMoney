package com.kshavrin.mymoney.core.common.database

object DatabaseFileNames {
    const val DATABASE_NAME = "mymoney.db"
    const val LEGACY_DATABASE_NAME = "monefy.db"

    val sidecarSuffixes: List<String> = listOf("-shm", "-wal")
}
