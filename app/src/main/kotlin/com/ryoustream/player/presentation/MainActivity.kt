package com.ryoustream.player.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.ryoustream.player.presentation.navigation.RyouNavGraph
import com.ryoustream.player.presentation.navigation.Screen
import com.ryoustream.player.presentation.permission.PermissionScreen
import com.ryoustream.player.presentation.theme.RyouPlayerTheme
import com.ryoustream.player.presentation.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val themeViewModel: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // enableEdgeToEdge BEFORE setContent — sets window flags properly
        enableEdgeToEdge()

        val startDestination = resolveStartDestination(intent)

        setContent {
            val themeState by themeViewModel.themeState.collectAsStateWithLifecycle()
            val systemDark  = isSystemInDarkTheme()

            val isDark = when (themeState.themeMode) {
                "DARK"  -> true
                "LIGHT" -> false
                else    -> systemDark
            }

            RyouPlayerTheme(
                darkTheme    = isDark,
                dynamicColor = themeState.useDynamicColor,
                amoledMode   = themeState.amoledMode,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background,
                ) {
                    PermissionScreen(
                        onPermissionsGranted = {
                            val navController = rememberNavController()
                            RyouNavGraph(
                                navController    = navController,
                                startDestination = startDestination,
                            )
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun resolveStartDestination(intent: Intent?): String {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri = intent.data
            if (uri != null) return Screen.Player.createRoute(uri.toString())
        }
        return Screen.Home.route
    }
}
