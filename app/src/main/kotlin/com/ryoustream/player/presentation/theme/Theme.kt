package com.ryoustream.player.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ─── Colors ───────────────────────────────────────────────────────────────────

val RyouPrimary = Color(0xFF6750A4)
val RyouPrimaryDark = Color(0xFFD0BCFF)
val RyouSecondary = Color(0xFF625B71)
val RyouSecondaryDark = Color(0xFFCCC2DC)
val RyouTertiary = Color(0xFF7D5260)
val RyouTertiaryDark = Color(0xFFEFB8C8)

val RyouBackground = Color(0xFFFFFBFE)
val RyouBackgroundDark = Color(0xFF1C1B1F)
val RyouSurface = Color(0xFFFFFBFE)
val RyouSurfaceDark = Color(0xFF1C1B1F)
val RyouSurfaceVariant = Color(0xFFE7E0EC)
val RyouSurfaceVariantDark = Color(0xFF49454F)

// Ryou custom accent
val RyouAccent = Color(0xFF7C4DFF)
val RyouAccentDark = Color(0xFFBB86FC)
val RyouPlayerBackground = Color(0xFF000000)

// Dark scheme (default for media player feel)
private val DarkColorScheme = darkColorScheme(
    primary = RyouPrimaryDark,
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = RyouSecondaryDark,
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = RyouTertiaryDark,
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    background = RyouBackgroundDark,
    onBackground = Color(0xFFE6E1E5),
    surface = RyouSurfaceDark,
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = RyouSurfaceVariantDark,
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    error = Color(0xFFCF6679),
)

private val LightColorScheme = lightColorScheme(
    primary = RyouPrimary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = RyouSecondary,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = RyouTertiary,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    background = RyouBackground,
    onBackground = Color(0xFF1C1B1F),
    surface = RyouSurface,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = RyouSurfaceVariant,
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    error = Color(0xFFB3261E),
)

private val AmoledColorScheme = DarkColorScheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF1A1A1A),
)

/**
 * Ryou Player Theme
 *
 * Supports:
 * - Material You dynamic colors (Android 12+)
 * - Manual dark/light mode
 * - AMOLED mode (pure black background)
 */
@Composable
fun RyouPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    amoledMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            when {
                amoledMode && darkTheme -> {
                    // Apply AMOLED override to dynamic dark scheme
                    dynamicDarkColorScheme(context).copy(
                        background = Color.Black,
                        surface = Color.Black,
                    )
                }
                darkTheme -> dynamicDarkColorScheme(context)
                else -> dynamicLightColorScheme(context)
            }
        }
        amoledMode && darkTheme -> AmoledColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RyouTypography,
        content = content
    )
}
