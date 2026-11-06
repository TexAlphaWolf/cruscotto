package dev.tex.cruscotto.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Language tag, or empty for the phone's language. */
enum class AppLanguage(val tag: String) { SYSTEM(""), ENGLISH("en"), GERMAN("de"), ITALIAN("it") }

/**
 * Non-sensitive app preferences, in plain SharedPreferences outside the encrypted database: the lock
 * screen, the language setup and the year-end reminder need them before anything is unlocked.
 */
object AppPrefs {
    private const val FILE = "ui_prefs"
    private const val K_THEME = "theme_mode"
    private const val K_LANG = "language"
    private const val K_PURGE_TXNS = "purge_txns"
    private const val K_PURGE_LOGS = "purge_logs"
    private const val K_LAST_PURGE_YEAR = "last_purge_year"
    private const val K_PURGED_NOTICE = "purged_notice_year"
    private const val K_NOTIF_ASKED = "notif_asked"

    private lateinit var prefs: SharedPreferences
    private val _theme = MutableStateFlow(ThemeMode.SYSTEM)
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private fun open(context: Context): SharedPreferences =
        context.applicationContext?.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            ?: context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun init(context: Context) {
        prefs = open(context)
        _theme.value = runCatching { ThemeMode.valueOf(prefs.getString(K_THEME, null) ?: "") }.getOrDefault(ThemeMode.SYSTEM)
        if (!prefs.contains(K_LAST_PURGE_YEAR)) {
            // first launch: nothing to clean up until next year
            prefs.edit().putInt(K_LAST_PURGE_YEAR, LocalDate.now().year).apply()
        }
    }

    fun setTheme(mode: ThemeMode) {
        _theme.value = mode
        prefs.edit().putString(K_THEME, mode.name).apply()
    }

    /** Read directly from a base context: used in attachBaseContext, before [init] runs. */
    fun language(context: Context): AppLanguage {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return runCatching { AppLanguage.valueOf(p.getString(K_LANG, null) ?: "") }.getOrDefault(AppLanguage.SYSTEM)
    }

    val language: AppLanguage
        get() = runCatching { AppLanguage.valueOf(prefs.getString(K_LANG, null) ?: "") }.getOrDefault(AppLanguage.SYSTEM)

    /** Saved synchronously: the activity is recreated right after. */
    fun setLanguage(lang: AppLanguage) {
        prefs.edit().putString(K_LANG, lang.name).commit()
    }

    var purgeTxns: Boolean
        get() = prefs.getBoolean(K_PURGE_TXNS, true)
        set(v) { prefs.edit().putBoolean(K_PURGE_TXNS, v).apply() }

    var purgeLogs: Boolean
        get() = prefs.getBoolean(K_PURGE_LOGS, true)
        set(v) { prefs.edit().putBoolean(K_PURGE_LOGS, v).apply() }

    val purgeEnabled: Boolean get() = purgeTxns || purgeLogs

    var lastPurgeYear: Int
        get() = prefs.getInt(K_LAST_PURGE_YEAR, LocalDate.now().year)
        set(v) { prefs.edit().putInt(K_LAST_PURGE_YEAR, v).apply() }

    /** Year whose data was just deleted, shown once as a notice; 0 = nothing to show. */
    var purgedNoticeYear: Int
        get() = prefs.getInt(K_PURGED_NOTICE, 0)
        set(v) { prefs.edit().putInt(K_PURGED_NOTICE, v).apply() }

    var notificationAsked: Boolean
        get() = prefs.getBoolean(K_NOTIF_ASKED, false)
        set(v) { prefs.edit().putBoolean(K_NOTIF_ASKED, v).apply() }
}
