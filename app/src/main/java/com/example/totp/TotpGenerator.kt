package com.example.totp

import java.security.GeneralSecurityException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.and

object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun isValidBase32(secret: String): Boolean {
        val clean = secret.uppercase().replace(" ", "").replace("-", "").trim()
        if (clean.isEmpty()) return false
        // Remove trailing padding '=' if present
        val noPadding = clean.trimEnd('=')
        if (noPadding.isEmpty()) return false
        for (char in noPadding) {
            if (char !in ALPHABET) return false
        }
        return true
    }

    fun decode(secret: String): ByteArray {
        val clean = secret.uppercase().replace(" ", "").replace("-", "").trim().trimEnd('=')
        var buffer = 0
        var bitsLeft = 0
        val result = ByteArray(clean.length * 5 / 8)
        var count = 0

        for (char in clean) {
            val value = ALPHABET.indexOf(char)
            if (value < 0) continue
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                result[count++] = ((buffer shr (bitsLeft - 8)) and 0xFF).toByte()
                bitsLeft -= 8
            }
        }
        return result.copyOf(count)
    }
}

object TotpGenerator {
    private const val TIME_STEP_SECONDS = 30L
    private const val CODE_DIGITS = 6

    fun generateTotp(
        secretBase32: String,
        timeSeconds: Long = System.currentTimeMillis() / 1000L
    ): String {
        if (!Base32.isValidBase32(secretBase32)) {
            throw IllegalArgumentException("Invalid Base32 Secret")
        }
        val keyBytes = Base32.decode(secretBase32)
        if (keyBytes.isEmpty()) {
            throw IllegalArgumentException("Decoded key is empty")
        }
        val counter = timeSeconds / TIME_STEP_SECONDS
        return generateHmacSha1Totp(keyBytes, counter)
    }

    fun getRemainingSeconds(timeSeconds: Long = System.currentTimeMillis() / 1000L): Int {
        val rem = (timeSeconds % TIME_STEP_SECONDS).toInt()
        return (TIME_STEP_SECONDS.toInt() - rem)
    }

    fun getProgressFraction(timeSeconds: Long = System.currentTimeMillis() / 1000L): Float {
        val remaining = getRemainingSeconds(timeSeconds)
        return remaining / 30f
    }

    private fun generateHmacSha1Totp(key: ByteArray, counter: Long): String {
        val data = ByteArray(8)
        var tempCounter = counter
        for (i in 7 downTo 0) {
            data[i] = (tempCounter and 0xFF).toByte()
            tempCounter = tempCounter shr 8
        }

        val mac = Mac.getInstance("HmacSHA1")
        val signKey = SecretKeySpec(key, "HmacSHA1")
        mac.init(signKey)
        val hash = mac.doFinal(data)

        val offset = (hash[hash.size - 1] and 0x0F).toInt()
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)

        val otp = binary % 1_000_000
        return String.format("%06d", otp)
    }
}
