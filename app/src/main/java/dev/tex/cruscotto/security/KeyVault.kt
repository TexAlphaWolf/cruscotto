package dev.tex.cruscotto.security

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.util.Base64

/**
 * Key vault. The database is encrypted (SQLCipher) with a random 32-byte key (dbKey) that is never
 * stored in clear. This file only holds wrapped copies of it:
 *  - PIN:      DeviceKey( AES-GCM( PBKDF2-SHA256(PIN, 600k), dbKey ) )  — usable only on this phone
 *  - recovery: AES-GCM( PBKDF2-SHA256(recovery code, 600k), dbKey )     — 80-bit code, not device-bound
 *  - biometric (optional): AES-GCM with a Keystore key that requires strong biometrics
 * The file lives in noBackupFilesDir, so it is excluded from backups and device transfers.
 */
class KeyVault(context: Context) {

    data class State(
        val version: Int,
        val kSalt: String,
        val kIter: Int,
        val wrapPin: String,
        val rSalt: String,
        val rIter: Int,
        val wrapRec: String,
        val bioIv: String? = null,
        val bioWrap: String? = null,
        val fails: Int = 0,
        val lockUntil: Long = 0,
        val autoLockMin: Int = 1,
    )

    private val file = AtomicFile(File(context.noBackupFilesDir, "vault.json"))
    private val b64e = Base64.getEncoder()
    private val b64d = Base64.getDecoder()

    @Volatile private var cached: State? = null

    fun exists(): Boolean = load() != null

    fun load(): State? {
        cached?.let { return it }
        return try {
            val o = JSONObject(String(file.readFully(), Charsets.UTF_8))
            State(
                version = o.optInt("v", 1),
                kSalt = o.getString("kSalt"), kIter = o.getInt("kIter"), wrapPin = o.getString("wrapPin"),
                rSalt = o.getString("rSalt"), rIter = o.getInt("rIter"), wrapRec = o.getString("wrapRec"),
                bioIv = o.optString("bioIv").ifEmpty { null }, bioWrap = o.optString("bioWrap").ifEmpty { null },
                fails = o.optInt("fails", 0), lockUntil = o.optLong("lockUntil", 0),
                autoLockMin = o.optInt("autoLockMin", 1),
            ).also { cached = it }
        } catch (e: Exception) {
            null
        }
    }

