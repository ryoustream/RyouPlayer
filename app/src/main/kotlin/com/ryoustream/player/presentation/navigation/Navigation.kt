package com.ryoustream.player.presentation.navigation

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.ryoustream.player.domain.model.MediaFolder
import com.ryoustream.player.presentation.about.AboutScreen
import com.ryoustream.player.presentation.components.BottomNavDest
import com.ryoustream.player.presentation.components.RyouBottomNavBar
import com.ryoustream.player.presentation.components.bottomNavRoutes
import com.ryoustream.player.presentation.folder.FolderScreen
import com.ryoustream.player.presentation.home.HomeScreen
import com.ryoustream.player.presentation.home.HomeViewModel
import com.ryoustream.player.presentation.library.LibraryScreen
import com.ryoustream.player.presentation.library.PlaylistDetailScreen
import com.ryoustream.player.presentation.player.PlayerActivity
import com.ryoustream.player.presentation.settings.SettingsScreen
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String) {
    object Home    : Screen("home")
    object Folders : Screen("folders")
    object Library : Screen("library")
    object You     : Screen("you")
    object About   : Screen("about")
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
 *   2. MPVLib.init() is called once per PlayerActivity lifecycle; double-init → crash.
 *   3. PlayerActivity uses singleTop launchMode to avoid duplicate instances.
 */
private fun launchPlayer(context: Context, uri: Uri) {
    context.startActivity(
        PlayerActivity.createIntent(context as androidx.activity.ComponentActivity, uri)
    )
}

@Composable
fun RyouNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Home.route,
    onPlayerRequested: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route

    val showBottomNav = currentRoute in bottomNavRoutes

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomNav) {
                RyouBottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { dest ->
                        navController.navigate(dest.route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = if (showBottomNav)
                Modifier.fillMaxSize().padding(innerPadding)
            else Modifier.fillMaxSize(),
        ) {
            // ─── Home ──────────────────────────────────────────────────────────
            composable(Screen.Home.route) {
                HomeScreen(
                    onMediaClick    = { launchPlayer(context, it.uri) },
                    onSettingsClick = { navController.navigate(Screen.Settings.route) },
                )
            }

            // ─── Folders tab ───────────────────────────────────────────────────
            composable(Screen.Folders.route) {
                FoldersTabScreen(
                    onFolderClick = { folder ->
                        navController.navigate(Screen.FolderDetail.createRoute(folder.id, folder.name))
                    },
                )
            }

            // ─── Library ───────────────────────────────────────────────────────
            composable(Screen.Library.route) {
                LibraryScreen(
                    onMediaClick    = { launchPlayer(context, it.uri) },
                    onPlaylistClick = { playlist ->
                        navController.navigate(Screen.PlaylistDetail.createRoute(playlist.id))
                    },
                    onStreamPlay    = { stream -> launchPlayer(context, Uri.parse(stream.url)) },
                )
            }

            // ─── Playlist Detail ───────────────────────────────────────────────
            composable(
                route = Screen.PlaylistDetail.route,
                arguments = listOf(navArgument("playlistId") { type = NavType.LongType }),
            ) {
                PlaylistDetailScreen(
                    onMediaClick = { launchPlayer(context, it.uri) },
                    onBack       = { navController.popBackStack() },
                )
            }

            // ─── About ────────────────────────────────────────────────────────
            composable(Screen.About.route) {
                AboutScreen(onBack = { navController.popBackStack() })
            }

            // ─── Player (deep-link) ────────────────────────────────────────────
            composable(
                route = Screen.Player.route,
                arguments = listOf(navArgument("mediaUri") { type = NavType.StringType }),
                deepLinks = listOf(navDeepLink { uriPattern = "ryou://player/{mediaUri}" }),
            ) { backStackEntry ->
                val encodedUri = backStackEntry.arguments?.getString("mediaUri") ?: ""
                val decodedUri = URLDecoder.decode(encodedUri, StandardCharsets.UTF_8.toString())
                launchPlayer(context, Uri.parse(decodedUri))
                navController.popBackStack()
            }

            // ─── Folder Detail ─────────────────────────────────────────────────
            composable(
                route = Screen.FolderDetail.route,
                arguments = listOf(
                    navArgument("folderId")   { type = NavType.LongType },
                    navArgument("folderName") { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val folderId   = backStackEntry.arguments?.getLong("folderId") ?: 0L
                val folderName = URLDecoder.decode(
                    backStackEntry.arguments?.getString("folderName") ?: "",
                    StandardCharsets.UTF_8.toString(),
                )
                FolderScreen(
                    folderName   = folderName,
                    onMediaClick = { launchPlayer(context, it.uri) },
                    onBack       = { navController.popBackStack() },
                )
            }

            // ─── Settings ─────────────────────────────────────────────────────
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack       = { navController.popBackStack() },
                    onAboutClick = { navController.navigate(Screen.About.route) },
                )
            }

            // ─── Stream Input ──────────────────────────────────────────────────
            composable(Screen.StreamInput.route) { }
        }
    }
}

// ── Folders tab screen — menggunakan HomeViewModel untuk data folder ───────────

@Composable
private fun FoldersTabScreen(
    onFolderClick: (MediaFolder) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    com.ryoustream.player.presentation.home.FoldersBrowserScreen(
        folders          = uiState.folders,
        isLoading        = uiState.isLoading,
        isRefreshing     = uiState.isRefreshing,
        folderViewMode   = uiState.folderViewMode,
        onFolderClick    = onFolderClick,
        onViewModeToggle = viewModel::onFolderViewModeToggle,
        onRescan         = viewModel::onRescanMedia,
        onRefresh        = viewModel::onRefresh,
    )
}
