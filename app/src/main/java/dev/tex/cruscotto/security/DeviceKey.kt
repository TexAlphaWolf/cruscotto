package dev.tex.cruscotto.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Non-extractable AES-256 key in the Android Keystore (StrongBox when the phone has one, otherwise TEE).
 *
 * It adds an outer layer around the PIN-wrapped database key. Without it, someone who copied the app's
 * files off the phone could try every PIN on a fast computer. With it, every guess must run on this
 * device, through the app, where the attempt limit applies.
 */
object DeviceKey {
    private const val ALIAS = "cruscotto_device_v1"
    private const val STORE = "AndroidKeyStore"
    private const val IV_BYTES = 12

    private fun keyStore(): KeyStore = KeyStore.getInstance(STORE).apply { load(null) }

    private fun spec(strongBox: Boolean) =
        KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .setIsStrongBoxBacked(strongBox)
            .build()

    /** Creates a fresh key, replacing any previous one. */
    fun create() {
        delete()
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE)
        try {
            gen.init(spec(strongBox = true))
            gen.generateKey()
        } catch (e: StrongBoxUnavailableException) {
            gen.init(spec(strongBox = false))
            gen.generateKey()
        }
    }

    fun exists(): Boolean = try { keyStore().containsAlias(ALIAS) } catch (_: Exception) { false }

    fun delete() {
        try { keyStore().deleteEntry(ALIAS) } catch (_: Exception) { }
    }

    private fun key(): SecretKey = keyStore().getKey(ALIAS, null) as? SecretKey
        ?: throw IllegalStateException("Device key missing")

    /** iv || ciphertext+tag. The Keystore chooses the IV. */
    fun seal(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return c.iv + c.doFinal(plain)
    }

    fun open(blob: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, IV_BYTES))
        return c.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
    }
}