    private fun save(s: State) {
        val o = JSONObject()
            .put("v", s.version)
            .put("kSalt", s.kSalt).put("kIter", s.kIter).put("wrapPin", s.wrapPin)
            .put("rSalt", s.rSalt).put("rIter", s.rIter).put("wrapRec", s.wrapRec)
            .put("bioIv", s.bioIv ?: "").put("bioWrap", s.bioWrap ?: "")
            .put("fails", s.fails).put("lockUntil", s.lockUntil).put("autoLockMin", s.autoLockMin)
        val out = file.startWrite()
        try {
            out.write(o.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
            cached = s
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }

    /** PIN layer: PBKDF2 + AES-GCM, then the device-bound Keystore layer. Returns (salt, blob). */
    private fun wrapWithPin(dbKey: ByteArray, pin: String): Pair<ByteArray, ByteArray> {
        if (!DeviceKey.exists()) DeviceKey.create()
        val salt = Crypto.randomBytes(16)
        val inner = Crypto.seal(Crypto.deriveKey(pin, salt), dbKey)
        return salt to DeviceKey.seal(inner)
    }

    /** First launch: creates dbKey and wraps it with the PIN and a new recovery code. Returns (dbKey, code). */
    fun create(pin: String): Pair<ByteArray, String> {
        DeviceKey.create()
        BioKeys.deleteKey()
        val dbKey = Crypto.randomBytes(32)
        val code = Crypto.newRecoveryCode()
        val (kSalt, wrapPin) = wrapWithPin(dbKey, pin)
        val rSalt = Crypto.randomBytes(16)
        val wrapRec = Crypto.seal(Crypto.deriveKey(Crypto.normalizeRecovery(code), rSalt), dbKey)
        save(
            State(
                version = 2,
                kSalt = b64e.encodeToString(kSalt), kIter = Crypto.KDF_ITERATIONS, wrapPin = b64e.encodeToString(wrapPin),
                rSalt = b64e.encodeToString(rSalt), rIter = Crypto.KDF_ITERATIONS, wrapRec = b64e.encodeToString(wrapRec),
            )
        )
        return dbKey to code
    }

    /** Milliseconds left to wait after too many wrong attempts. */
    fun lockoutRemaining(now: Long = System.currentTimeMillis()): Long =
        ((load()?.lockUntil ?: 0) - now).coerceAtLeast(0)

    fun unlockWithPin(pin: String): ByteArray? {
        val s = load() ?: return null
        if (lockoutRemaining() > 0) return null
        val blob = b64d.decode(s.wrapPin)
        val inner = if (s.version >= 2) {
            // A missing or broken device key cannot be fixed by guessing: only the recovery code helps.
            try { DeviceKey.open(blob) } catch (e: Exception) { return null }
        } else blob
        val key = try {
            Crypto.open(Crypto.deriveKey(pin, b64d.decode(s.kSalt), s.kIter), inner)
        } catch (e: Exception) {
            registerFail(); return null
        }
        resetFails()
        // migrate the old format to the device-bound one; if the Keystore fails, keep the old format
        if (s.version < 2) runCatching { changePin(key, pin) }
        return key
    }

    /** True when the PIN can no longer work on this phone (device key lost) and recovery is needed. */
    fun pinNeedsRecovery(): Boolean = (load()?.version ?: 0) >= 2 && !DeviceKey.exists()

    fun unlockWithRecovery(code: String): ByteArray? {
        val s = load() ?: return null
        if (lockoutRemaining() > 0) return null
        val norm = Crypto.normalizeRecovery(code)
        if (norm.length < 16) { registerFail(); return null }
        return try {
            Crypto.open(Crypto.deriveKey(norm, b64d.decode(s.rSalt), s.rIter), b64d.decode(s.wrapRec)).also { resetFails() }
        } catch (e: Exception) {
            registerFail(); null
        }
    }

    /** Checks the PIN (wrong attempts count towards the wait, as on the lock screen). */
    fun checkPin(pin: String): Boolean = unlockWithPin(pin)?.also { it.fill(0) } != null

    /** New PIN: re-wraps the same dbKey; the recovery code stays valid. */
    fun changePin(dbKey: ByteArray, newPin: String) {
        val s = load() ?: return
        val (salt, blob) = wrapWithPin(dbKey, newPin)
        save(
            s.copy(
                version = 2,
                kSalt = b64e.encodeToString(salt), kIter = Crypto.KDF_ITERATIONS, wrapPin = b64e.encodeToString(blob),
                fails = 0, lockUntil = 0,
            )
        )
    }

    fun registerFail() {
        val s = load() ?: return
        val fails = s.fails + 1
        val wait = if (fails >= 5) minOf(30_000L shl (fails - 5).coerceAtMost(7), 3_600_000L) else 0L
        save(s.copy(fails = fails, lockUntil = if (wait > 0) System.currentTimeMillis() + wait else 0))
    }

    fun remainingAttemptsBeforeWait(): Int = (5 - (load()?.fails ?: 0)).coerceAtLeast(0)

    private fun resetFails() {
        val s = load() ?: return
        if (s.fails != 0 || s.lockUntil != 0L) save(s.copy(fails = 0, lockUntil = 0))
    }

    // ---------- biometrics ----------
    fun hasBiometric(): Boolean = load()?.let { it.bioIv != null && it.bioWrap != null } == true
    fun biometricIv(): ByteArray? = load()?.bioIv?.let { b64d.decode(it) }
    fun biometricWrapped(): ByteArray? = load()?.bioWrap?.let { b64d.decode(it) }

    fun setBiometric(iv: ByteArray, wrapped: ByteArray) {
        val s = load() ?: return
        save(s.copy(bioIv = b64e.encodeToString(iv), bioWrap = b64e.encodeToString(wrapped)))
    }

    fun clearBiometric() {
        val s = load() ?: return
        save(s.copy(bioIv = null, bioWrap = null))
        BioKeys.deleteKey()
    }

    // ---------- auto-lock (minutes in background; 0 = immediately) ----------
    fun autoLockMinutes(): Int = load()?.autoLockMin ?: 1
    fun setAutoLockMinutes(min: Int) {
        val s = load() ?: return
        save(s.copy(autoLockMin = min))
    }
}
