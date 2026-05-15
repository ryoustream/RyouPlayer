package com.ryoustream.player.presentation.navigation

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.ryoustream.player.domain.model.NetworkStream
import com.ryoustream.player.presentation.home.HomeScreen
import com.ryoustream.player.presentation.library.LibraryScreen
import com.ryoustream.player.presentation.player.PlayerActivity
import com.ryoustream.player.presentation.settings.SettingsScreen
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Navigation routes for Ryou Player
 */
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Library : Screen("library")
    object Settings : Screen("settings")

    object Player : Screen("player/{mediaUri}") {
        fun createRoute(uri: String): String {
            val encoded = URLEncoder.encode(uri, StandardCharsets.UTF_8.toString())
            return "player/$encoded"
        }
    }

    object FolderDetail : Screen("folder/{folderId}/{folderName}") {
        fun createRoute(folderId: Long, name: String): String {
            val encoded = URLEncoder.encode(name, StandardCharsets.UTF_8.toString())
            return "folder/$folderId/$encoded"
        }
    }

    object PlaylistDetail : Screen("playlist/{playlistId}") {
        fun createRoute(playlistId: Long) = "playlist/$playlistId"
    }

    object StreamInput : Screen("stream_input")
}

/**
 * Launches PlayerActivity via Intent.
 *
 * CRITICAL: The player MUST run in PlayerActivity (separate Activity), NOT as an
 * inline composable in MainActivity's NavGraph. Reasons:
 *   1. PlayerActivity sets fullscreen + cutout window flags BEFORE setContent.
 *      These flags cannot be applied retroactively to MainActivity's window.
 *   2. MPVLib.init() is called once per PlayerActivity lifecycle. If PlayerScreen
 *      were inline in the NavGraph, any configuration change (rotation) would
 *      cause MainActivity to recreate → NavGraph rebuild → initializePlayer()
 *      called again on an already-initialized MPV context → native crash.
 *   3. PlayerActivity uses singleTop launchMode, so back→reopen reuses the
 *      same Activity instance and ViewModel, avoiding double-init.
 */
private fun launchPlayer(context: Context, uri: Uri) {
    context.startActivity(
        PlayerActivity.createIntent(context as androidx.activity.ComponentActivity, uri)
    )
}

/**
 * Root navigation graph for Ryou Player.
 * Note: Player routes are handled by launching PlayerActivity, not composable navigation.
 */
@Composable
fun RyouNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Home.route,
    onPlayerRequested: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        // ─── Home ──────────────────────────────────────────────────────────────
        composable(Screen.Home.route) {
            HomeScreen(
                onMediaClick = { mediaItem -> launchPlayer(context, mediaItem.uri) },
                onFolderClick = { folder ->
                    navController.navigate(Screen.FolderDetail.createRoute(folder.id, folder.name))
                },
                onPlaylistClick = { playlist ->
                    navController.navigate(Screen.PlaylistDetail.createRoute(playlist.id))
                },
                onStreamClick = { stream -> launchPlayer(context, Uri.parse(stream.url)) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
            )
        }

        // ─── Library ───────────────────────────────────────────────────────────
        composable(Screen.Library.route) {
            LibraryScreen(
                onMediaClick = { mediaItem -> launchPlayer(context, mediaItem.uri) },
                onPlaylistClick = { playlist ->
                    navController.navigate(Screen.PlaylistDetail.createRoute(playlist.id))
                },
            )
        }

        // ─── Player composable route (kept for deep-link / ACTION_VIEW from
        //     external apps that resolve to MainActivity) ──────────────────────
        composable(
            route = Screen.Player.route,
            arguments = listOf(navArgument("mediaUri") { type = NavType.StringType }),
            deepLinks = listOf(navDeepLink { uriPattern = "ryou://player/{mediaUri}" }),
        ) { backStackEntry ->
            val encodedUri = backStackEntry.arguments?.getString("mediaUri") ?: ""
            val decodedUri = URLDecoder.decode(encodedUri, StandardCharsets.UTF_8.toString())
            // Redirect to PlayerActivity and pop back — we never render inline
            val uri = Uri.parse(decodedUri)
            launchPlayer(context, uri)
            navController.popBackStack()
        }

        // ─── Folder Detail ─────────────────────────────────────────────────────
        composable(
            route = Screen.FolderDetail.route,
            arguments = listOf(
                navArgument("folderId") { type = NavType.LongType },
                navArgument("folderName") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val folderId = backStackEntry.arguments?.getLong("folderId") ?: 0L
            val folderName = URLDecoder.decode(
                backStackEntry.arguments?.getString("folderName") ?: "",
                StandardCharsets.UTF_8.toString(),
            )
            HomeScreen(
                folderId    = folderId,
                folderTitle = folderName,
                onMediaClick = { mediaItem -> launchPlayer(context, mediaItem.uri) },
                onFolderClick = {},
                onPlaylistClick = { playlist ->
                    navController.navigate(Screen.PlaylistDetail.createRoute(playlist.id))
                },
                onStreamClick = { stream -> launchPlayer(context, Uri.parse(stream.url)) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onBack = { navController.popBackStack() },
            )
        }

        // ─── Settings ─────────────────────────────────────────────────────────
        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        // ─── Stream Input ──────────────────────────────────────────────────────
        composable(Screen.StreamInput.route) { }
    }
}
