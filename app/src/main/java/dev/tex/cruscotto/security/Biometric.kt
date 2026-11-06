package dev.tex.cruscotto.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES key in the hardware Keystore, usable only after strong biometric authentication.
 * It is invalidated when a new fingerprint or face is enrolled on the phone.
 */
object BioKeys {
    private const val ALIAS = "cruscotto_bio_v1"
    private const val STORE = "AndroidKeyStore"

    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    private fun keyStore(): KeyStore = KeyStore.getInstance(STORE).apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        (keyStore().getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE).run {
            init(spec)
            generateKey()
        }
    }

    fun deleteKey() {
        try { keyStore().deleteEntry(ALIAS) } catch (_: Exception) { }
    }

    /** Cipher that wraps the dbKey; use it inside BiometricPrompt. */
    fun encryptCipher(): Cipher? = try {
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }
    } catch (e: KeyPermanentlyInvalidatedException) {
        deleteKey()
        try { Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) } } catch (_: Exception) { null }
    } catch (e: Exception) {
        null
    }

    /** Cipher that unwraps the dbKey. Null when the key was invalidated (new biometric enrolled). */
    fun decryptCipher(iv: ByteArray): Cipher? {
        return try {
            val key = keyStore().getKey(ALIAS, null) as? SecretKey ?: return null
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            deleteKey(); null
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Shows the biometric prompt bound to [cipher].
 * [onError] receives null when the user cancelled, an empty string for a generic failure,
 * otherwise the system's (already localized) message.
 */
fun FragmentActivity.showBiometricPrompt(
    title: String,
    subtitle: String,
    negativeButton: String,
    cipher: Cipher,
    onSuccess: (Cipher) -> Unit,
    onError: (String?) -> Unit,
) {
    val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            val c = result.cryptoObject?.cipher
            if (c != null) onSuccess(c) else onError("")
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            val cancelled = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                errorCode == BiometricPrompt.ERROR_CANCELED
            onError(if (cancelled) null else errString.toString())
        }
    })
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setNegativeButtonText(negativeButton)
        .setAllowedAuthenticators(BIOMETRIC_STRONG)
        .setConfirmationRequired(false)
        .build()
    prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
}
