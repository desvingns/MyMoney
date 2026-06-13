package com.kshavrin.mymoney.feature.lockscreen.setup

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject

class PinHasher
    @Inject
    constructor() {
        fun hash(
            pin: String,
            salt: ByteArray = randomSalt(),
        ): String {
            val derived = derive(pin, salt, CURRENT_ITERATIONS)
            return "$CURRENT_VERSION:$CURRENT_ITERATIONS:${encode(salt)}:${encode(derived)}"
        }

        fun verify(
            pin: String,
            stored: String,
        ): Boolean = verifyDetailed(pin, stored).verified

        fun verifyDetailed(
            pin: String,
            stored: String,
        ): PinVerificationResult {
            val parsed = parse(stored) ?: return PinVerificationResult(verified = false, needsRehash = false)
            val actual = derive(pin, parsed.salt, parsed.iterations)
            val verified = constantTimeEquals(parsed.derivedKey, actual)
            return PinVerificationResult(verified = verified, needsRehash = verified && parsed.needsRehash)
        }

        fun isCurrentFormat(stored: String): Boolean = parse(stored)?.needsRehash == false

        private fun parse(stored: String): ParsedHash? {
            val parts = stored.split(":")
            return when {
                parts.size == 4 && parts[0] == CURRENT_VERSION -> {
                    val iterations = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
                    val salt = decode(parts[2]) ?: return null
                    val derivedKey = decode(parts[3]) ?: return null
                    ParsedHash(
                        iterations = iterations,
                        salt = salt,
                        derivedKey = derivedKey,
                        needsRehash = iterations != CURRENT_ITERATIONS,
                    )
                }

                parts.size == 2 -> {
                    val salt = decode(parts[0]) ?: return null
                    val derivedKey = decode(parts[1]) ?: return null
                    ParsedHash(
                        iterations = LEGACY_ITERATIONS,
                        salt = salt,
                        derivedKey = derivedKey,
                        needsRehash = true,
                    )
                }

                else -> null
            }
        }

        private fun derive(
            pin: String,
            salt: ByteArray,
            iterations: Int,
        ): ByteArray {
            val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        }

        private fun randomSalt(): ByteArray = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }

        private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

        private fun decode(value: String): ByteArray? = runCatching { Base64.getDecoder().decode(value) }.getOrNull()

        private fun constantTimeEquals(
            a: ByteArray,
            b: ByteArray,
        ): Boolean {
            if (a.size != b.size) return false
            var diff = 0
            for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
            return diff == 0
        }

        private companion object {
            const val CURRENT_VERSION = "v2"
            const val ALGORITHM = "PBKDF2WithHmacSHA256"
            const val CURRENT_ITERATIONS = 600_000
            const val LEGACY_ITERATIONS = 10_000
            const val KEY_LENGTH_BITS = 256
            const val SALT_LENGTH_BYTES = 16
        }
    }

data class PinVerificationResult(
    val verified: Boolean,
    val needsRehash: Boolean,
)

private data class ParsedHash(
    val iterations: Int,
    val salt: ByteArray,
    val derivedKey: ByteArray,
    val needsRehash: Boolean,
)
