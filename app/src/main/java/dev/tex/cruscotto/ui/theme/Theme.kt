package dev.tex.cruscotto.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.tex.cruscotto.R
import dev.tex.cruscotto.prefs.ThemeMode

// ---------- type: Fraunces for titles, Manrope for everything else ----------
val Fraunces = FontFamily(
    Font(R.font.fraunces_regular, FontWeight.Normal),
    Font(R.font.fraunces_semibold, FontWeight.SemiBold),
)
val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)

/** Amounts and mileage: Manrope with tabular figures, so digits line up like an odometer. */
val NumberStyle = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")

private val AppTypography = Typography().let { t ->
    fun TextStyle.m(w: FontWeight = FontWeight.Normal) = copy(fontFamily = Manrope, fontWeight = w)
    fun TextStyle.f(size: Int) = copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = size.sp, letterSpacing = 0.sp)
    t.copy(
        displayLarge = t.displayLarge.f(52), displayMedium = t.displayMedium.f(42), displaySmall = t.displaySmall.f(34),
        headlineLarge = t.headlineLarge.f(30), headlineMedium = t.headlineMedium.f(26), headlineSmall = t.headlineSmall.f(23),
        titleLarge = t.titleLarge.f(22), titleMedium = t.titleMedium.f(18),
        titleSmall = t.titleSmall.m(FontWeight.Bold),
        bodyLarge = t.bodyLarge.m(), bodyMedium = t.bodyMedium.m(), bodySmall = t.bodySmall.m(),
        labelLarge = t.labelLarge.m(FontWeight.SemiBold), labelMedium = t.labelMedium.m(FontWeight.SemiBold),
        labelSmall = t.labelSmall.m(FontWeight.Bold).copy(letterSpacing = 1.2.sp),
    )
}

// ---------- semantic colours ----------
@Immutable
data class StatusColors(
    val ok: Color, val okSoft: Color,
    val warn: Color, val warnSoft: Color,
    val bad: Color, val badSoft: Color,
    val income: Color, val expense: Color,
)

/** Gradients and theme-specific decoration. */
@Immutable
data class AppColors(
    val isDark: Boolean,
    /** Thin accent stripe on cards and bars: pastel azure→green→orange (light), orange (dark). */
    val stripe: Brush,
    /** Background of the summary tiles. */
    val hero: Brush,
    val heroBorder: Color?,
    /** Lock screen background. */
    val lockBackground: Brush,
    val barOk: Brush,
    val barWarn: Brush,
    val barBad: Brush,
    val barIncome: Brush,
    val barExpense: Brush,
    val track: Color,
    val navIndicator: Color,
    val cardBorder: Color?,
)

private val LightStatus = StatusColors(
    ok = Color(0xFF2E8B5E), okSoft = Color(0xFFDDF3E7),
    warn = Color(0xFFC46A1A), warnSoft = Color(0xFFFFEBD9),
    bad = Color(0xFFC0392B), badSoft = Color(0xFFFBE1DD),
    income = Color(0xFF5DB88F), expense = Color(0xFFF2A160),
)
private val DarkStatus = StatusColors(
    ok = Color(0xFF7FD1A4), okSoft = Color(0xFF13261C),
    warn = Color(0xFFFFB067), warnSoft = Color(0xFF2E1E0E),
    bad = Color(0xFFFF7A6B), badSoft = Color(0xFF2E1412),
    income = Color(0xFFCFCFD4), expense = Color(0xFFFF8A3D),
)

