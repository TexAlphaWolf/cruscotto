package dev.tex.cruscotto

import android.app.Application
import android.content.Context
import dev.tex.cruscotto.prefs.AppPrefs
import dev.tex.cruscotto.prefs.Locales
import dev.tex.cruscotto.session.Session
import dev.tex.cruscotto.yearend.YearEnd

class CruscottoApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(Locales.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        AppPrefs.init(this)
        Session.init(this)
        YearEnd.schedule(this)
    }
}
