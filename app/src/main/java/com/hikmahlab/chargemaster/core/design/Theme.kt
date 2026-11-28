package com.hikmahlab.chargemaster.core.design

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hikmahlab.chargemaster.domain.model.ThemeMode

/** Centralized brand tokens: cream base, friendly mint primary, signal colours for warnings. */
object Tokens {
    val Primary = Color(0xFF14946A)
    val Secondary = Color(0xFF7A5CD6)
    val Accent = Color(0xFF7FE0B6)
    val BackgroundLight = Color(0xFFFFF8F1)
    val SurfaceLight = Color(0xFFFFFFFF)
    val BackgroundDark = Color(0xFF16151B)
    val SurfaceDark = Color(0xFF211F27)
    val Navy = Color(0xFF2F5FA8)
    val Warning = Color(0xFFD99A0B)
    val Danger = Color(0xFFE0525F)
    val Hot = Color(0xFFEB7A3C)
    val Info = Color(0xFF4A7FD4)
}

/** A soft background with the deeper ink that reads on it. */
@Immutable
data class Pastel(val container: Color, val content: Color)

@Immutable
data class Pastels(
    val mint: Pastel, val peach: Pastel, val sky: Pastel,
    val lilac: Pastel, val butter: Pastel, val rose: Pastel,
) {
    /** Rotation used for tiles so neighbours never share a colour. */
    val cycle: List<Pastel> get() = listOf(mint, peach, sky, lilac, butter, rose)
}

val LightPastels = Pastels(
    mint = Pastel(Color(0xFFD7F5E6), Color(0xFF136E4F)),
    peach = Pastel(Color(0xFFFFE4D4), Color(0xFFA84F22)),
    sky = Pastel(Color(0xFFDDEBFF), Color(0xFF2B5BA6)),
    lilac = Pastel(Color(0xFFEBE2FF), Color(0xFF5E43B0)),
    butter = Pastel(Color(0xFFFFF0C4), Color(0xFF7E5F08)),
    rose = Pastel(Color(0xFFFFDDE5), Color(0xFFAA3456)),
)

val DarkPastels = Pastels(
    mint = Pastel(Color(0xFF1C3A2F), Color(0xFF8EE6C0)),
    peach = Pastel(Color(0xFF3D2A21), Color(0xFFFFBC97)),
    sky = Pastel(Color(0xFF1E2B42), Color(0xFFA2C5FF)),
    lilac = Pastel(Color(0xFF2B2440), Color(0xFFC9B8FF)),
    butter = Pastel(Color(0xFF383020), Color(0xFFFFD97A)),
    rose = Pastel(Color(0xFF3D2129), Color(0xFFFFA2BA)),
)

val LocalPastels = staticCompositionLocalOf { LightPastels }

val LightColors: ColorScheme = lightColorScheme(
    primary = Tokens.Primary, onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F5E6), onPrimaryContainer = Color(0xFF0B4A34),
    secondary = Tokens.Secondary, onSecondary = Color.White,
    secondaryContainer = Color(0xFFEBE2FF), onSecondaryContainer = Color(0xFF2E1F66),
    tertiary = Tokens.Navy, onTertiary = Color.White,
    background = Tokens.BackgroundLight, onBackground = Color(0xFF2A2833),
    surface = Tokens.SurfaceLight, onSurface = Color(0xFF2A2833),
    surfaceVariant = Color(0xFFF3ECE4), onSurfaceVariant = Color(0xFF6E6A78),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFFBF3EA), surfaceContainerHigh = Color(0xFFF5ECE2),
    surfaceContainerHighest = Color(0xFFEFE5DA),
    outline = Color(0xFFA6A0AE), outlineVariant = Color(0xFFEDE4DA),
    error = Tokens.Danger,
)

val DarkColors: ColorScheme = darkColorScheme(
    primary = Tokens.Accent, onPrimary = Color(0xFF07301F),
    primaryContainer = Color(0xFF1C3A2F), onPrimaryContainer = Color(0xFFBDF3DA),
    secondary = Color(0xFFC9B8FF), onSecondary = Color(0xFF2B1D5C),
    secondaryContainer = Color(0xFF2B2440), onSecondaryContainer = Color(0xFFE6DDFF),
    tertiary = Color(0xFFA2C5FF), onTertiary = Color(0xFF14305E),
    background = Tokens.BackgroundDark, onBackground = Color(0xFFF1EEF6),
    surface = Tokens.SurfaceDark, onSurface = Color(0xFFF1EEF6),
    surfaceVariant = Color(0xFF2C2933), onSurfaceVariant = Color(0xFFAEA8BA),
    surfaceContainerLowest = Color(0xFF111015), surfaceContainerLow = Color(0xFF211F27),
    surfaceContainer = Color(0xFF26242D), surfaceContainerHigh = Color(0xFF2D2A34),
    surfaceContainerHighest = Color(0xFF34313C),
    outline = Color(0xFF7D7888), outlineVariant = Color(0xFF34313C),
    error = Color(0xFFFF8A94),
)

private val AmoledColors: ColorScheme = DarkColors.copy(
    background = Color.Black, surface = Color(0xFF141318),
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF141318),
    surfaceContainer = Color(0xFF19181E), surfaceContainerHigh = Color(0xFF201F26),
    surfaceContainerHighest = Color(0xFF27252D),
    surfaceVariant = Color(0xFF201F26), outlineVariant = Color(0xFF27252D),
)

private fun ColorScheme.highContrast(dark: Boolean): ColorScheme = if (dark) copy(
    onSurface = Color.White, onBackground = Color.White, onSurfaceVariant = Color(0xFFE6EEE9),
    primary = Color(0xFF8CFFD0), outline = Color(0xFFE6EEE9),
) else copy(
    onSurface = Color.Black, onBackground = Color.Black, onSurfaceVariant = Color(0xFF1F2A25),
    primary = Color(0xFF0A3E31), outline = Color(0xFF1F2A25),
)

/** Tabular figures keep changing numbers from jittering. */
private const val TNUM = "tnum"

private val AppTypography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontSize = 72.sp, lineHeight = 76.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-2.5).sp, fontFeatureSettings = TNUM),
        displayMedium = t.displayMedium.copy(fontSize = 52.sp, lineHeight = 56.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp, fontFeatureSettings = TNUM),
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, fontFeatureSettings = TNUM),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp, fontFeatureSettings = TNUM),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, fontFeatureSettings = TNUM),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp, fontFeatureSettings = TNUM),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        bodyLarge = t.bodyLarge.copy(letterSpacing = 0.1.sp),
        bodyMedium = t.bodyMedium.copy(letterSpacing = 0.1.sp),
        bodySmall = t.bodySmall.copy(letterSpacing = 0.15.sp, lineHeight = 17.sp),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
        labelMedium = t.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp, fontFeatureSettings = TNUM),
        labelSmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    )
}

/** Display family for friendly headings (kept as a single switch point). */
val SerifFamily: FontFamily = FontFamily.Default

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** True when motion should be minimized (system animator scale 0 or in-app setting). */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun ChargeMasterTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    highContrast: Boolean = false,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val context = LocalContext.current
    var scheme = when {
        themeMode == ThemeMode.AMOLED -> AmoledColors
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    if (highContrast) scheme = scheme.highContrast(dark)
    val systemReduce = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
    CompositionLocalProvider(
        LocalReduceMotion provides (reduceMotion || systemReduce),
        LocalPastels provides if (dark) DarkPastels else LightPastels,
    ) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
