package com.example.core.common

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object CryptoUtils {

    const val PBKDF2_ITERATIONS = 120_000
    const val PBKDF2_KEY_LENGTH_BITS = 256
    const val PBKDF2_SALT_BYTES = 16

    /** Short random access code (no look-alike characters) for sharing readings on the home network. */
    fun generateToken(length: Int = 10): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        return buildString {
            repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) }
        }
    }

    fun sha256(value: String): String {
        if (value.trim().isEmpty()) return ""
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(value.trim().toByteArray(Charsets.UTF_8))
            toHexString(digest)
        } catch (e: Exception) {
            ""
        }
    }

    fun sha1(value: String): String {
        if (value.trim().isEmpty()) return ""
        return try {
            val md = MessageDigest.getInstance("SHA-1")
            val digest = md.digest(value.trim().toByteArray(Charsets.UTF_8))
            toHexString(digest)
        } catch (e: Exception) {
            ""
        }
    }

    fun hashPinPbkdf2(pin: String): String {
        if (pin.isBlank()) return ""
        val salt = ByteArray(PBKDF2_SALT_BYTES).apply {
            SecureRandom().nextBytes(this)
        }
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH_BITS)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = skf.generateSecret(spec).encoded
        return "${toHexString(salt)}:${toHexString(hash)}"
    }

    sealed interface PinVerificationResult {
        data class Valid(val migratedHash: String?) : PinVerificationResult
        data object INVALID : PinVerificationResult
    }

    fun verifyPin(enteredPin: String, storedPinHash: String): PinVerificationResult {
        if (storedPinHash.isBlank()) return PinVerificationResult.INVALID
        if (!storedPinHash.contains(":")) {
            // Legacy SHA-256 compatibility
            val enteredSha256 = sha256(enteredPin)
            if (MessageDigest.isEqual(enteredSha256.toByteArray(Charsets.UTF_8), storedPinHash.toByteArray(Charsets.UTF_8))) {
                val newHash = hashPinPbkdf2(enteredPin)
                return PinVerificationResult.Valid(migratedHash = newHash)
            }
            return PinVerificationResult.INVALID
        }

        val parts = storedPinHash.split(":")
        if (parts.size != 2) return PinVerificationResult.INVALID
        return try {
            val salt = hexToByteArray(parts[0])
            val expectedHash = hexToByteArray(parts[1])

            val spec = PBEKeySpec(enteredPin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH_BITS)
            val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val actualHash = skf.generateSecret(spec).encoded

            if (MessageDigest.isEqual(actualHash, expectedHash)) {
                PinVerificationResult.Valid(migratedHash = null)
            } else {
                PinVerificationResult.INVALID
            }
        } catch (e: Exception) {
            PinVerificationResult.INVALID
        }
    }

    fun toHexString(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        val hexArray = "0123456789abcdef".toCharArray()
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = hexArray[v ushr 4]
            hexChars[i * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }

    fun hexToByteArray(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
