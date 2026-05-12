package com.ryoustream.player.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat

val RyouPrimary      = Color(0xFF4A7EC7)   // Biru icon play button
val RyouPrimaryDark  = Color(0xFF90BBF0)   // Biru terang untuk dark mode
val RyouAccent       = Color(0xFF3A6DB5)   // Biru lebih gelap untuk aksen

private val DarkColorScheme = darkColorScheme(
    primary             = Color(0xFF90BBF0),   // biru cerah
    onPrimary           = Color(0xFF003063),
    primaryContainer    = Color(0xFF254E89),
    onPrimaryContainer  = Color(0xFFD4E4FF),
    secondary           = Color(0xFFB4C8E8),
    onSecondary         = Color(0xFF1E3254),
    secondaryContainer  = Color(0xFF324D73),
    onSecondaryContainer= Color(0xFFD4E4FF),
    background          = Color(0xFF0F1923),   // navy gelap
    onBackground        = Color(0xFFE2EAF5),
    surface             = Color(0xFF141E2B),
    onSurface           = Color(0xFFE2EAF5),
    surfaceVariant      = Color(0xFF243347),
    onSurfaceVariant    = Color(0xFFB8CAE0),
    outline             = Color(0xFF4A6482),
    error               = Color(0xFFCF6679),
)

private val LightColorScheme = lightColorScheme(
    primary             = Color(0xFF4A7EC7),   // biru icon
    onPrimary           = Color(0xFFFFFFFF),
    primaryContainer    = Color(0xFFD4E4FF),
    onPrimaryContainer  = Color(0xFF002A5C),
    secondary           = Color(0xFF4E6B94),
    onSecondary         = Color(0xFFFFFFFF),
    secondaryContainer  = Color(0xFFD4E4FF),
    onSecondaryContainer= Color(0xFF07213F),
    background          = Color(0xFFFEF9ED),   // krem dari icon
    onBackground        = Color(0xFF1A1C1E),
    surface             = Color(0xFFFEF9ED),
    onSurface           = Color(0xFF1A1C1E),
    surfaceVariant      = Color(0xFFE8F0FA),
    onSurfaceVariant    = Color(0xFF3A4A5C),
    outline             = Color(0xFF7A9ABB),
    error               = Color(0xFFB3261E),
)

private val AmoledColorScheme = DarkColorScheme.copy(
    background    = Color.Black,
    surface       = Color.Black,
    surfaceVariant= Color(0xFF0D1520),
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
            // Transparent bars are handled by enableEdgeToEdge() called in each Activity.
            // Setting statusBarColor / navigationBarColor is deprecated on API 35+.
            WindowInsetsControllerCompat(window, view).apply {
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
