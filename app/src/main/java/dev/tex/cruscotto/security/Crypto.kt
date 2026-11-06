package dev.tex.cruscotto.security

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Pure crypto primitives (no Android dependencies), so they can be unit-tested on the JVM. */
object Crypto {
    const val KDF_ITERATIONS = 600_000
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12
    private val rng = SecureRandom()

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { rng.nextBytes(it) }

    /** PBKDF2-HMAC-SHA256 → AES-256 key. */
    fun deriveKey(secret: String, salt: ByteArray, iterations: Int = KDF_ITERATIONS): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, iterations, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** AES-GCM: returns iv || ciphertext+tag. */
    fun seal(key: ByteArray, plain: ByteArray): ByteArray {
        val iv = randomBytes(IV_BYTES)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return iv + c.doFinal(plain)
    }

    /** Opens a [seal] blob; throws if the key is wrong or the data was tampered with. */
    fun open(key: ByteArray, blob: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, blob, 0, IV_BYTES))
        return c.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
    }

    // ---------- recovery code ----------
    private const val B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun newRecoveryCode(): String =
        base32Encode(randomBytes(10)).take(16).chunked(4).joinToString("-")

    fun normalizeRecovery(code: String): String = code.uppercase().filter { it in 'A'..'Z' || it in '2'..'7' }

    // ---------- Base32 (RFC 4648, no padding) ----------
    fun base32Encode(bytes: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                sb.append(B32[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
            buffer = buffer and ((1 shl bits) - 1)
        }
        if (bits > 0) sb.append(B32[(buffer shl (5 - bits)) and 31])
        return sb.toString()
    }

    fun base32Decode(s: String): ByteArray {
        val clean = s.uppercase().filter { it in B32 }
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (ch in clean) {
            buffer = (buffer shl 5) or B32.indexOf(ch)
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xFF)
                bits -= 8
            }
            buffer = buffer and ((1 shl bits) - 1)
        }
        return out.toByteArray()
    }

    // ---------- TOTP (RFC 6238, SHA-1, 6 digits, 30 s) ----------
    fun totp(secretB32: String, step: Long, digits: Int = 6): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(base32Decode(secretB32), "HmacSHA1"))
        val h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array())
        val o = h[h.size - 1].toInt() and 0x0F
        val bin = ((h[o].toInt() and 0x7F) shl 24) or
            ((h[o + 1].toInt() and 0xFF) shl 16) or
            ((h[o + 2].toInt() and 0xFF) shl 8) or
            (h[o + 3].toInt() and 0xFF)
        var mod = 1
        repeat(digits) { mod *= 10 }
        return (bin % mod).toString().padStart(digits, '0')
    }

    fun verifyTotp(secretB32: String, code: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val digits = code.filter { it.isDigit() }
        if (digits.length != 6 || secretB32.isBlank()) return false
        val step = nowMillis / 30_000
        return (-1..1).any { d -> constantTimeEquals(totp(secretB32, step + d), digits) }
    }

    fun newTotpSecret(): String = base32Encode(randomBytes(20))

    fun otpAuthUri(secretB32: String, account: String = "Cruscotto"): String =
        "otpauth://totp/Cruscotto:$account?secret=$secretB32&issuer=Cruscotto&algorithm=SHA1&digits=6&period=30"

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var r = 0
        for (i in a.indices) r = r or (a[i].code xor b[i].code)
        return r == 0
    }
}
