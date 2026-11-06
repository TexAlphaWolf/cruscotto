package dev.tex.cruscotto.prefs

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import java.util.Locale

/** Applies the language chosen in the app (or leaves the phone's one) to a context. */
object Locales {
    fun wrap(base: Context): Context {
        val lang = AppPrefs.language(base)
        if (lang == AppLanguage.SYSTEM) {
            // back to the phone's language, also for formats, after a previous in-app choice
            base.resources.configuration.locales.takeIf { !it.isEmpty }?.let { Locale.setDefault(it[0]) }
            return base
        }
        val locale = Locale.forLanguageTag(lang.tag)
        Locale.setDefault(locale) // number and date formats follow the chosen language
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }

    /** A context in the language currently chosen in the app, even if [context] was created earlier. */
    fun current(context: Context): Context {
        val lang = AppPrefs.language(context)
        val locales = if (lang == AppLanguage.SYSTEM) Resources.getSystem().configuration.locales else LocaleList(Locale.forLanguageTag(lang.tag))
        val config = Configuration(context.resources.configuration)
        config.setLocales(locales)
        return context.createConfigurationContext(config)
    }

    /** A context in a specific language. */
    fun forTag(context: Context, tag: String): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList(Locale.forLanguageTag(tag)))
        return context.createConfigurationContext(config)
    }
}
