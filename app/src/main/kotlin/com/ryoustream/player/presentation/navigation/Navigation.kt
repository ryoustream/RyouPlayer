package com.ryoustream.player.presentation.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.ryoustream.player.presentation.home.HomeScreen
import com.ryoustream.player.presentation.library.LibraryScreen
import com.ryoustream.player.presentation.player.PlayerScreen
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
 * Root navigation graph for Ryou Player
 */
@Composable
fun RyouNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Home.route,
    onPlayerRequested: ((String) -> Unit)? = null,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        // ─── Home ──────────────────────────────────────────────────────────────
        composable(Screen.Home.route) {
            HomeScreen(
                onMediaClick = { mediaItem ->
                    navController.navigate(
                        Screen.Player.createRoute(mediaItem.uri.toString())
                    )
                },
                onFolderClick = { folder ->
                    navController.navigate(
                        Screen.FolderDetail.createRoute(folder.id, folder.name)
                    )
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                },
            )
        }

        // ─── Library ───────────────────────────────────────────────────────────
        composable(Screen.Library.route) {
            LibraryScreen(
                onMediaClick = { mediaItem ->
                    navController.navigate(
                        Screen.Player.createRoute(mediaItem.uri.toString())
                    )
                },
                onPlaylistClick = { playlist ->
                    navController.navigate(Screen.PlaylistDetail.createRoute(playlist.id))
                },
            )
        }

        // ─── Player ────────────────────────────────────────────────────────────
        composable(
            route = Screen.Player.route,
            arguments = listOf(
                navArgument("mediaUri") { type = NavType.StringType }
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = "ryou://player/{mediaUri}" }
            )
        ) { backStackEntry ->
            val encodedUri = backStackEntry.arguments?.getString("mediaUri") ?: ""
            val decodedUri = URLDecoder.decode(encodedUri, StandardCharsets.UTF_8.toString())
            PlayerScreen(
                mediaUri = Uri.parse(decodedUri),
                onBack = { navController.popBackStack() },
            )
        }

        // ─── Folder Detail ─────────────────────────────────────────────────────
        composable(
            route = Screen.FolderDetail.route,
            arguments = listOf(
                navArgument("folderId") { type = NavType.LongType },
                navArgument("folderName") { type = NavType.StringType },
            )
        ) { backStackEntry ->
            val folderId = backStackEntry.arguments?.getLong("folderId") ?: 0L
            val folderName = URLDecoder.decode(
                backStackEntry.arguments?.getString("folderName") ?: "",
                StandardCharsets.UTF_8.toString()
            )
            // FolderDetailScreen can reuse HomeScreen/LibraryScreen with folder filter
            HomeScreen(
                folderId = folderId,
                folderTitle = folderName,
                onMediaClick = { mediaItem ->
                    navController.navigate(Screen.Player.createRoute(mediaItem.uri.toString()))
                },
                onFolderClick = {},
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onBack = { navController.popBackStack() },
            )
        }

        // ─── Settings ─────────────────────────────────────────────────────────
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // ─── Stream Input ──────────────────────────────────────────────────────
        composable(Screen.StreamInput.route) {
            // StreamInputScreen can be added here
        }
    }
}
