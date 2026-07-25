package com.kshavrin.mymoney.core.datastore

enum class CloudProvider {
    Dropbox,
    GoogleDrive,
}

data class CloudBinding(
    val provider: CloudProvider,
    val stableAccountId: String,
    val accountLabel: String,
)
