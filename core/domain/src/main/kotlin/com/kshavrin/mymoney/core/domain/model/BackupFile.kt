package com.kshavrin.mymoney.core.domain.model

data class BackupFile(
    val name: String,
    val uriString: String,
    val lastModifiedEpochMs: Long,
)
