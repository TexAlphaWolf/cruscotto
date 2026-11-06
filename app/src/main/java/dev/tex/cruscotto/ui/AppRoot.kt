package dev.tex.cruscotto.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import dev.tex.cruscotto.prefs.AppPrefs
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tex.cruscotto.ui.theme.LocalAppColors
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import dev.tex.cruscotto.R
import dev.tex.cruscotto.data.Repository
import dev.tex.cruscotto.session.AppState
import dev.tex.cruscotto.session.Session
import dev.tex.cruscotto.ui.budget.BudgetScreen
import dev.tex.cruscotto.ui.garage.CarDetailScreen
import dev.tex.cruscotto.ui.garage.GarageScreen
import dev.tex.cruscotto.ui.lock.OfferBiometricScreen
import dev.tex.cruscotto.ui.lock.OtpScreen
import dev.tex.cruscotto.ui.lock.RecoveryCodeScreen
import dev.tex.cruscotto.ui.lock.SetupScreen
import dev.tex.cruscotto.ui.lock.UnlockScreen
import dev.tex.cruscotto.ui.movements.MovementsScreen
import dev.tex.cruscotto.ui.settings.SettingsScreen
import java.time.YearMonth

/** Snackbar shared by all screens. */
val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState> { error("SnackbarHostState missing") }

private enum class Tab(@StringRes val label: Int, val icon: ImageVector) {
    Budget(R.string.tab_budget, Icons.Filled.Home),
    Movements(R.string.tab_movements, Icons.AutoMirrored.Filled.List),
    Garage(R.string.tab_garage, Icons.Filled.Build),
    Settings(R.string.tab_settings, Icons.Filled.Settings),
}

@Composable
fun AppRoot() {
    val state by Session.state.collectAsState()
    when (val s = state) {
        AppState.NeedsSetup -> SetupScreen()
        AppState.Locked -> UnlockScreen()
        AppState.AwaitOtp -> OtpScreen()
        is AppState.ShowRecovery -> RecoveryCodeScreen(s.code)
        AppState.OfferBiometric -> OfferBiometricScreen()
        AppState.Unlocked -> {
            val repo = Session.repo
            if (repo == null) UnlockScreen() else MainScaffold(repo)
        }
    }
}

@Composable
private fun MainScaffold(repo: Repository) {
    var tab by rememberSaveable { mutableStateOf(Tab.Budget) }
    var carId by rememberSaveable { mutableStateOf<String?>(null) }
    // month shared by Budget and Movements
    var month by remember { mutableStateOf(YearMonth.now()) }
    val snackbar = remember { SnackbarHostState() }

    // Android 13+: ask once for notifications, used only by the year-end reminder
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && AppPrefs.purgeEnabled && !AppPrefs.notificationAsked) {
            AppPrefs.notificationAsked = true
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler(enabled = carId != null) { carId = null }
    BackHandler(enabled = carId == null && tab != Tab.Budget) { tab = Tab.Budget }

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                val app = LocalAppColors.current
                Column {
                    // dark theme: orange stripe above the bar; light theme: hairline
                    if (app.isDark) Box(Modifier.fillMaxWidth().height(2.dp).background(app.stripe))
                    else HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t; if (t != Tab.Garage) carId = null },
                                icon = { Icon(t.icon, contentDescription = null) },
                                label = { Text(stringResource(t.label), maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = app.navIndicator,
                                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    Tab.Budget -> BudgetScreen(repo, month, onMonth = { month = it })
                    Tab.Movements -> MovementsScreen(repo, month, onMonth = { month = it })
                    Tab.Garage -> {
                        val id = carId
                        if (id == null) GarageScreen(repo, onOpenCar = { carId = it })
                        else CarDetailScreen(repo, id, onBack = { carId = null })
                    }
                    Tab.Settings -> SettingsScreen(repo)
                }
            }
        }
    }
}
