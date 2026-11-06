package dev.tex.cruscotto.session

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.tex.cruscotto.data.AppDatabase
import dev.tex.cruscotto.data.Defaults
import dev.tex.cruscotto.data.Repository
import dev.tex.cruscotto.prefs.AppPrefs
import dev.tex.cruscotto.prefs.Locales
import dev.tex.cruscotto.security.Crypto
import dev.tex.cruscotto.security.KeyVault
import dev.tex.cruscotto.ui.components.defaultCategoryName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

sealed interface AppState {
    data object NeedsSetup : AppState
    data object Locked : AppState
    data object AwaitOtp : AppState
    data class ShowRecovery(val code: String) : AppState
    data object OfferBiometric : AppState
    data object Unlocked : AppState
}

/**
 * Process-wide session. The database exists only while the app is unlocked:
 * on lock it is closed and the key is wiped from memory.
 */
object Session {
    private lateinit var appContext: Context
    lateinit var vault: KeyVault
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<AppState>(AppState.Locked)
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var db: AppDatabase? = null
    private var dbKey: ByteArray? = null
    var repo: Repository? = null
        private set

    /**
     * Set just before opening the system file picker or the authenticator app, which send this app to the
     * background: the next return to the foreground does not trigger the auto-lock.
     */
    @Volatile var suspendAutoLock = false
    private var backgroundSince = 0L

    /** Emits on every lock, so lock screens that are already showing (e.g. the recovery flow) can reset. */
    val lockRequests = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun init(context: Context) {
        appContext = context.applicationContext
        vault = KeyVault(appContext)
        _state.value = if (vault.exists()) AppState.Locked else AppState.NeedsSetup
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                backgroundSince = SystemClock.elapsedRealtime()
            }

            override fun onStart(owner: LifecycleOwner) {
                if (backgroundSince == 0L) return
                if (suspendAutoLock) {
                    suspendAutoLock = false
                    backgroundSince = 0L
                    return
                }
                val away = SystemClock.elapsedRealtime() - backgroundSince
                backgroundSince = 0L
                val limit = vault.autoLockMinutes() * 60_000L
                // Not while the recovery code is on screen: the user may have left to save it in a password manager.
                val s = _state.value
                val open = s == AppState.Unlocked || s == AppState.AwaitOtp || s == AppState.OfferBiometric || repo != null && s == AppState.Locked
                if (open && away >= limit) lock()
            }
        })
    }

    /** First launch: creates keys and database, then shows the recovery code. */
    suspend fun setup(pin: String) {
        // a database left without keys (e.g. partially cleared app data) could never be opened again
        appContext.deleteDatabase(AppDatabase.FILE)
        val (key, code) = withContext(Dispatchers.Default) { vault.create(pin) }
        open(key)
        _state.value = AppState.ShowRecovery(code)
    }

    fun recoveryAcknowledged(biometricAvailable: Boolean) {
        _state.value = if (biometricAvailable && !vault.hasBiometric()) AppState.OfferBiometric else AppState.Unlocked
    }

    fun biometricOfferDone() {
        _state.value = AppState.Unlocked
    }

    /** PIN unlock: false when wrong. */
    suspend fun unlockWithPin(pin: String): Boolean {
        val key = withContext(Dispatchers.Default) { vault.unlockWithPin(pin) } ?: return false
        open(key)
        afterKeyUnlock()
        return true
    }

    /** Recovery-code unlock: the session opens, then the UI asks for a new PIN. */
    suspend fun unlockWithRecovery(code: String): Boolean {
        val key = withContext(Dispatchers.Default) { vault.unlockWithRecovery(code) } ?: return false
        open(key)
        return true
    }

    suspend fun unlockWithBiometricKey(key: ByteArray) {
        open(key)
        afterKeyUnlock()
    }

    private suspend fun afterKeyUnlock() {
        val s = repo?.settingsOnce()
        _state.value = if (s?.totpOn == true) AppState.AwaitOtp else AppState.Unlocked
    }

    suspend fun verifyOtp(code: String): Boolean {
        if (vault.lockoutRemaining() > 0) return false
        val s = repo?.settingsOnce() ?: return false
        val ok = Crypto.verifyTotp(s.totpSecret, code)
        if (ok) _state.value = AppState.Unlocked else withContext(Dispatchers.Default) { vault.registerFail() }
        return ok
    }

    /** After recovery: new PIN on the same key. */
    suspend fun setNewPinAfterRecovery(pin: String) {
        val key = dbKey ?: return
        withContext(Dispatchers.Default) { vault.changePin(key, pin) }
        _state.value = AppState.Unlocked
    }

    suspend fun changePin(current: String, newPin: String): Boolean {
        val key = dbKey ?: return false
        val ok = withContext(Dispatchers.Default) { vault.checkPin(current) }
        if (!ok) return false
        withContext(Dispatchers.Default) { vault.changePin(key, newPin) }
        return true
    }

    /** Copy of the key, only for wrapping it with biometrics (wipe it after use). */
    fun currentKeyCopy(): ByteArray? = dbKey?.copyOf()

    private suspend fun open(key: ByteArray) {
        dbKey?.fill(0)
        dbKey = key.copyOf()
        val database = withContext(Dispatchers.IO) { AppDatabase.open(appContext, key) }
        key.fill(0)
        val r = Repository(database)
        withContext(Dispatchers.IO) {
            val ctx = Locales.current(appContext)
            val nameOf = { id: String -> ctx.getString(defaultCategoryName(id)) }
            r.ensureDefaults(nameOf)
            r.relocalizeDefaults(defaultNamesInAllLanguages(), nameOf)
            purgeIfDue(r)
        }
        db = database
        repo = r
    }

    /** After a language change: renames the untouched default categories to the new language. */
    fun relocalizeDefaults() {
        val r = repo ?: return
        scope.launch(Dispatchers.IO) {
            val ctx = Locales.current(appContext)
            runCatching { r.relocalizeDefaults(defaultNamesInAllLanguages()) { id -> ctx.getString(defaultCategoryName(id)) } }
        }
    }

    /** Default category names in every app language, to recognise the ones the user never renamed. */
    private fun defaultNamesInAllLanguages(): Map<String, Set<String>> {
        val ctxs = listOf("en", "de", "it").map { Locales.forTag(appContext, it) }
        return (Defaults.outCategories + Defaults.inCategories).associate { c ->
            c.id to ctxs.map { it.getString(defaultCategoryName(c.id)) }.toSet()
        }
    }

    /**
     * First unlock on or after 1 January: deletes the previous years' data (as chosen in Settings).
     * The database is encrypted, so this can only happen while unlocked.
     */
    private suspend fun purgeIfDue(r: Repository) {
        val year = LocalDate.now().year
        if (AppPrefs.lastPurgeYear >= year) return
        if (AppPrefs.purgeEnabled) {
            r.purgeBefore(LocalDate.of(year, 1, 1), AppPrefs.purgeTxns, AppPrefs.purgeLogs)
            AppPrefs.purgedNoticeYear = year - 1
        }
        AppPrefs.lastPurgeYear = year
    }

    fun lock() {
        if (_state.value == AppState.NeedsSetup) return
        lockRequests.tryEmit(Unit)
        _state.value = AppState.Locked
        val oldDb = db
        db = null
        repo = null
        dbKey?.fill(0)
        dbKey = null
        // delayed close: gives the screens time to stop reading
        scope.launch {
            delay(500)
            oldDb?.close()
        }
    }
}
