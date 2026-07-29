package com.kshavrin.mymoney.core.network.shared

import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

data class InviteToken(
    val plaintext: String,
    val hash: String,
)

@Singleton
class InviteTokenFactory
    @Inject
    constructor(
        private val random: SecureRandom,
    ) {
        fun newToken(): InviteToken {
            val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
            val plaintext = bytes.toHex()
            return InviteToken(plaintext = plaintext, hash = hash(plaintext))
        }

        // Must match join_workspace()'s server-side encode(digest(token,'sha256'),'hex').
        fun hash(token: String): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(token.toByteArray(Charsets.UTF_8))
                .toHex()

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

        private companion object {
            const val TOKEN_BYTES = 32
        }
    }