private val LightApp = AppColors(
    isDark = false,
    stripe = Brush.horizontalGradient(listOf(Color(0xFF7DBDE8), Color(0xFF86D1AE), Color(0xFFF7B27A))),
    hero = Brush.linearGradient(listOf(Color(0xFFCFE8F8), Color(0xFFD4F1E3), Color(0xFFFFE2CB))),
    heroBorder = null,
    lockBackground = Brush.verticalGradient(listOf(Color(0xFFE3F2FC), Color(0xFFF3F7FA), Color(0xFFE6F6EE), Color(0xFFFFF0E3))),
    barOk = Brush.horizontalGradient(listOf(Color(0xFF6FB3E3), Color(0xFF6CC59C))),
    barWarn = Brush.horizontalGradient(listOf(Color(0xFFF7C08A), Color(0xFFE9873B))),
    barBad = Brush.horizontalGradient(listOf(Color(0xFFF29A7E), Color(0xFFC0392B))),
    barIncome = Brush.horizontalGradient(listOf(Color(0xFF9FD8BE), Color(0xFF5DB88F))),
    barExpense = Brush.horizontalGradient(listOf(Color(0xFFFFC99B), Color(0xFFF2A160))),
    track = Color(0xFFE6EEF4),
    navIndicator = Color(0xFFD5ECFA),
    cardBorder = null,
)
private val DarkApp = AppColors(
    isDark = true,
    stripe = Brush.horizontalGradient(listOf(Color(0xFFFF7A2E), Color(0xFFFFB067))),
    hero = Brush.linearGradient(listOf(Color(0xFF1C1C1F), Color(0xFF121214))),
    heroBorder = Color(0xFF2A2A2E),
    lockBackground = Brush.verticalGradient(listOf(Color(0xFF141416), Color(0xFF0A0A0B))),
    barOk = Brush.horizontalGradient(listOf(Color(0xFF6E6E75), Color(0xFFCFCFD4))),
    barWarn = Brush.horizontalGradient(listOf(Color(0xFFFF7A2E), Color(0xFFFFB067))),
    barBad = Brush.horizontalGradient(listOf(Color(0xFFE0453A), Color(0xFFFF7A6B))),
    barIncome = Brush.horizontalGradient(listOf(Color(0xFF6E6E75), Color(0xFFCFCFD4))),
    barExpense = Brush.horizontalGradient(listOf(Color(0xFFFF7A2E), Color(0xFFFFB067))),
    track = Color(0xFF222226),
    navIndicator = Color(0xFF3A2210),
    cardBorder = Color(0xFF2A2A2E),
)

val LocalStatus = staticCompositionLocalOf { LightStatus }
val LocalAppColors = staticCompositionLocalOf { LightApp }

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2B7BB9), onPrimary = Color.White,
    primaryContainer = Color(0xFFD5ECFA), onPrimaryContainer = Color(0xFF0E3A5A),
    secondary = Color(0xFF3C9A72), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3F0E2), onSecondaryContainer = Color(0xFF0F3D2A),
    tertiary = Color(0xFFD9782D), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE3CC), onTertiaryContainer = Color(0xFF5A2C08),
    background = Color(0xFFF3F7FA), onBackground = Color(0xFF1C2A35),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1C2A35),
    surfaceVariant = Color(0xFFEAF1F6), onSurfaceVariant = Color(0xFF5E7180),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF7FAFC),
    surfaceContainer = Color(0xFFF1F6F9), surfaceContainerHigh = Color(0xFFFFFFFF), surfaceContainerHighest = Color(0xFFE6EEF4),
    outline = Color(0xFFC5D3DD), outlineVariant = Color(0xFFDCE6ED),
    error = Color(0xFFC0392B),
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFF8A3D), onPrimary = Color(0xFF1A0D03),
    primaryContainer = Color(0xFF3A2210), onPrimaryContainer = Color(0xFFFFD1B0),
    secondary = Color(0xFFC7C7CC), onSecondary = Color(0xFF1A1A1C),
    secondaryContainer = Color(0xFF2A2A2E), onSecondaryContainer = Color(0xFFE4E4E7),
    tertiary = Color(0xFFFFB067), onTertiary = Color(0xFF1A0D03),
    tertiaryContainer = Color(0xFF3A2210), onTertiaryContainer = Color(0xFFFFD1B0),
    background = Color(0xFF0A0A0B), onBackground = Color(0xFFEDEDEE),
    surface = Color(0xFF151517), onSurface = Color(0xFFEDEDEE),
    surfaceVariant = Color(0xFF1F1F22), onSurfaceVariant = Color(0xFF9C9CA3),
    surfaceContainerLowest = Color(0xFF0A0A0B), surfaceContainerLow = Color(0xFF121214),
    surfaceContainer = Color(0xFF151517), surfaceContainerHigh = Color(0xFF1C1C1F), surfaceContainerHighest = Color(0xFF26262A),
    outline = Color(0xFF3A3A40), outlineVariant = Color(0xFF2A2A2E),
    error = Color(0xFFFF7A6B),
)

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun CruscottoTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalStatus provides if (dark) DarkStatus else LightStatus,
        LocalAppColors provides if (dark) DarkApp else LightApp,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AppTypography,
            content = content,
        )
    }
}
