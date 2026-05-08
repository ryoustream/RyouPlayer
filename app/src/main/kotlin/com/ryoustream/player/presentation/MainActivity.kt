package com.ryoustream.player.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.ryoustream.player.presentation.navigation.RyouNavGraph
import com.ryoustream.player.presentation.navigation.Screen
import com.ryoustream.player.presentation.theme.RyouPlayerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * MainActivity - Single Activity architecture
 *
 * Hosts the entire Compose UI. Handles:
 * - Splash screen
 * - Edge-to-edge display
 * - External media file intent handling
 * - Dark/light theme based on settings
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate
        installSplashScreen()

        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display
        enableEdgeToEdge()

        // Determine start destination based on incoming intent
        val startDestination = resolveStartDestination(intent)

        setContent {
            val navController = rememberNavController()

            RyouPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RyouNavGraph(
                        navController = navController,
                        startDestination = startDestination,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Handle new intent for external file opens
    }

    /**
     * Determines the start destination based on the incoming intent.
     * If the app is launched with a media URI (e.g., from file manager),
     * navigate directly to the player.
     */
    private fun resolveStartDestination(intent: Intent?): String {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri = intent.data
            if (uri != null) {
                return Screen.Player.createRoute(uri.toString())
            }
        }
        return Screen.Home.route
    }
}
