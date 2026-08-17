package com.kshavrin.mymoney.core.network.shared

import javax.inject.Inject

class SignInWithGoogleUseCase
    @Inject
    constructor(
        private val sharedAuth: SharedAuth,
    ) {
        suspend operator fun invoke(
            googleIdToken: String,
            nonce: String,
        ): Result<SharedSession> = sharedAuth.signInWithGoogle(googleIdToken, nonce)
    }
