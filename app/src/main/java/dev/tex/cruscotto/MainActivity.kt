package dev.tex.cruscotto

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import dev.tex.cruscotto.prefs.AppPrefs
import dev.tex.cruscotto.prefs.Locales
import dev.tex.cruscotto.ui.AppRoot
import dev.tex.cruscotto.ui.theme.CruscottoTheme
import dev.tex.cruscotto.ui.theme.isDarkTheme

/** FragmentActivity because BiometricPrompt needs it. */
class MainActivity : FragmentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Locales.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // no screenshots and no preview in the recent-apps screen
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            val mode by AppPrefs.theme.collectAsState()
            val dark = isDarkTheme(mode)
            // status and navigation bar icons follow the app theme, not only the system one
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            CruscottoTheme(dark) {
                AppRoot()
            }
        }
    }
}
