package com.kshavrin.mymoney.core.network.shared

data class SupabaseConfig(
    val url: String,
    val anonKey: String,
    val googleWebClientId: String = "",
) {
    val isConfigured: Boolean
        get() =
            url.isNotBlank() &&
                anonKey.isNotBlank() &&
                !url.startsWith(PLACEHOLDER_PREFIX) &&
                !anonKey.startsWith(PLACEHOLDER_PREFIX)

    val isGoogleSignInConfigured: Boolean
        get() =
            isConfigured &&
                googleWebClientId.isNotBlank() &&
                !googleWebClientId.startsWith(PLACEHOLDER_PREFIX)

    private companion object {
        const val PLACEHOLDER_PREFIX = "PLACEHOLDER_"
    }
}
