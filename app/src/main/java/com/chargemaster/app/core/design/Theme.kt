package com.chargemaster.app.core.design

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.chargemaster.app.domain.model.ThemeMode

/** Centralized brand tokens. */
object Tokens {
    val Primary = Color(0xFF145C4A)
    val Secondary = Color(0xFF4B8B78)
    val Accent = Color(0xFF69D7AD)
    val BackgroundLight = Color(0xFFF5F8F6)
    val SurfaceLight = Color(0xFFFFFFFF)
    val BackgroundDark = Color(0xFF0D1713)
    val SurfaceDark = Color(0xFF172720)
    val Navy = Color(0xFF0F1E2E)
    val Warning = Color(0xFFE0A100)
    val Danger = Color(0xFFD64545)
    val Info = Color(0xFF3E7CB1)
}

val LightColors: ColorScheme = lightColorScheme(
    primary = Tokens.Primary, onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F0D8), onPrimaryContainer = Color(0xFF002117),
    secondary = Tokens.Secondary, onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E8DF), onSecondaryContainer = Color(0xFF0D1F18),
    tertiary = Tokens.Navy, onTertiary = Color.White,
    background = Tokens.BackgroundLight, onBackground = Color(0xFF17201C),
    surface = Tokens.SurfaceLight, onSurface = Color(0xFF17201C),
    surfaceVariant = Color(0xFFE3ECE7), onSurfaceVariant = Color(0xFF46524C),
    surfaceContainer = Color(0xFFEDF3EF), surfaceContainerHigh = Color(0xFFE6EEE9),
    outline = Color(0xFF76847D), outlineVariant = Color(0xFFC5D0CA),
    error = Tokens.Danger,
)

val DarkColors: ColorScheme = darkColorScheme(
    primary = Tokens.Accent, onPrimary = Color(0xFF00382A),
    primaryContainer = Tokens.Primary, onPrimaryContainer = Color(0xFFB9F0D8),
    secondary = Color(0xFF9CD2BE), onSecondary = Color(0xFF07372A),
    secondaryContainer = Color(0xFF244E41), onSecondaryContainer = Color(0xFFD3E8DF),
    tertiary = Color(0xFF9EC3E6), onTertiary = Tokens.Navy,
    background = Tokens.BackgroundDark, onBackground = Color(0xFFDDE6E1),
    surface = Tokens.SurfaceDark, onSurface = Color(0xFFDDE6E1),
    surfaceVariant = Color(0xFF22362D), onSurfaceVariant = Color(0xFFB2C2BA),
    surfaceContainer = Color(0xFF1A2C24), surfaceContainerHigh = Color(0xFF20342B),
    outline = Color(0xFF7C8D85), outlineVariant = Color(0xFF34473E),
    error = Color(0xFFFF8A80),
)

private val AmoledColors: ColorScheme = DarkColors.copy(
    background = Color.Black, surface = Color(0xFF070B09),
    surfaceContainer = Color(0xFF0B120F), surfaceContainerHigh = Color(0xFF101A15),
    surfaceVariant = Color(0xFF14211B),
)

private fun ColorScheme.highContrast(dark: Boolean): ColorScheme = if (dark) copy(
    onSurface = Color.White, onBackground = Color.White, onSurfaceVariant = Color(0xFFE6EEE9),
    primary = Color(0xFF8CFFD0), outline = Color(0xFFE6EEE9),
) else copy(
    onSurface = Color.Black, onBackground = Color.Black, onSurfaceVariant = Color(0xFF1F2A25),
    primary = Color(0xFF0A3E31), outline = Color(0xFF1F2A25),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
        displayMedium = t.displayMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    )
}

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
    CompositionLocalProvider(LocalReduceMotion provides (reduceMotion || systemReduce)) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
