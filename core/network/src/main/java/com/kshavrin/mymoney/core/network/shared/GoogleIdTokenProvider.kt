package com.kshavrin.mymoney.core.network.shared

import android.app.Activity

data class GoogleIdTokenResult(
    val idToken: String,
    val nonce: String,
)

interface GoogleIdTokenProvider {
    suspend fun fetchIdToken(activity: Activity): Result<GoogleIdTokenResult>
}
