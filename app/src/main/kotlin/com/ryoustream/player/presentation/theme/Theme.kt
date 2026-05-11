package com.ryoustream.player.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

val RyouPrimary      = Color(0xFF6750A4)
val RyouPrimaryDark  = Color(0xFFD0BCFF)
val RyouAccent       = Color(0xFF7C4DFF)

private val DarkColorScheme = darkColorScheme(
    primary             = Color(0xFFD0BCFF),
    onPrimary           = Color(0xFF381E72),
    primaryContainer    = Color(0xFF4F378B),
    onPrimaryContainer  = Color(0xFFEADDFF),
    secondary           = Color(0xFFCCC2DC),
    onSecondary         = Color(0xFF332D41),
    secondaryContainer  = Color(0xFF4A4458),
    onSecondaryContainer= Color(0xFFE8DEF8),
    background          = Color(0xFF1C1B1F),
    onBackground        = Color(0xFFE6E1E5),
    surface             = Color(0xFF1C1B1F),
    onSurface           = Color(0xFFE6E1E5),
    surfaceVariant      = Color(0xFF49454F),
    onSurfaceVariant    = Color(0xFFCAC4D0),
    outline             = Color(0xFF938F99),
    error               = Color(0xFFCF6679),
)

private val LightColorScheme = lightColorScheme(
    primary             = Color(0xFF6750A4),
    onPrimary           = Color(0xFFFFFFFF),
    primaryContainer    = Color(0xFFEADDFF),
    onPrimaryContainer  = Color(0xFF21005D),
    secondary           = Color(0xFF625B71),
    onSecondary         = Color(0xFFFFFFFF),
    secondaryContainer  = Color(0xFFE8DEF8),
    onSecondaryContainer= Color(0xFF1D192B),
    background          = Color(0xFFFFFBFE),
    onBackground        = Color(0xFF1C1B1F),
    surface             = Color(0xFFFFFBFE),
    onSurface           = Color(0xFF1C1B1F),
    surfaceVariant      = Color(0xFFE7E0EC),
    onSurfaceVariant    = Color(0xFF49454F),
    outline             = Color(0xFF79747E),
    error               = Color(0xFFB3261E),
)

private val AmoledColorScheme = DarkColorScheme.copy(
    background    = Color.Black,
    surface       = Color.Black,
    surfaceVariant= Color(0xFF111111),
)

@Composable
fun RyouPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    amoledMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            when {
                amoledMode && darkTheme -> dynamicDarkColorScheme(ctx).copy(
                    background = Color.Black, surface = Color.Black,
                )
                darkTheme  -> dynamicDarkColorScheme(ctx)
                else       -> dynamicLightColorScheme(ctx)
            }
        }
        amoledMode && darkTheme -> AmoledColorScheme
        darkTheme               -> DarkColorScheme
        else                    -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Transparent bars — Compose draws behind them via enableEdgeToEdge()
            window.statusBarColor  = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()

            // FIX: Set icon colors correctly based on actual theme
            WindowInsetsControllerCompat(window, view).apply {
                // Light status bar = dark icons (for light theme)
                isAppearanceLightStatusBars     = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = RyouTypography,
        content     = content,
    )
}
